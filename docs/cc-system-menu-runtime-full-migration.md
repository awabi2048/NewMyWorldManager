# MWM / Chanpon CC-System Menu Runtime 完全移行計画

## 文書の位置付け

- このファイルを本作業の唯一の正本とする。
- 実装判断、対象一覧、進捗、検証結果、未完了事項は必ずこのファイルへ反映する。
- 会話、タスク、作業メモ、コミットメッセージだけを仕様の保存先にしない。
- 再開時は、最初に本書、ワークスペースの `AGENTS.md`、`.docs/specs/ui/cross-system-ui-design.md` を読む。
- 実装中に判明した追加対象は、コード変更より先に「対象台帳」へ追加する。

最終更新日: 2026-07-25

## 現在の基準コミット

| モジュール | メインブランチ | 基準コミット |
|---|---|---|
| CC-System | `master` | `ef261eefa3bd17154e5eddc3bb54ba3793478f44` |
| MyWorldManager | `master` | `30c3460643acffef0e27b45190403c85b39b8279` |
| MWM-Chanpon | `main` | `a398bf19ad434fb47a4d92f998b96fc7c0f793d5` |
| Chanpon-Utilities | `main` | `328983ad676ddf319d38cc12eb081b92f79050fd` |

## 最終目標

MWM / Chanpon関連のInventory GUI、Paper Dialog、Bedrock Formについて、描画、入力受付、実行可否、成功・拒否、効果音、再描画、画面遷移、履歴、閉じる処理をCC-System Runtimeへ完全移管する。

機能モジュールは次だけを担当する。

- 画面へ表示する意味データ
- 権限と状態に基づく実行可否
- ドメイン処理
- Actionの結果
- 次のRoute

次の個別実装を最終状態に残さない。

- GUI表示目的の`Bukkit.createInventory`
- GUI Action受付目的の`InventoryClickEvent`
- 直接の`Dialog.create`
- 直接のFloodgate/Cumulus Form生成
- 個別の`playClickSound` / `playAdminClickSound`
- 個別のGUI履歴スタック
- タイトル文字列による画面識別
- 表示名やMaterialだけによるAction判定

## CC-Systemの正規契約

### Inventory

- `InventoryMenuDefinition`
- `MenuRoute`
- `MenuElement`
- `GuiElementRole`
- `MenuActionResult`
- `MenuUpdate`
- `MenuSoundPolicy`
- `MenuRuntimeService`

### Paper Dialog

- `MenuDialogRequest`
- `MenuDialogInput`
- `MenuDialogButton`
- `MenuDialogService`

### Bedrock Form

- `MenuSimpleFormRequest`
- `MenuCustomFormRequest`
- `MenuFormButton`
- `MenuFormInput`
- `MenuFormService`

## Routeと画面寿命

| 種別 | 用途 | 遷移 |
|---|---|---|
| ROOT | コマンド等から開く入口 | `ROOT` |
| ROUTED | 戻る対象となり、再オープン可能な通常画面 | `NAVIGATE` |
| REPLACE | 同じ画面のページ・状態更新 | `REPLACE`または`Refresh` |
| EPHEMERAL | ポータル現地設定など、親の履歴を変更しない一時画面 | `PRESERVE_HISTORY` |

すべてのROUTED画面は、ownerとroute idに対応する再オープン処理を登録する。再オープン不能な画面を履歴へ積まない。

## 既知の不具合と根本原因

### ポータル設定画面を閉じると追跡が解除される

`PortalGui`が再オープン不能な一時Inventoryを通常遷移として表示し、Close処理が現在Routeと履歴を消去することが原因。

対処:

- `PortalGui`をEPHEMERAL画面としてRuntime定義化する。
- `PRESERVE_HISTORY`を使用する。
- タイトルや手動セッションではなく、RuntimeのRouteとActionで管理する。

### 管理画面のクリック音が欠落する

個別ListenerがActionを処理しており、Runtimeが成功・拒否を認識できないことが原因。

対処:

- ActionをRuntimeへ移す。
- 実行可能Actionは既定音を1回だけ再生する。
- 確認、取消、戻る、ナビゲーションはRoleに応じた音を使用する。
- 装飾、空欄、無効Actionは成功音を鳴らさない。
- 個別の音呼び出しを削除する。

## 2026-07-25監査結果

| 対象 | Inventory生成 | Runtime定義 | 個別クリック処理 | 判定 |
|---|---:|---:|---:|---|
| MyWorldManager | 46か所・21ファイル | 0件 | 62参照・27ファイル | 未移行 |
| MWM-Chanpon | 40か所・17ファイル | 0件 | 72参照・21ファイル | 未移行 |
| Chanpon-Utilities | 0件 | 0件 | 安全制御用Listenerのみ | GUI移行対象なし |

追加監査:

- MyWorldManagerの`ManagedMenuPresenter.open`: 65回
- MWM-Chanponの`ManagedMenuPresenter.open`: 41回
- MyWorldManagerの直接`Dialog.create`: 21か所・12ファイル
- MWM-Chanponの直接`Dialog.create`: 6か所・4ファイル
- MyWorldManagerの個別クリック音呼び出し: 215参照
- MWM-Chanponの個別クリック音呼び出し: 33参照
- Bedrock Formは`FloodgateFormBridge`からCC-System `MenuFormService`へ到達しており、主要経路は移行済み

`ManagedMenuPresenter.open`だけを呼ぶ画面は完全移行と判定しない。Action、Role、音、履歴がRuntime所有になった場合だけ完了とする。

## 対象台帳

### MyWorldManager Inventory

- [x] `AdminCommandGui`
- [x] `AdminPortalGui`
- [ ] `CreationGui`
- [ ] `DiscoveryGui`
- [ ] `EnvironmentGui`
- [ ] `FavoriteGui`
- [x] `FavoriteMenuGui`
- [x] `InviteGui`
- [x] `MeetGui`
- [x] `PendingInteractionGui`
- [ ] `PlayerWorldGui`
- [x] `PortalGui`
- [ ] `TemplateWizardGui`
- [ ] `TourGui`
- [x] `UserSettingsGui`
- [x] `VisitGui`
- [x] `VisitWorldGui`
- [ ] `WorldGui`
- [ ] `WorldSettingsGui`
- [ ] `BedrockMenuService`のInventory代替画面
- [ ] `GuiHelper`内の旧Inventory生成経路
- [ ] 各種Confirmation Inventory
- [ ] `WorldMigrationService`の確認画面

### MyWorldManager Dialog

- [ ] `AnnouncementDialogManager`
- [ ] `CreationDialogManager`
- [ ] `DialogConfirmManager`
- [ ] `LikeSignDialogManager`
- [ ] `TourDialogManager`
- [ ] `AdminGuiListener`
- [ ] `DiscoveryListener`
- [ ] `PlayerWorldListener`
- [ ] `TemplateWizardListener`
- [ ] `WorldSettingsListener`
- [ ] `VisitCommand`
- [ ] `VisitWorldCommand`

### MWM-Chanpon Inventory

- [x] `AnnualArchiveFlow`
- [x] `AutomationSettingsMenu`
- [x] `BackupListMenu`
- [x] `ChanponAdminMenu`
- [x] `ChanponAdminWorldListMenu`
- [ ] `ChanponDiscoveryMenuProvider`
- [ ] `ChanponEnvironmentGui`
- [ ] `ChanponFavoriteVisitMenuProvider`
- [ ] `ChanponPlayerWorldMenuProvider`
- [ ] `ChanponWorldSettingsMenuProvider`
- [x] `SubmissionAdminMenu`
- [x] `SubmittedWorldMenuProvider`
- [x] `ToolPermissionMenu`
- [x] `WorldDataExportMenu`
- [x] `WorldDataManagementMenu`
- [ ] `ProductionToggleExtension`
- [ ] `WorldBackupMenuExtension`

### MWM-Chanpon Dialog

- [ ] `ChanponWorldMenuAccessProvider`
- [ ] `EasyVoidWorldCreationService`
- [ ] `WorldBackupMenuExtension`
- [ ] `ChanponEnvironmentGui`

### Chanpon-Utilities

- [x] 独自Inventory GUIなし
- [x] 直接Dialogなし
- [x] 直接Formなし
- [ ] 今後GUIを追加する場合にCC-System Runtimeを必須とする構造テストを追加

## ワールド出力の確定仕様

### 年度

MWM-Chanpon configへ必須設定を追加する。

```yaml
standalone_export:
  fiscal_year: 2026
```

- 未設定、数値以外、許容範囲外は起動時エラー。
- 現在年への暗黙フォールバックは行わない。

### 出力名

- 単一: `［さばちゃんぽん<年度>］【<ワールド名>】.zip`
- 複数: `［さばちゃんぽん<年度>］【<代表ワールド名>】ほか.zip`

ワールド名にファイルシステム禁止文字を設定できないよう、MyWorldManagerの名前検証で拒否する。出力時に別文字へ置換しない。出力側でも防御的に検証し、不正値なら失敗させる。

拒否対象:

- `/ \ : * ? " < > |`
- U+0000～U+001Fの制御文字
- 改行

### 選択順と代表ワールド

- 選択値をSetだけで保持しない。
- 選択順を保持するか、`primaryWorldUuid`を別フィールドとして保持する。
- 出力前にRuntimeのConfirmation画面を表示する。
- 代表ワールド、対象数、出力名、スポーン座標を確認させる。
- 確認された代表ワールドをオーバーワールドにする。

### level.dat

稼働サーバーのメインワールドディレクトリにある`level.dat`を共有データの正本としてコピーし、出力時に必要な項目だけを書き換える。選択した代表ワールドの`level.dat`は使用しない。

- `LastPlayed`: 出力時刻
- `Version` / `DataVersion`: 稼働中サーバーのMinecraftバージョン
- `LevelName`: 代表ワールド名
- `GameType = 1`: クリエイティブ
- `allowCommands = 1b`: チートあり
- `SpawnX/Y/Z`と`SpawnAngle`: 代表ワールドの保存済みスポーン
- プレイヤー情報を含める場合はプレイヤーのゲームモードもクリエイティブ
- シード、新規チャンク生成設定、ゲームルール、時刻、天候、ワールド境界など上記以外の共有値: 稼働サーバーのメインワールド

### 追加ワールド

`region`、`entities`、`poi`だけでなく、移植可能な`data`情報を分類して保持する。次はワールド間で衝突するため、単純コピーしない。

- map ID
- raids
- world border
- game rules
- clocks
- scheduled events
- Paper固有データ

各項目について「サーバーのメインワールドを使用」「ディメンション側へ変換」「意図的に除外」を分類し、manifestへ記録する。

### ポータル

- ポータル移動用datapack関数は使用しない。
- ポータル位置をコマンドブロックで必ず上書きする。
- その上を感圧板で必ず上書きする。
- 既存ブロックの有無による警告は不要。
- 元ワールドは変更せず、出力先リージョンだけを書き換える。
- コマンドは対象ディメンションの保存済みスポーンへ転送する。
- 追加カスタムディメンションの登録用datapackは維持する。

## 実装フェーズ

### Phase 0: 再発防止基盤

対象: CC-System、MyWorldManager、MWM-Chanpon、Chanpon-Utilities

- [x] Runtime契約のアーキテクチャテストを作る。
- [x] 許可された基盤コード以外の`Bukkit.createInventory`を禁止する。
- [x] GUI Action用`InventoryClickEvent`を禁止する。
- [x] 直接`Dialog.create`を禁止する。
- [x] 直接Cumulus Form生成を禁止する。
- [x] 個別クリック音を禁止する。
- [x] 旧`ManagedMenuPresenter`を非推奨化し、最終フェーズで削除する。
- [x] 例外が必要なInteractiveStationは、入力スロット契約としてRuntimeへ実装する。

完了条件:

- 新しい違反を追加するとテストが失敗する。
- 既存違反は明示された一時許可リストに限定され、移行ごとに減少する。

### Phase 1: 管理画面・ポータル

対象: MyWorldManager、MWM-Chanpon

- [x] `AdminCommandGui`
- [x] `AdminPortalGui`
- [x] `PortalGui`
- [x] `ChanponAdminMenu`
- [x] `ChanponAdminWorldListMenu`
- [x] `WorldDataManagementMenu`
- [x] `WorldDataExportMenu`
- [x] `AutomationSettingsMenu`

完了条件:

- ポータル設定画面を閉じても親Routeが維持される。
- すべての実行可能クリックで音が1回だけ鳴る。
- 戻る、確認、取消、ページ移動が正しいRoleを持つ。
- 個別Listenerと個別音がない。

### Phase 2: ワールド出力完成

対象: MWM-Chanpon、MyWorldManager

- [x] 年度config
- [x] 不正ワールド名の入口拒否
- [x] 順序付き選択と代表ワールド
- [x] Runtime確認画面
- [x] 出力名
- [x] `level.dat`再構成
- [x] クリエイティブ・チートあり
- [x] スポーン保存
- [x] 追加ワールドデータ分類
- [x] コマンドブロック・感圧板ポータル
- [x] manifest拡張

完了条件:

- 単一・複数の両方を公式バニラ環境で読み込める。
- 日時、バージョン、名前、ゲームモード、チート、スポーンが仕様どおり。
- 代表ワールドがオーバーワールドになる。
- 対象内ポータルが双方向に機能する。

### Phase 3: 一般利用画面

対象: MyWorldManager、MWM-Chanpon

- [ ] ワールド一覧、設定、訪問、発見、お気に入り
- [ ] 招待、メンバー、Meet、Tour
- [ ] 作成、テンプレート、環境設定
- [ ] 確認画面
- [ ] Provider・Extension画面

完了条件:

- 対象台帳のInventory項目がすべて完了。
- 画面タイトルによるクリック振り分けがない。
- Java版とBedrock版が同じActionへ到達する。

### Phase 4: Dialog完全移行

対象: MyWorldManager、MWM-Chanpon

- [ ] 対象台帳の27個の直接Dialog生成を移行する。
- [ ] 入力、確認、取消、閉じる処理を`MenuDialogService`へ統一する。
- [ ] Dialog固有の手動音と手動履歴を削除する。

完了条件:

- MWM / Chanponソースに`Dialog.create`が存在しない。
- Dialog Actionが`MenuActionResult`を返す。

### Phase 5: 旧経路削除

- [ ] `ManagedMenuPresenter`互換ラッパーを削除する。
- [ ] 旧`GuiHelper`生成経路を削除する。
- [ ] GUI用の個別Listenerを削除する。
- [ ] `SoundManager`のメニュークリック用途を削除する。
- [ ] 手動Route履歴を削除する。
- [ ] 一時許可リストを空にする。

完了条件:

- 機械検索で禁止APIが0件。
- 全モジュールの`mvn clean package`成功。
- Runtime契約テスト、Routeテスト、音ポリシーテスト成功。

### Phase 6: デプロイ・実機検証

- [ ] 稼働サーバーをプロセスとログから特定。
- [ ] 対象JARをビルド・配置。
- [ ] RCONで保存して正常停止。
- [ ] 規定ショートカットから再起動。
- [ ] PID、ポート、バージョン、JAR SHA-256、言語リソースを確認。
- [ ] Java版Inventory GUIを実機確認。
- [ ] Paper Dialogを実機確認。
- [ ] Bedrock Formを実機確認。
- [ ] 単一・複数ZIPを完全バニラで読み込み。
- [ ] ポータル、スポーン、クリエイティブ、チートを確認。

## 実装単位とコミット境界

1. CC-Systemのテスト・必要な公開API
2. MyWorldManager管理・ポータル
3. MWM-Chanpon管理・出力
4. MyWorldManager一般画面
5. MWM-Chanpon Provider / Extension
6. Dialog
7. 旧経路削除
8. デプロイ・実機証拠

各単位で次を実施する。

- 対象台帳を更新
- ビルド・テスト
- 作業ブランチへコミット
- リモートへpush
- 次の作業と残件を本書へ記録

## 再開手順

1. 本書の「現在の基準コミット」を確認する。
2. 4モジュールの`git status --short --branch`を確認する。
3. 対象台帳で最初の未完了項目を選ぶ。
4. そのPhaseの完了条件を読む。
5. UI変更前に`.docs/specs/ui/cross-system-ui-design.md`を読む。
6. 対象モジュールごとに作業ブランチを作る。
7. 変更前に禁止APIの件数を再計測する。
8. 実装・テスト後、本書のチェックと証拠を更新する。

## 進捗記録

### 2026-07-25

- 既存の安全機能、書見台読書、拒否理由、選択式出力を各メインブランチへマージ済み。
- Runtime横断監査を実施。
- Inventoryの完全Runtime定義がMWM / MWM-Chanponとも0件であることを確認。
- 直接Dialog、個別音、手動履歴を移行対象として確定。
- 4モジュールで、禁止APIのファイル別件数を完全一致で検査するRuntimeアーキテクチャテストを追加。
- 既存違反はテストリソースの一時許可リストへ固定し、新規追加と無断の件数変化を失敗させる構造にした。
- Chanpon-Utilitiesの`InventoryClickEvent`はGUI Actionではなく、安全制御とFreeCam保護の2 Listenerだけを明示許可した。
- CC-System、MyWorldManager、MWM-Chanponの`ManagedMenuPresenter`を非推奨化した。
- InteractiveStation用の`InventoryMenuView.inputSlots`、表示要素との排他検証、再描画時の入力保持が既にRuntimeへ実装済みであることを確認した。
- Phase 0の4モジュールで`mvn clean package`成功。Runtimeアーキテクチャテストは各モジュールで実行成功。
- Chanpon-Utilitiesへ未許可の`Bukkit.createInventory`参照を一時追加する負例試験を行い、契約テストが`CREATE_INVENTORY`の新規レコードを検出して失敗することを確認後、試験差分を除去して再ビルドした。
- Phase 0完了。次はPhase 1の管理画面・ポータル移行から開始する。
- CC-System 2.12.0へ画面終了ハンドラを追加し、個別`InventoryCloseEvent`なしで確認画面の未決定終了を扱えるようにした。
- `AdminPortalGui`をRuntime Route、Action、Role、既定音へ移行し、`AdminGuiListener`内のタイトル判定、ポータル操作、個別クリック音を削除した。
- `PendingInteractionGui`と共通`ConfirmationMenuGui`をRuntimeへ移行し、確認画面のコールバックと未決定終了をRouteセッションで管理するようにした。
- 単一ワールド出力では、overworld地形を選択した代表ワールドから取得し、`level.dat`と共有`data/`を稼働サーバーのメインワールドディレクトリから取得するようにした。
- 共有データのコピー前に稼働サーバーのメインワールドを保存し、メモリ上の最新状態を`level.dat`と`data/`へ反映してから出力するようにした。
- 追加ワールドの`data/`をディメンション固有、サーバーメインワールド使用、除外へ分類し、方針をmanifestへ記録するようにした。
- `ChanponAdminMenu`とスポーンワールド確認画面をRuntime Route、Action、Roleへ移行し、個別Inventory生成と個別クリックListenerを削除した。
- `AutomationSettingsMenu`の本体、設定切替確認、復元点作成確認をRuntimeへ移行し、3個の直接Inventory生成と4個の個別クリックイベント分岐を削除した。
- `WorldDataManagementMenu`の一覧、単体・一括バックアップ確認、一括ロールバック確認をRuntimeへ移行した。バックアップ一覧から戻るページは旧手動Route履歴ではなく明示状態で維持するようにした。
- `BackupListMenu`の一覧、復元確認、二段階削除確認をRuntimeへ移行し、バックアップID・一覧モード・対象ワールド・ページをRoute payloadへ統一した。
- `AnnualArchiveFlow`を単一インスタンス化し、名前確認と最終実行確認をRuntimeへ移行した。チャット入力Listenerだけを入力受付として残し、InventoryクリックListenerを削除した。
- `SubmissionAdminMenu`の一覧、ページ、進捗フィルタ、並び替え、提出停止、本番用切替、未提出完了確認をRuntimeへ移行し、画面状態と対象UUIDをRoute payloadへ統一した。
- `ChanponAdminWorldListMenu`の現在ワールド表示、一覧、公開状態フィルタ、並び替え、ページ移動、ワープ、設定遷移、情報コピーをRuntimeへ移行した。
- CC-System 2.11.0へ、登録済み画面を親Routeと履歴を変更せず開く`MenuRuntimeService.openEphemeral`を追加した。
- EPHEMERAL画面を閉じた場合に親ナビゲーションを消去しないセッション寿命テストを追加し、CC-Systemの全90テストに成功した。
- `PortalGui`を`InventoryMenuDefinition`、Route payload、Runtime Actionへ完全移行した。直接Inventory生成、個別`InventoryClickEvent`、個別クリック音、ItemTagによるAction判定を撤去した。
- `PortalListener`は単一登録済み`PortalGui`を使用し、ポータル設定を`openEphemeral`で開く。閉じた後も親メニューのRoute追跡を維持する。
- CC-System 2.11.0（SHA-256 `60B41956A3A97053104BC84ABCB49BB0662CB780D204C704DE7B30D5CA63E9AE`）とMyWorldManager 1.10.1（SHA-256 `95620A70220323D59A72CCCE623C2CC289B82275C437A7A1FC52C17824E62AB8`）を`D:\Minecraft\Chiyogami-26.1.2`へ配置した。
- RCON保存後に正常停止し、規定ショートカットからPID 5256で再起動した。RCONで両バージョン、起動完了、対象プラグインの起動例外なしを確認した。
- 資源収集側commit `6090a30`の実質差分から、ProgressPath線長、Loreテスト、日英`tutorial_rank.unit`キーだけをCC-System 2.11.0へ統合した。`pom.xml`は取り込まず、commit `f7ffa32`として保存した。
- JAR内の日英`content/tutorial_rank.yml`に`unit`、`minute`、`experience`が存在することを確認した。
- Minecraft画面取得は`インターフェイスがサポートされていません (0x80004002)`で失敗した。推測座標による入力は行わず、ポータル設定を閉じた後の親Route保持と実際の可聴音は未検証として残す。
