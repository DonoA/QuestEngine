package io.dallen.questengine

import org.bukkit.entity.Player

fun <K, V> HashMap<K, V>.findOrCreate(key: K, value: V): V {
    val v = this[key]
    if(v == null) {
        this[key] = value
        return value
    }
    return v
}

fun Player.getData(): DataManager.PlayerData {
    var plrd = DataManager.playerData[this.uniqueId]
    if(plrd == null) {
        plrd = DataManager.loadPlayerData(this.uniqueId)
    }
    return plrd
}

fun Player.isQuester(): Boolean {
    if(DataManager.playerData[this.uniqueId] != null && DataManager.playerData[this.uniqueId]!!.questing) return true
    if(DataManager.getPlayerDataLocation(this.uniqueId).exists() && this.getData().questing) return true
    return false
}