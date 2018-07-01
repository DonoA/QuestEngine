package io.dallen.questengine

import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import kotlinx.serialization.json.JSON
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import org.bukkit.event.world.WorldSaveEvent
import java.util.*


class QuestEngine : JavaPlugin() {

    companion object {
        var instance: QuestEngine? = null
        var protocolManager: ProtocolManager? = null
        private set
    }

    override fun onEnable() {
        this.getCommand("Quests").executor = CommandHandler
        Bukkit.getPluginManager().registerEvents(EventListener, this)
        instance = this
        protocolManager = ProtocolLibrary.getProtocolManager()

        DataManager.buildFileSystem()
        DataManager.loadQuests()
        DataManager.loadNPCs(Bukkit.getWorlds()[0])

        PacketHandler.registerAll()
    }

    object CommandHandler : CommandExecutor {
        override fun onCommand(sender: CommandSender?, cmd: Command?, name: String?, args: Array<out String>): Boolean {
            if(args.isEmpty() || sender !is Player) {
                return false
            }
            val player: Player = sender
            when(args[0].toLowerCase()) {
                "join" -> {
                    player.getData().questing = true
                    PacketHandler.scanVisibleEntites(player, player.location)
                    // tp player to their last saved location (if we have one)
                    player.sendMessage("Quest mode active")
                }
                "leave" -> {
                    player.getData().questing = false
                    PacketHandler.removeAllEntites(player)
                    player.sendMessage("Quest mode deactivate")
                }
                "current" -> {
                    if(player.getData().activeQuest == -1) {
                        player.sendMessage("You have no active quests")
                        return true
                    }
                    val activeQuest = DataManager.questDirectory[player.getData().activeQuest]!!
                    player.sendMessage("Active quest: ${activeQuest.title}")
                    activeQuest.objectives.forEach { obj ->
                        val objColor = if(player.getData().completedObjectives.contains(obj.id)) ChatColor.GREEN else ChatColor.RESET
                        player.sendMessage("$objColor - ${obj.createMessage()}")
                    }
                }
                "purge" -> {
                    player.sendMessage("Purge!")
                    // remove all questing data
                }
                "reload" -> {
                    DataManager.playerData.clear()
                    DataManager.npcsDirectory.clear()
                    DataManager.questDirectory.clear()

                    DataManager.loadQuests()
                    DataManager.loadNPCs(Bukkit.getWorlds()[0])

                    player.sendMessage("Reload!")
                }
                "save" -> {
                    DataManager.playerData.forEach { uuid, _ -> DataManager.savePlayerData(uuid) }
                    player.sendMessage("All player data saved")
                }
                "bknd" -> {
                    ChatMenuController.handleClick(UUID.fromString(args[1]), args[2].toInt())
                }
                "loc" -> {
                    val l = DataManager.SimpleLocation(player.location.x, player.location.y, player.location.z)
                    println(JSON.indented.stringify(l))
                }
                "dev" -> {
                    DataManager.npcsDirectory.forEach {
                        _, npc -> println(JSON.indented.stringify(npc))
                    }
                    DataManager.questDirectory.forEach {
                        _, q -> println(JSON.indented.stringify(q))
                    }
                }
                else -> return false
            }
            return true
        }
    }

    object EventListener : Listener {
        @EventHandler
        fun onMove(e: PlayerMoveEvent) {
            if(e.to.distance(e.from) == 0.0) return
            if(!e.player.isQuester()) return
            PacketHandler.scanVisibleEntites(e.player, e.to)
        }

        @EventHandler
        fun onJoin(e: PlayerJoinEvent) {
            println("Add plr ${e.player.name}")

            // this ensures that the data will be ready to go
            if(e.player.isQuester()) {
                e.player.getData()
                PacketHandler.scanVisibleEntites(e.player, e.player.location)
            }
        }

        @EventHandler
        fun onLeave(e: PlayerQuitEvent) {
            println("Rm plr ${e.player.name}")
            DataManager.savePlayerData(e.player.uniqueId)
        }

        @EventHandler
        fun onWorldSave(e: WorldSaveEvent) {
            println("Save all plr data")
            // TODO: add use dirty alg
            DataManager.playerData.forEach { uuid, _ -> DataManager.savePlayerData(uuid) }
        }

        @EventHandler
        fun onClick(e: PlayerInteractEvent) {
            val obj = e.player.getData().findQuest()?.findInteractObjective(e.clickedBlock.location)
            if(obj != null) {
                e.player.getData().completedObjectives.add(obj.id)
                e.player.sendMessage("Objective completed!")
            }
        }
    }
}