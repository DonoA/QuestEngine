package io.dallen.questengine

import kotlinx.serialization.Optional
import kotlinx.serialization.Serializable
import net.md_5.bungee.api.ChatColor
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.*

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
            "addobj" -> {
                player.sendMessage("Objective Completed!")
                player.getData().completedObjectives.add(args[1].toInt())
            }
            "removeobj" -> {
                player.sendMessage("Objective Removed!")
                player.getData().completedObjectives.remove(args[1].toInt())
            }
            "joinfaction" -> {
                player.sendMessage("Faction Joined!")
                player.getData().faction = args[1].toInt()
            }
            "leavefaction" -> {
                player.sendMessage("Faction Left!")
                player.getData().faction = -1
            }
            "modfacrep" -> {
                player.getData().factionRep += args[1].toInt()
                player.sendMessage("Rep changed to ${player.getData().factionRep}")
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
data class NPCState(val location: DataManager.SimpleLocation, val stateRequirements: NPCStateReq,
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
data class NPC(val id: Int, val name: String, @Optional val skinName: String? = null, val states: List<NPCState>) {
    fun state(pd: PlayerData) = states.last { e-> e.fitsReqs(pd) }

    fun sendMessage(player: Player, text: String) {
        player.sendMessage("${ChatColor.GREEN}$name${ChatColor.RESET}: $text")
    }
}

object NPCManager {
    val fakePlayerHandles: HashMap<Int, PacketHandler.FakePlayerEntity> = HashMap()

    val renderedFakePlayers: HashMap<UUID, MutableSet<Int>> = HashMap()

    fun scanVisibleEntities(p: Player, loc: Location) {
        // TODO: this is quite poorly optimized and should fixed at some point
        fakePlayerHandles.forEach { id, npc ->
            // View distance for npcs is 45, could be changed if needed
            if(npc.visible.invoke(p) && npc.location.distance(loc) < 45.0) {
                tryRenderPlayerEntity(p, id)
            } else {
                tryRemoveRenderedPlayerEntity(p, id)
            }
        }
    }

    fun removeAllEntities(p: Player) {
        renderedFakePlayers.forEach { _, npcs ->
            npcs.toMutableSet().forEach { npcId ->
                tryRemoveRenderedPlayerEntity(p, npcId)
            }
        }
    }

    fun tryRenderPlayerEntity(p: Player, id: Int) {
        val l  = renderedFakePlayers.findOrCreate(p.uniqueId, mutableSetOf())
        if(l.contains(id)) return
        PacketHandler.spawnPlayerEntity(p, id)
        l.add(id)
    }

    fun tryRemoveRenderedPlayerEntity(p: Player, id: Int) {
        val l  = renderedFakePlayers.findOrCreate(p.uniqueId, mutableSetOf())
        if(!l.contains(id)) return
        PacketHandler.removePlayerEntity(p, id)
        l.remove(id)
    }
}