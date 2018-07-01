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
import net.minecraft.server.v1_12_R1.Packet
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import org.bukkit.event.world.WorldSaveEvent
import java.util.*


class QuestEngine : JavaPlugin() {

    companion object {
        var instance: QuestEngine? = null
        var protocolManager: ProtocolManager? = null
        var setting: World? = null
        private set
    }

    override fun onEnable() {
        this.getCommand("Quests").executor = CommandHandler
        Bukkit.getPluginManager().registerEvents(EventListener, this)
        instance = this
        protocolManager = ProtocolLibrary.getProtocolManager()
        setting = Bukkit.getWorlds()[0]

        DataManager.buildFileSystem()
        DataManager.loadQuests()
        DataManager.loadAvailableSkins()
        DataManager.loadNPCs(setting!!)

        PacketHandler.registerAll()
    }

    override fun onDisable() {
        DataManager.playerData.forEach { uuid, _ -> DataManager.savePlayerData(uuid) }
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
                    if(player.getData().lastLocation != null) {
                        player.teleport(player.getData().lastLocation!!.toBukkit(setting!!))
                        player.getData().lastLocation = null
                    }
                    PacketHandler.scanVisibleEntites(player, player.location)

                    player.getData().lastLocation = DataManager.SimpleLocation.fromSimpleBukkit(player.location)

                    // tp player to their last saved location (if we have one)
                    player.sendMessage("Quest mode active")
                }
                "leave" -> {
                    player.getData().questing = false
                    player.getData().lastLocation = DataManager.SimpleLocation.fromSimpleBukkit(player.location)
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
                    DataManager.playerData.remove(player.uniqueId)
                    DataManager.getPlayerDataLocation(player.uniqueId).delete()
                    player.sendMessage("Data removed and deleted. You're off the grid")
                }
                "reload" -> {
                    Bukkit.getOnlinePlayers().forEach { p -> PacketHandler.removeAllEntites(p) }
                    PacketHandler.fakePlayerHandles.clear()

                    DataManager.playerData.clear()
                    DataManager.npcsDirectory.clear()
                    DataManager.questDirectory.clear()
                    DataManager.skinDirectory.clear()

                    DataManager.loadQuests()
                    DataManager.loadAvailableSkins()
                    DataManager.loadNPCs(setting!!)

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
                    println("Player at: ${JSON.indented.stringify(l)}")
                    val tb = player.getTargetBlock(mutableSetOf(Material.AIR), 5)
                    if(tb != null) {
                        val r = DataManager.SimpleLocation(tb.location.x, tb.location.y, tb.location.z)
                        println("Looking at: ${JSON.indented.stringify(r)}")
                    }
                }
                "dev" -> {
                    DataManager.npcsDirectory.forEach {
                        _, npc -> println(JSON.indented.stringify(npc))
                    }
                    DataManager.questDirectory.forEach {
                        _, q -> println(JSON.indented.stringify(q))
                    }
                }
                "savenew" -> {
                    DataManager.acquireSkin("D4llen", args[1])
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
            PacketHandler.removeAllEntites(e.player)
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