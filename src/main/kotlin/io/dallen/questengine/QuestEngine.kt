package io.dallen.questengine

import org.bukkit.plugin.java.JavaPlugin

class QuestEngine : JavaPlugin() {

    companion object {
        var instance: QuestEngine? = null
        private set
    }

    override fun onEnable() {
        instance = this
    }
}