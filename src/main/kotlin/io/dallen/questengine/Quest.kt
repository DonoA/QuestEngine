package io.dallen.questengine

import kotlinx.serialization.Optional
import kotlinx.serialization.Serializable
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World

@Serializable
data class QuestPreReq(val id: Int, @Optional val timeout: Int = 0, @Optional val withError: String = "")

@Serializable
data class QuestObjectiveParameters(@Optional val location: DataManager.SimpleLocation? = null,
                                    @Optional val visibleBefore: Boolean = true)

@Serializable
data class QuestObjective(val id: Int, val type: String, val info: String, val parameters: QuestObjectiveParameters,
                          val preReq: List<QuestPreReq> = emptyList()) {
    fun createMessage(): String {
        return "$type, $info"
    }

    fun hasCompletedPrereq(pd: PlayerData): Boolean {
        var completedObj = false
        preReq.forEach {
            if(!pd.completedObjectives.contains(it.id)) completedObj = false
        }
        return completedObj
    }
}

@Serializable
data class Quest(val id: Int, val title: String, val objectives: List<QuestObjective>) {
    fun findInteractObjective(loc: Location): QuestObjective? {
        return objectives.firstOrNull {
            obj -> obj.type == "interact" && obj.parameters.location!!.toBukkit(loc.world) == loc
        }
    }

    fun getFakeStates(world: World): List<Pair<Location, Material>> = objectives.filter {
        obj -> obj.parameters.location != null && !obj.parameters.visibleBefore
    }.map {
        obj -> Pair(obj.parameters.location!!.toBukkit(world), Material.AIR)
    }
}