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
        var viewDist: Int = 45
        private set
    }

    override fun onEnable() {
        this.getCommand("Quests").executor = CommandHandler
        Bukkit.getPluginManager().registerEvents(EventListener, this)
        instance = this
        this.saveDefaultConfig()
        protocolManager = ProtocolLibrary.getProtocolManager()
        setting = Bukkit.getWorld(this.config.getString("mainWorld"))
        viewDist = this.config.getInt("npcViewDist")

        DataManager.buildFileSystem()
        DataManager.loadQuests()
        DataManager.loadAvailableSkins()
        DataManager.loadNPCs(setting!!)

        PacketHandler.registerAll()
    }

    override fun onDisable() {
        DataManager.saveDirtyPlayers()
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
                    PacketHandler.scanVisibleEntities(player, player.location)

                    player.sendMessage("Quest mode active")
                }
                "leave" -> {
                    player.getData().questing = false
                    player.getData().lastLocation = DataManager.SimpleLocation.fromSimpleBukkit(player.location)

                    PacketHandler.removeAllEntities(player)
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
                "completed" -> {
                    if(player.getData().completedQuests.isEmpty() && player.getData().activeQuest == -1) {
                        player.sendMessage("You do not have any quests active or completed")
                        return true
                    }
                    player.sendMessage("Quests:")
                    player.getData().completedQuests.forEach { questId ->
                        player.sendMessage("${ChatColor.GREEN} - ${DataManager.questDirectory[questId]!!.title}")
                    }
                    if(player.getData().activeQuest != -1) {
                        player.sendMessage("${ChatColor.YELLOW} - ${DataManager.questDirectory[player.getData().activeQuest]!!.title}")
                    }
                }
                "purge" -> {
                    DataManager.playerData.remove(player.uniqueId)
                    DataManager.getPlayerDataLocation(player.uniqueId).delete()
                    player.sendMessage("Data removed and deleted. You're off the grid")
                }
                "reload" -> {
                    if(!player.hasPermission(PermissionManager.adminPermission)) return false
                    Bukkit.getOnlinePlayers().forEach { p -> PacketHandler.removeAllEntities(p) }
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
                    if(!player.hasPermission(PermissionManager.staffPermission)) return false
                    // force non dirty save
                    DataManager.playerData.forEach { uuid, _ -> DataManager.savePlayerData(uuid) }
                    player.sendMessage("All player data saved")
                }
                "bknd" -> {
                    ChatMenuController.handleClick(UUID.fromString(args[1]), args[2].toInt())
                }
                "loc" -> {
                    val l = DataManager.SimpleLocation.fromSimpleBukkit(player.location)
                    println("Player at: ${JSON.indented.stringify(l)}")
                    val tb = player.getTargetBlock(mutableSetOf(Material.AIR), 5)
                    if(tb != null) {
                        val r = DataManager.SimpleLocation.fromSimpleBukkit(tb.location)
                        println("Looking at: ${JSON.indented.stringify(r)}")
                    }
                    player.sendMessage("Location logged to console")
                }
                "stats" -> {
                    if(!player.hasPermission(PermissionManager.staffPermission)) return false
                    DataManager.npcsDirectory.forEach {
                        _, npc -> println(JSON.indented.stringify(npc))
                    }
                    DataManager.questDirectory.forEach {
                        _, q -> println(JSON.indented.stringify(q))
                    }
                }
                "saveskin" -> {
                    if(!player.hasPermission(PermissionManager.adminPermission)) return false
                    DataManager.acquireSkin(if(args.size > 2) args[2] else "D4llen", args[1])
                }
                "loaddata" -> {
                    if(!player.hasPermission(PermissionManager.adminPermission)) return false
                    DataManager.loadFile(args[1], args[2])
                    player.sendMessage("${ChatColor.GREEN}Saved to ${args[2]}!")
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
            PacketHandler.scanVisibleEntities(e.player, e.to)
        }

        @EventHandler
        fun onJoin(e: PlayerJoinEvent) {
            println("Add plr ${e.player.name}")

            // this ensures that the data will be ready to go
            if(e.player.isQuester()) {
                e.player.getData()
                PacketHandler.scanVisibleEntities(e.player, e.player.location)
            }
        }

        @EventHandler
        fun onLeave(e: PlayerQuitEvent) {
            println("Rm plr ${e.player.name}")
            DataManager.savePlayerData(e.player.uniqueId)
            PacketHandler.removeAllEntities(e.player)
        }

        @EventHandler
        fun onWorldSave(e: WorldSaveEvent) {
            DataManager.saveDirtyPlayers()
        }

        @EventHandler
        fun onClick(e: PlayerInteractEvent) {
            val obj = e.player.getData().activeQuestObject()?.findInteractObjective(e.clickedBlock.location)
            if(obj != null) {
                e.player.getData().completedObjectives.add(obj.id)
                e.player.sendMessage("Objective Completed!")
            }
        }
    }
}