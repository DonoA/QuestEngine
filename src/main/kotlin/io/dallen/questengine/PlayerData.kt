package io.dallen.questengine

import kotlinx.serialization.Optional
import kotlinx.serialization.Serializable
import org.bukkit.Bukkit
import java.util.*

class PlayerData(val uuid: String, questing: Boolean, activeQuest: Int = -1,
                 val completedObjectives: MutableList<Int> = mutableListOf(),
                 val completedQuests: MutableList<Int> = mutableListOf(),
                 faction: Int = -1, factionRep: Int = 0, lastLocation: DataManager.SimpleLocation? = null,
                 dirty: Boolean = false) {

    var factionRep = factionRep
        set(value) {
            field = value
            dirty = true
        }

    var faction = faction
        set(value) {
            field = value
            dirty = true
        }

    var questing = questing
        set(value) {
            field = value
            dirty = true
        }

    var activeQuest = activeQuest
        set(value) {
            field = value
            dirty = true
        }

    var lastLocation = lastLocation
        set(value) {
            field = value
            dirty = true
        }

    var dirty = dirty

    fun realFaction() = DataManager.factionDirectory[faction]

    fun activeQuestObject() = DataManager.questDirectory[activeQuest]

    fun getPlayer() = Bukkit.getPlayer(UUID.fromString(uuid))!!

    fun toRaw() = RawPlayerData(uuid, questing, activeQuest, completedObjectives, completedQuests, faction, factionRep,
            lastLocation)
}

@Serializable
data class RawPlayerData(val uuid: String, val questing: Boolean, val activeQuest: Int = -1,
                         val completedObjectives: MutableList<Int> = mutableListOf(),
                         val completedQuests: MutableList<Int> = mutableListOf(),
                         @Optional val faction: Int = -1, @Optional val factionRep: Int = 0,
                         val lastLocation: DataManager.SimpleLocation? = null) {
    fun toActual() = PlayerData(uuid, questing, activeQuest, completedObjectives, completedQuests, faction, factionRep,
            lastLocation)
}