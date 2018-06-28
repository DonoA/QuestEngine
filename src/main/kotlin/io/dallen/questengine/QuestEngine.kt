package io.dallen.questengine

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class QuestEngine : JavaPlugin() {

    companion object {
        var instance: QuestEngine? = null
        private set
    }

    override fun onEnable() {
        this.getCommand("Quests").executor = CommandHandler
        instance = this
        DataManager.buildFileSystem()
        DataManager.loadNPCs()
        DataManager.loadQuests()
    }

    object CommandHandler : CommandExecutor {
        override fun onCommand(sender: CommandSender?, cmd: Command?, name: String?, args: Array<out String>): Boolean {
            if(args.isEmpty() || sender !is Player) {
                return false
            }
            val player: Player = sender
            when(args[0].decapitalize()) {
                "join" -> {
                    player.sendMessage("Join!")
                }
                "leave" -> {
                    player.sendMessage("Leave!")
                }
                "purge" -> {
                    player.sendMessage("Purge!")
                }
                "reload" -> {
                    player.sendMessage("Reload!")
                }
                "save" -> {
                    player.sendMessage("Save!")
                }
                "bknd" -> {
                    ChatMenuController.handleClick(player.uniqueId, args[2].toInt())
                }
                "dev" -> {
                    DataManager.npcsDirectory[args[1].toInt()]!!.startConvo(player)
                }
                else -> return false
            }
            return true
        }
    }
}