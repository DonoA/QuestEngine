package io.dallen.questengine

import kotlinx.serialization.Serializable

@Serializable
data class FactionLeader(val title: String)

@Serializable
data class FactionRank(val repfloor: Int, val title: String)

@Serializable
data class Faction(val id: Int, val name: String, val ranks: List<FactionRank>, val leaders: List<FactionLeader>)
