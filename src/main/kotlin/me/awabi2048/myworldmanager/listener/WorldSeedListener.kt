                if (!consumed) {
                    player.sendMessage(plugin.languageManager.getMessage(player, "messages.item_not_found_hand"))
                    player.closeInventory()
                    return
                }
