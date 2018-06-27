package io.dallen.questengine

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.md_5.bungee.api.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class QuestEngine : JavaPlugin() {

    companion object {
        var instance: QuestEngine? = null
        var objectParser: ObjectMapper = ObjectMapper().registerKotlinModule()
        private set
    }

    override fun onEnable() {
        this.getCommand("Quests").executor = CommandHandler
        instance = this
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
                    if(args[1] == "menu") {
                        ChatMenuController.sendMenu(player, ChatMenuController.ChatMenu(listOf(
                        ChatMenuController.ChatMenuOption("Op1", ChatColor.AQUA),
                        ChatMenuController.ChatMenuOption("Op2", ChatColor.RED)
                        ), { id, clicked ->
                            player.sendMessage("Clicked ${clicked.name}")
                        }))
                    }
                }
                else -> return false
            }
            return true
        }
    }
}