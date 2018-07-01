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
    data class QuestPreReq(val id: Int, val timeout: Int = 0)

    @Serializable
    data class QuestObjectiveParameters(val location: SimpleLocation? = null)

    @Serializable
    data class QuestObjective(val id: Int, val type: String, val info: String, val parameters: QuestObjectiveParameters,
                              val preReq: List<QuestPreReq> = emptyList()) {
        fun createMessage(): String {
            return "$type, $info"
        }
    }

    @Serializable
    data class Quest(val id: Int, val title: String, val objectives: List<QuestObjective>) {
        fun findInteractObjective(loc: Location): QuestObjective {
            return objectives.first {
                obj -> obj.type == "interact" && obj.parameters.location!!.toBukkit(loc.world) == loc
            }
        }
    }

    @Serializable
    data class ConversationOption(val text: String, val action: String) {
        fun executeAction(player: Player, owner: NPC, state: NPCState) {
            val args = action.split(" ")
            println("exec " + args.toString())
            when(args[0].toLowerCase()) {
                "end" -> owner.sendMessage(player, "Goodbye")
                "nextpoint" -> {
                    state.getPointById(args[1].toInt()).sendPoint(player, owner, state)
                }
                "addquest" -> {
                    if(player.getData().activeQuest != -1) {
                        owner.sendMessage(player, "It seems you are already working on a task")
                    } else {
                        player.getData().activeQuest = args[1].toInt()
                        player.sendMessage("Quest Accepted!")
                    }
                }
                "finishquest" -> {
                    player.sendMessage("Quest Completed!")
                    player.getData().completedQuests.add(player.getData().activeQuest)
                    player.getData().activeQuest = -1
                    player.getData().completedObjectives.clear()
                }
                "obj" -> {
                    player.sendMessage("Objective Completed!")
                    player.getData().completedObjectives.add(args[1].toInt())
                }
            }
        }
    }

    @Serializable
    data class ConversationPoint(val id: Int, val text: String, val options: List<ConversationOption>) {
        fun sendPoint(player: Player, owner: NPC, state: NPCState) {
            val menu = ChatMenuController.ChatMenu(
                    options.map { op -> ChatMenuController.ChatMenuOption(op.text, ChatColor.AQUA) },
                    { id, _ -> options[id].executeAction(player, owner, state) }
            )
            owner.sendMessage(player, text)
            ChatMenuController.sendMenu(player, menu)
        }
    }

    @Serializable
    data class NPCStateReq(val activeQuest: Int?, val completedObjectives: List<Int>, val completedQuests: List<Int>)

    @Serializable
    data class NPCState(val location: SimpleLocation, val stateRequirements: NPCStateReq,
                        val convoPoints: List<ConversationPoint>) {
        fun fitsReqs(pd: PlayerData): Boolean {
            if(stateRequirements.activeQuest != null && pd.activeQuest != stateRequirements.activeQuest) return false
            if(!pd.completedObjectives.containsAll(stateRequirements.completedObjectives)) return false
            if(!pd.completedQuests.containsAll(stateRequirements.completedQuests)) return false
            return true
        }

        fun getPointById(id: Int) = convoPoints.first { e -> e.id == id }

        fun startConvo(player: Player, npc: NPC) {
            convoPoints.first().sendPoint(player, npc, this)
        }
    }

    @Serializable
    data class NPC(val id: Int, val name: String, val states: List<NPCState>) {
        fun state(pd: PlayerData) = states.last { e-> e.fitsReqs(pd) }

        fun sendMessage(player: Player, text: String) {
            player.sendMessage("${ChatColor.GREEN}$name${ChatColor.RESET}: $text")
        }
    }

    @Serializable
    data class PlayerData(val uuid: String, var questing: Boolean, var activeQuest: Int = -1,
                          val completedObjectives: MutableList<Int> = mutableListOf(),
                          val completedQuests: MutableList<Int> = mutableListOf(),
                          var lastLocation: SimpleLocation? = null) {
        fun findQuest() = questDirectory[activeQuest]
    }

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
            println(npcFile)
            val loadedNPC = JSON.parse<NPC>(File(npcDir.path + "/" + npcFile).readText())
            loadedNPC.states.forEach { state ->
                PacketHandler.registerNPC(loadedNPC.name, state.location.toBukkit(world),
                    { p -> loadedNPC.state(p.getData()) == state },
                    { e -> state.startConvo(e.player, loadedNPC) }
                )
            }

            npcsDirectory[loadedNPC.id] = loadedNPC
        }
    }

    fun getPlayerDataLocation(uuid: UUID) =
            File("${QuestEngine.instance!!.dataFolder.path}/playerdata/$uuid.json")

    fun loadPlayerData(uuid: UUID): PlayerData {
        val plrDataFile = getPlayerDataLocation(uuid)
        val plrData = if(plrDataFile.exists()) JSON.parse(plrDataFile.readText())
                  else PlayerData(uuid.toString(), true)
        playerData[uuid] = plrData
        return plrData
    }

    fun savePlayerData(uuid: UUID) {
        val plrDataFile = getPlayerDataLocation(uuid)
        plrDataFile.writeText(JSON.stringify(playerData[uuid]!!))
    }
}