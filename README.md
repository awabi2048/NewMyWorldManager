# My World Manager プラグイン
## 概要
CrafterCrossing向けに制作された、マイワールドシステムを管理するためのプラグインです。
サーバー依存の設計は行なっておらず、他のサーバーでも利用することができます。

## 互換性
このプラグインは、現在`Chiyogami v0.0.7-1.21.8`を使用した場合のみをサポートしています。Paper系列のサーバーソフトであれば動作すると思われますが、
このプラグインでは用途により大量のワールドが同時にロードされる場合があるため、Chiyogamiを併用することを強く推奨します。

## Special Thanks
- bea4dev 様（Chiyogami開発者 https://github.com/bea4dev/Chiyogami ）
- kotarobo_ 様（こたサーバー／CrafterCrossing オーナー）

## API（Postイベント）
他プラグイン向けに、MyWorldManagerの主要な内部処理をBukkitイベントとして受け取れるようになりました。

- `me.awabi2048.myworldmanager.api.event.MwmWorldCreatedEvent`
- `me.awabi2048.myworldmanager.api.event.MwmWorldDeletedEvent`
- `me.awabi2048.myworldmanager.api.event.MwmWorldWarpedEvent`
- `me.awabi2048.myworldmanager.api.event.MwmMemberAddedEvent`
- `me.awabi2048.myworldmanager.api.event.MwmMemberRemovedEvent`
- `me.awabi2048.myworldmanager.api.event.MwmOwnerTransferredEvent`
- `me.awabi2048.myworldmanager.api.event.MwmDailyMaintenanceCompletedEvent`
- `me.awabi2048.myworldmanager.api.event.MwmWorldFavoritedEvent`
- `me.awabi2048.myworldmanager.api.event.MwmLikeSignLikedEvent`
- `me.awabi2048.myworldmanager.api.event.MwmWorldVisitedEvent`

### 使い方（他プラグイン側）
`plugin.yml` に `softdepend: [MyWorldManager]` を指定し、通常の `@EventHandler` で購読してください。

```kotlin
@EventHandler
fun onMemberAdded(event: MwmMemberAddedEvent) {
    logger.info("member=${event.memberName}, world=${event.worldUuid}, source=${event.source}")
}
```

### 所有ワールド一覧の取得
`MyWorldManager` のインスタンスから、プレイヤー所有ワールド (`WorldData`) の一覧を取得できます。

```kotlin
val mwm = server.pluginManager.getPlugin("MyWorldManager") as? me.awabi2048.myworldmanager.MyWorldManager
val ownedWorlds = mwm?.getOwnedWorlds(player).orEmpty()
```
