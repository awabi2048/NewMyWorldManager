package me.awabi2048.myworldmanager.service

object UnloadedWorldRegistry {
    private val unloadedWorlds = mutableSetOf<String>()

    fun register(worldName: String) {
        unloadedWorlds.add(worldName)
    }

    fun unregister(worldName: String) {
        unloadedWorlds.remove(worldName)
    }

    fun isUnloaded(worldName: String): Boolean {
        return unloadedWorlds.contains(worldName)
    }
}
