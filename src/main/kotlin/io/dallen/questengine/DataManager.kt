package io.dallen.questengine

import kotlinx.serialization.*
import kotlinx.serialization.json.JSON
import net.md_5.bungee.api.ChatColor
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import java.io.File
import java.util.*

object DataManager {

    @Serializable
    data class SimpleLocation(val x: Double, val y: Double, val z: Double, val pitch: Float = 0f, val yaw: Float = 0f) {
        fun toBukkit(world: World): Location = Location(world, x, y, z, pitch, yaw)
    }

    @Serializable
    data class QuestPreReq(val id: Int, val timeout: Int? = null)

    @Serializable
    data class QuestObjectiveParameters(val npc: Int? = null, val location: SimpleLocation? = null)

    @Serializable
    data class QuestObjective(val id: Int, val type: String, val parameters: QuestObjectiveParameters, val preReq: List<QuestPreReq> = emptyList())

    @Serializable
    data class Quest(val id: Int, val title: String, val objectives: List<QuestObjective>)

    @Serializable
    data class ConversationOption(val text: String, val action: String) {
        fun executeAction(player: Player, owner: NPC) {
            // todo do this
            val args = action.split(" ")
            println("exec " + args.toString())
            when(args[0].toLowerCase()) {
                "end" -> owner.sendMessage(player, "Goodbye")
                "nextpoint" -> {
                    owner.getPointById(args[1].toInt()).sendPoint(player, owner)
                }
                "addquest" -> {
                    owner.sendMessage(player, "(gives quest)")
                    // assign quest to player asking
                }
            }
        }
    }

    @Serializable
    data class ConversationPoint(val id: Int, val text: String, val options: List<ConversationOption>) {
        fun sendPoint(player: Player, owner: NPC) {
            val menu = ChatMenuController.ChatMenu(
                    options.map { op -> ChatMenuController.ChatMenuOption(op.text, ChatColor.AQUA) },
                    { id, _ -> options[id].executeAction(player, owner) }
            )
            owner.sendMessage(player, text)
            ChatMenuController.sendMenu(player, menu)
        }
    }

    @Serializable
    data class NPC(val id: Int, val name: String, val location: SimpleLocation, val convoPoints: List<ConversationPoint>) {
        fun startConvo(player: Player) {
            convoPoints.first().sendPoint(player, this)
        }

        fun getPointById(id: Int) = convoPoints.first { e -> e.id == id }

        fun sendMessage(player: Player, text: String) {
            player.sendMessage("${ChatColor.GREEN}$name${ChatColor.RESET}: $text")
        }
    }

    @Serializable
    data class PlayerData(val uuid: String, var questing: Boolean, var activeQuest: Int?, val completedQuests: List<Int> = emptyList(), var lastLocation: SimpleLocation? = null)

    val questDirectory: HashMap<Int, Quest> = HashMap()

    val npcsDirectory: HashMap<Int, NPC> = HashMap()

    val playerData: HashMap<UUID, PlayerData> = HashMap()

    fun buildFileSystem() {
        for(f in arrayOf("quests", "npcs", "playerdata")) {
            val dir = File(QuestEngine.instance!!.dataFolder.path + "/" + f)
            println("mk " + dir.path)
            if(!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    fun loadQuests() {
        val questDir = File(QuestEngine.instance!!.dataFolder.path + "/quests")
        for(questFile in questDir.list()) {
            val loadedQuest = JSON.parse<Quest>(File(questDir.path + "/" + questFile).readText())
            questDirectory[loadedQuest.id] = loadedQuest
        }
    }

    fun loadNPCs(world: World) {
        val npcDir = File(QuestEngine.instance!!.dataFolder.path + "/npcs")
        for(npcFile in npcDir.list()) {
            val loadedNPC = JSON.parse<NPC>(File(npcDir.path + "/" + npcFile).readText())
            PacketHandler.registerNPC(loadedNPC.name, loadedNPC.location.toBukkit(world)) { e -> loadedNPC.startConvo(e.player) }
            npcsDirectory[loadedNPC.id] = loadedNPC
        }
    }

    fun getPlayerDataLocation(uuid: UUID) =
            File("${QuestEngine.instance!!.dataFolder.path}/playerdata/$uuid.json")

    fun loadPlayerData(uuid: UUID): PlayerData {
        val plrDataFile = getPlayerDataLocation(uuid)
        val plrData = if(plrDataFile.exists()) JSON.parse(plrDataFile.readText())
                  else PlayerData(uuid.toString(), true, null)
        playerData[uuid] = plrData
        return plrData
    }

    fun savePlayerData(uuid: UUID) {
        val plrDataFile = getPlayerDataLocation(uuid)
        plrDataFile.writeText(JSON.stringify(playerData[uuid]!!))
    }
}