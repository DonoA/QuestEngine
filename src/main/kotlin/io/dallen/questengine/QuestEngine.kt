package io.dallen.questengine

import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import com.comphenix.protocol.wrappers.WrappedGameProfile
import com.comphenix.protocol.wrappers.PlayerInfoData
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter
import net.minecraft.server.v1_12_R1.PacketPlayInUseEntity
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent


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

        val playerInteractAdapter = object : PacketAdapter(QuestEngine.instance, PacketType.Play.Client.USE_ENTITY) {
            override fun onPacketReceiving(event: PacketEvent?) {
                val interact = CraftBukkitHandler.decodeInteractPacket(event!!)
                // TODO: add delay here for multi event fire
                println(interact)
            }
        }
        protocolManager!!.addPacketListener(playerInteractAdapter)
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
                "spawn" -> {
                    val uuid = CraftBukkitHandler.registerNPC("TestNpc", player.location)
                    CraftBukkitHandler.spawnPlayerEntity(player, uuid)
                }
                else -> return false
            }
            return true
        }
    }

    object EventListener : Listener {
        @EventHandler
        fun onClick(e: PlayerInteractEntityEvent) {
            println(e)
        }
    }
}