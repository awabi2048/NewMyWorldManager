package me.awabi2048.myworldmanager.service

import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ワールド生成をサーバー全体で1件ずつ直列実行するための非ブロッキングパイプライン。
 *
 * 背景: 生成の最重量部（Bukkit.createWorld やスポーン探索の大量チャンクロード）は
 * メインスレッド必須であり、別プレイヤー同士の同時生成は制御なしの直列待ちになっていた。
 * 入口で許可を1つに束ねることで、tick停止の積み上がりを順序付きの待ち行列に変える。
 *
 * 設計上の決定:
 * - 許可は生成全体（テンプレートの非同期コピーを含む）を覆う。コピー並列による
 *   ディスク競合も抑止するための単純化であり、コピー中はメインスレッドが空くため
 *   サーバー自体は進行する。
 * - メインスレッドを sleep で待たせない。空きがあれば呼出スレッドで即時実行し、
 *   使用中ならキューに積んで許可の受渡し時にメインスレッドへ正規化して実行する。
 * - 許可の解放はタスクごとに冪等な [Permit] で行う。完了経路が複数（正常・失敗・
 *   非同期コールバック・想定外例外）に分散しても二重解放が起きない。
 * - Bukkit への依存はコンストラクタ引数に切り出し、単体テスト可能にしている。
 */
class WorldCreationPipeline(
    private val isMainThread: () -> Boolean,
    private val canDispatch: () -> Boolean,
    private val dispatchOnMain: (Runnable) -> Unit,
    private val maxQueueSize: Int = MAX_QUEUE_SIZE,
) {
    companion object {
        /** 同時待ちの上限。プレイヤー単位ガードと併せて無限滞留を防ぐための保険である。 */
        const val MAX_QUEUE_SIZE = 10
    }

    /** 1件の生成が保持する許可。解放は冪等であり、複数経路からの二重解放を吸収する。 */
    inner class Permit internal constructor() {
        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) {
                onPermitReleased()
            }
        }
    }

    private data class Pending(
        val owner: UUID,
        val task: (Permit) -> Unit,
        val onFailure: () -> Unit,
        val permit: Permit,
    )

    enum class SubmitResult {
        /** 空きがあり、呼出スレッドで即時実行を開始した。 */
        STARTED,
        /** 使用中のため待ち行列に入れた。完了はタスク側の Future 等で通知される。 */
        QUEUED,
        /** 待ち行列が上限に達したため受け付けなかった。 */
        QUEUE_FULL,
        /** シャットダウン中のため受け付けなかった。 */
        REJECTED_SHUTDOWN,
    }

    private val lock = Any()
    private val queue = ArrayDeque<Pending>()
    private var busy = false
    private var shutdown = false

    /**
     * 生成タスクを提出する。
     * 即時実行できた場合は呼出スレッドで [task] を開始し [SubmitResult.STARTED] を返す。
     * 使用中の場合はキューへ積み、[onQueued] に現在の待ち位置（1始まり）を通知する。
     * 呼出側は [onFailure] でキュー破棄時（シャットダウン・想定外例外）の後始末を行う。
     */
    fun submit(
        owner: UUID,
        onQueued: (position: Int) -> Unit,
        task: (Permit) -> Unit,
        onFailure: () -> Unit,
    ): SubmitResult {
        val permit = Permit()
        synchronized(lock) {
            if (shutdown) return SubmitResult.REJECTED_SHUTDOWN
            if (!busy) {
                busy = true
            } else {
                if (queue.size >= maxQueueSize) return SubmitResult.QUEUE_FULL
                queue.addLast(Pending(owner, task, onFailure, permit))
                onQueued(queue.size)
                return SubmitResult.QUEUED
            }
        }
        // スケジュール直前の想定外例外で許可をリークさせないための最終防壁。
        // タスク本体は完了経路で自ら解放するが、二重解放は Permit が吸収する。
        try {
            task(permit)
        } catch (failure: Throwable) {
            runCatching { onFailure() }
            permit.release()
            throw failure
        }
        return SubmitResult.STARTED
    }

    /**
     * 即時実行を試みる。空きがあれば許可を返し、なければ null を返す。
     * 同期 API など、待ち行列に入れられない呼出向けである。
     */
    fun tryAcquire(): Permit? {
        synchronized(lock) {
            if (shutdown || busy) return null
            busy = true
            return Permit()
        }
    }

    /**
     * プラグイン無効化時に待ち行列を破棄する。実行中の1件は完了を待たず、
     * その完了経路は [onPermitReleased] のシャットダウン分岐で無害化される。
     */
    fun shutdown() {
        val pending = synchronized(lock) {
            shutdown = true
            val drained = queue.toList()
            queue.clear()
            drained
        }
        pending.forEach { runCatching { it.onFailure() } }
    }

    private fun onPermitReleased() {
        while (true) {
            val next = synchronized(lock) {
                val candidate = queue.pollFirst()
                if (candidate == null) {
                    busy = false
                }
                candidate
            } ?: return
            // 取得と解放を同一ロック下で線形化しているため、ここでの再 poll は不要である。
            if (!canDispatch()) {
                runCatching { next.onFailure() }
                continue
            }
            if (isMainThread()) {
                runTask(next)
                return
            }
            try {
                dispatchOnMain(Runnable { runTask(next) })
            } catch (failure: Throwable) {
                // 無効化競合などでスケジュールできなかった場合は次へ進める。
                runCatching { next.onFailure() }
                next.permit.release()
                continue
            }
            return
        }
    }

    private fun runTask(next: Pending) {
        try {
            next.task(next.permit)
        } catch (failure: Throwable) {
            runCatching { next.onFailure() }
            next.permit.release()
        }
    }
}
