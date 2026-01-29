$path = 'c:\Users\wwwww\.gemini\antigravity\scratch\my_world_manager\src\main\kotlin\me\awabi2048\myworldmanager\listener\WorldSettingsListener.kt'
$c = Get-Content $path -Raw -Encoding UTF8
$target = 'player.sendMessage\(".*"\)'
$replacement = 'player.sendMessage("§cポイントが不足しています。")'
# Replace only the one in the EXPANSION_CONFIRM block which is currently broken
# It's unique enough (hopefully) or I can use a more specific regex
$c = $c -replace 'if \(stats\.worldPoint < cost\) \{\s*player\.sendMessage\(".*"\)', 'if (stats.worldPoint < cost) {`n                        player.sendMessage("§cポイントが不足しています。")'
$c | Set-Content $path -Encoding UTF8
