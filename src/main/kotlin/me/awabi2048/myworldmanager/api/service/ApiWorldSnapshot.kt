package me.awabi2048.myworldmanager.api.service

import java.io.File
import java.util.UUID

/**
 * ワールドのディスク書き込みを一時的に静止した状態へ固定するスナップショットリースです。
 *
 * バックアップやアーカイブのような「ディレクトリをそのまま複製する」処理向けに、
 * 複製の間だけチャンク保存・自動保存・アンロード保存の書き込みが入らないことを
 * 保証します。`World.save()`（flush なし）直後の複製では、書き込みキューに残った
 * データと複製が競合し、リージョンファイルの位置表と本体が別時点の内容になり得ます。
 *
 * 静止の方式は取得時のワールド状態で決まります。
 *
 * - 未ロード: 何もしません。ディレクトリはすでに静止しています。
 * - ロード中・無人: ワールドをアンロードして書き込み主体を消します。close で再ロードします。
 * - ロード中・在室: 自動保存を停止して `save(true)`（同期 flush）で書き出し切ります。
 *   close で自動保存を再有効化します。在室中はチャンクのアンロード保存が残り得るため、
 *   [isStillQuiescent] で複製中の新規書き込みを検出してください。
 *
 * 取得はメインスレッドで行い、close は任意スレッドから呼べます。
 * close は冪等です。
 */
interface ApiWorldSnapshot : AutoCloseable {
    val worldUuid: UUID

    /** 複製対象となるワールドの実ディレクトリです。 */
    val worldDirectory: File

    /**
     * 書き込み静止の確立に成功した場合のみ true です。
     * false の場合はディレクトリを複製せず、[failureReason] を記録して中止してください。
     */
    val quiescent: Boolean

    /** [quiescent] が false のときの失敗理由です。 */
    val failureReason: String?

    /**
     * 取得後も書き込み停止が保たれているかを検査します。
     *
     * 複製中に新しい書き込みが検出された場合は false を返します。false が返った
     * スナップショットからの複製物は世代不整合を含み得るため破棄してください。
     */
    fun isStillQuiescent(): Boolean

    /** 静止を解除し、ワールドを取得前の状態（ロード済み・自動保存有効）へ戻します。 */
    override fun close()
}
