package io.dallen.questengine

import kotlinx.serialization.*
import kotlinx.serialization.Optional
import kotlinx.serialization.json.JSON
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import java.io.File
import java.util.*
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels


object DataManager {

    @Serializable
    data class SavedSkinProp(val name: String, val value: String, val sig: String)

    @Serializable
    data class SavedSkin(val id: String, val props: List<SavedSkinProp>)

    @Serializable
    data class SimpleLocation(val x: Double, val y: Double, val z: Double, val pitch: Float = 0f, val yaw: Float = 0f) {
        fun toBukkit(world: World): Location = Location(world, x, y, z, yaw, pitch)

        companion object {
            fun fromSimpleBukkit(loc: Location) = SimpleLocation(loc.x, loc.y, loc.z)
            fun fromFullBukkit(loc: Location) = SimpleLocation(loc.x, loc.y, loc.z, loc.pitch, loc.yaw)
        }
    }

    val questDirectory: HashMap<Int, Quest> = HashMap()

    val npcsDirectory: HashMap<Int, NPC> = HashMap()

    val factionDirectory: HashMap<Int, Faction> = HashMap()

    val skinDirectory: HashMap<String, SavedSkin> = HashMap()

    val playerData: HashMap<UUID, PlayerData> = HashMap()

    fun buildFileSystem() {
        for(f in arrayOf("quests", "npcs", "playerdata", "skins", "factions")) {
            val dir = File(QuestEngine.instance!!.dataFolder.path + "/" + f)
            println("mk " + dir.path)
            if(!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    fun loadQuests() {
        val questDir = File(QuestEngine.instance!!.dataFolder.path + "/quests")
        questDir.list()
                .map { JSON.parse<Quest>(File(questDir.path + "/" + it).readText()) }
                .forEach { questDirectory[it.id] = it }
    }

    fun loadNPCs(world: World) {
        val npcDir = File(QuestEngine.instance!!.dataFolder.path + "/npcs")
        for(npcFile in npcDir.list()) {
            val loadedNPC = JSON.parse<NPC>(File(npcDir.path + "/" + npcFile).readText())
            loadedNPC.states.forEach { state ->
                val npc = PacketHandler.registerNPC(loadedNPC.name, state.location.toBukkit(world), loadedNPC.skinName,
                    { p -> loadedNPC.state(p.getData()) == state },
                    { e -> state.startConvo(e.player, loadedNPC) }
                )
                NPCManager.fakePlayerHandles[npc.id] = npc
            }

            npcsDirectory[loadedNPC.id] = loadedNPC
        }
    }

    fun loadFactions() {
        val factionDir = File(QuestEngine.instance!!.dataFolder.path + "/factions")
        factionDir.list()
                .map { JSON.parse<Faction>(File(factionDir.path + "/" + it).readText()) }
                .forEach { factionDirectory[it.id] = it }
    }

    fun getPlayerDataLocation(uuid: UUID) =
            File("${QuestEngine.instance!!.dataFolder.path}/playerdata/$uuid.json")

    fun loadPlayerData(uuid: UUID): PlayerData {
        val plrDataFile = getPlayerDataLocation(uuid)
        val plrData = if(plrDataFile.exists()) JSON.parse<RawPlayerData>(plrDataFile.readText()).toActual()
                  else PlayerData(uuid.toString(), true)
        playerData[uuid] = plrData
        return plrData
    }

    fun savePlayerData(uuid: UUID) {
        val plrDataFile = getPlayerDataLocation(uuid)
        plrDataFile.writeText(JSON.stringify(playerData[uuid]!!.toRaw()))
    }

    fun loadAvailableSkins() {
        val skinDir = File(QuestEngine.instance!!.dataFolder.path + "/skins")
        for(skinFile in skinDir.list()) {
            val loadedSkin = JSON.parse<SavedSkin>(File(skinDir.path + "/" + skinFile).readText())
            skinDirectory[loadedSkin.id] = loadedSkin
        }
    }

    fun acquireSkin(user: String, name: String) {
        val dbSkin = PacketHandler.stealSkin(user)
        val dbSavedSkin = dbSkin.map { s -> SavedSkinProp(s.name, s.value, s.signature) }
        val dbSavedSkinStr = JSON.indented.stringify(SavedSkin(name, dbSavedSkin))
        File("${QuestEngine.instance!!.dataFolder.path}/skins/$name.json").writeText(dbSavedSkinStr)
    }

    fun saveDirtyPlayers() {
        playerData.filter { e -> e.value.dirty }.forEach { uuid, _ -> savePlayerData(uuid) }
    }

    fun loadFile(url: String, savePath: String): Boolean {
        if(savePath.contains("..")) {
            println("Player attempted to download file above quest dir!!")
            return false
        }
        val rbc = Channels.newChannel(URL(url).openStream())
        val fos = FileOutputStream(QuestEngine.instance!!.dataFolder.path + "/" + savePath)
        fos.channel.transferFrom(rbc, 0, java.lang.Long.MAX_VALUE)
        return true
    }
}