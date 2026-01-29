# MyWorldManager 管理者・開発者ガイド

本ドキュメントでは、MyWorldManagerの導入、メンテナンス、および構成設定について解説します。

---

## ディレクトリ構造とデータ構成
プラグインデータは `plugins/MyWorldManager/` に集約されています。

### データファイル
- `worlds/`: 各ワールドのメタデータ（UUID別のYAMLファイル）
- `player_stats/`: プレイヤー統計データ（UUID別のYAMLファイル）
    - 所持ポイント、解放済みスロット数、お気に入りワールド、言語設定など
- `portals.yml`: 設置されたポータルのデータ
- `templates.yml`: ワールドテンプレートの定義

### 設定ファイル
- `config.yml`: プラグイン全体の設定
- `menus/`: 各GUIメニューのアイコン、サウンド設定（メニュー単位での管理）
- `lang/`: 言語ファイル（`ja_jp.yml`, `en_us.yml`）

---

## メンテナンス機能
サーバー資源の最適化とワールド整理を目的とした機能です。

### 有効期限とアーカイブ
- **自動延長**: オーナーがログインするたびに、有効期限が30日延長されます。
- **自動選定**: 有効期限を過ぎたワールドがアーカイブ対象として認識されます。
- **手動切替**: `/mwm info` メニューからワールドを右クリックでアーカイブ/復旧を切り替え可能です。

### データ移行
旧形式のデータから新形式への移行機能です。
- **ワールド移行** (`/mwm migrate-worlds`): `world_data.yml` から新しいワールドデータ形式へ移行
- **プレイヤー移行** (`/mwm migrate-players`): `player_data.yml` から新しい統計データ形式へ移行

> ⚠️ 移行コマンドは `config.yml` で有効化する必要があります。

---

## 管理・運用ツール
サーバー運営を効率化するための高度な管理機能です。

### 📋 ワールド管理GUI
全ワールドの状態を一覧表示し、管理できます。
- **コマンド**: `/mwm info`
- **機能**:
    - **一覧表示**: 全登録ワールドをページング表示
    - **テレポート**: 左クリックで該当ワールドへ移動
    - **アーカイブ切替**: 右クリックでアーカイブ/復旧を実行

### 🚪 ポータル一括管理
全ポータルの稼働状況をGUIで一覧確認・操作できます。
- **コマンド**: `/mwm portals`
- **機能**:
    - **テレポート**: 左クリックで該当ポータルの設置場所へ移動
    - **遠隔撤去**: 右クリックでポータルを遠隔削除

### 📦 ワールドデータのエクスポート
ワールドデータとメタデータをパッケージ化して書き出します。
- **コマンド**: `/mwm export <UUID>`
- **機能**: 指定したワールドのディレクトリと管理情報をZip形式で `exports/` フォルダに出力

### 🪄 テンプレート作成ツール
既存のワールドをテンプレートとしてGUI上で登録できます。
- **コマンド**: `/mwm create-template`
- **機能**: アイコン、名称、説明文、初期スポーン地点をGUIから設定し、`templates.yml` へ書き出し

### 🎁 カスタムアイテム付与
- **コマンド**: `/mwm give <player> <item_id> [count]`
- **アイテムID**: `portal` (ワールドポータル)

---

## メニューの構成設定 (Data-Driven)
`menus/` フォルダ内の構成ファイルを編集することで、GUIの挙動を調整可能です。

```yaml
# 例: creation.yml
open_sound:
  sound: "BLOCK_IRON_TRAPDOOR_OPEN"
  pitch: 1.0
icons:
  confirm:
    material: LIME_WOOL
    sound: "ENTITY_EXPERIENCE_ORB_PICKUP"
    pitch: 1.5
```
- 各GUI要素に任意の `Material` および `Sound` を定義できます。
- 装飾用アイテム（枠線など）は一貫性保持のため、システム側で固定（ハードコード）されています。

---

## 管理者コマンド

| コマンド | 説明 | 権限 |
|:---|:---|:---|
| `/mwm reload` | 構成ファイルおよびデータの再読み込み | `myworldmanager.admin` |
| `/mwm create <player>` | 対象プレイヤーに作成ウィザードを提示 | `myworldmanager.admin` |
| `/mwm info` | ワールド管理GUIを開く | `myworldmanager.admin` |
| `/mwm portals` | ポータル一括管理GUIを開く | `myworldmanager.admin` |
| `/mwm export <UUID>` | ワールドデータをZipでエクスポート | `myworldmanager.admin` |
| `/mwm create-template` | テンプレート作成ウィザードの起動 | `myworldmanager.admin` |
| `/mwm stats <player> <field> <action> [value]` | プレイヤー統計値の参照・操作 | `myworldmanager.admin` |
| `/mwm give <player> <item_id> [count]` | カスタムアイテムの付与 | `myworldmanager.admin` |
| `/mwm update-day` | 日次処理（期限チェック等）の強制実行 | `myworldmanager.admin` |
| `/mwm update-data` | 全データの整合性チェック・補正 | `myworldmanager.admin` |
| `/mwm migrate-worlds` | ワールドデータの移行（コンソール専用） | コンソール |
| `/mwm migrate-players` | プレイヤーデータの移行（コンソール専用） | コンソール |

### stats コマンドの詳細
```
/mwm stats <player> <field> <get|set|add|remove> [value]
```
- **field**: `points`, `warp-slots`, `world-slots`
- **例**: `/mwm stats Steve points add 100`

---

## 構成の要点 (config.yml)

| 設定項目 | 説明 | デフォルト |
|:---|:---|:---|
| `creation_cost.template/seed/random` | 作成タイプ別のポイントコスト | 0/200/50 |
| `default_expiration.initial_days` | 初期有効期限（日） | 90 |
| `default_expiration.extension_days` | ログイン時の延長日数 | 30 |
| `expansion.initial_size` | 初期ワールドサイズ | 100 |
| `expansion.costs.1/2/3` | 拡張段階別コスト | 100/200/400 |
| `evacuation_location` | 退避位置（削除・アクセス権喪失時） | - |
| `template_preview` | プレビューの回転時間・カメラ設定 | 15秒 |
| `critical_settings.refund_percentage` | 削除時の払い戻し割合 | 0.5 (50%) |
| `announcement.max_lines/max_line_length` | 案内メッセージの制限 | 5行/100文字 |

---
*Version: 0.1-alpha*
*Last Updated: 2026-01-25*

