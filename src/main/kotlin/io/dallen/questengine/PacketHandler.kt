package io.dallen.questengine

import org.bukkit.entity.Player
import org.bukkit.entity.Entity
import org.bukkit.Location

import java.util.*
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import com.comphenix.protocol.wrappers.WrappedGameProfile
import com.comphenix.protocol.wrappers.WrappedSignedProperty
import org.bukkit.event.player.PlayerInteractEntityEvent
import java.util.UUID
import org.bukkit.Bukkit

// imports needing updates follow: !!
import com.mojang.authlib.GameProfile
import com.mojang.authlib.minecraft.MinecraftSessionService
import net.minecraft.server.v1_12_R1.*
import org.bukkit.craftbukkit.v1_12_R1.CraftServer
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer


object PacketHandler {

    // Despite the disgusting mix of NMS, OBC, and protocol lib seen in this file, I think this is the easiest way
    // to accomplish what we want. NMS and OBC would require channel injection to sample packets while Protocol lib
    // requires extensive byte encoding to create the needed Entity watcher object that CBS creates for us

    data class FakePlayerEntity(val location: Location, val name: String, val uuid: UUID, val id: Int,
                                val handle: EntityPlayer, val visible: (Player) -> Boolean,
                                val interactEvent: (PlayerInteractEntityEvent) -> Unit)

    val fakePlayerHandles: HashMap<Int, FakePlayerEntity> = HashMap()

    val fakePlayerInteractCooldown: HashMap<UUID, Long> = HashMap()

    val renderedFakePlayers: HashMap<UUID, MutableSet<Int>> = HashMap()

    val fakeBlockStates: HashMap<UUID, List<Pair<Location, org.bukkit.Material>>> = HashMap()

    fun registerFakeState(uuid: UUID, data: List<Pair<Location, org.bukkit.Material>>) {
        fakeBlockStates[uuid] = data
    }

    fun removeFakeStates(uuid: UUID) {
        fakeBlockStates.remove(uuid)
    }

    fun registerNPC(name: String, location: Location, skinId: String?, visible: (Player) -> Boolean, handler: (PlayerInteractEntityEvent) -> Unit): Int {
        val cbServer = (Bukkit.getServer() as CraftServer).server
        val cbWorld = (QuestEngine.setting!! as CraftWorld).handle
        val uuid = UUID.randomUUID()
        val profile = WrappedGameProfile(uuid, name)

        if(skinId != null) {
            val skinProps = DataManager.skinDirectory[skinId]!!.props.map { skin -> WrappedSignedProperty(skin.name, skin.value, skin.sig) }
            profile.properties.removeAll("textures")
            profile.properties.putAll("textures", skinProps)
        }

        val npc = EntityPlayer(cbServer, cbWorld, profile.handle as GameProfile, PlayerInteractManager(cbWorld))
        npc.bukkitEntity.setAI(false)
        npc.setPositionRotation(location.x, location.y, location.z, location.yaw, location.pitch)
        fakePlayerHandles[npc.id] = FakePlayerEntity(location, name, uuid, npc.id, npc, visible, handler)
        return npc.id
    }

    fun scanVisibleEntities(p: Player, loc: Location) {
        // TODO: this is quite poorly optimized and should fixed at some point
        PacketHandler.fakePlayerHandles.forEach { id, npc ->
            // View distance for npcs is 45, could be changed if needed
            if(npc.visible.invoke(p) && npc.location.distance(loc) < 45.0) {
                PacketHandler.tryRenderPlayerEntity(p, id)
            } else {
                PacketHandler.tryRemoveRenderedPlayerEntity(p, id)
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
        spawnPlayerEntity(p, id)
        l.add(id)
    }

    fun spawnPlayerEntity(p: Player, id: Int) {
        val connection = (p as CraftPlayer).handle.playerConnection
        val fakePlayer = fakePlayerHandles[id] ?: return
        val npc = fakePlayer.handle

        connection.sendPacket(PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, npc))
        connection.sendPacket(PacketPlayOutNamedEntitySpawn(npc))
        PacketHandler.sendFakeHeadRotation(p, id, fakePlayer.location.yaw)

//        connection.sendPacket(PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, npc))
    }

    fun tryRemoveRenderedPlayerEntity(p: Player, id: Int) {
        val l  = renderedFakePlayers.findOrCreate(p.uniqueId, mutableSetOf())
        if(!l.contains(id)) return
        removePlayerEntity(p, id)
        l.remove(id)
    }

    fun removePlayerEntity(p: Player, id: Int) {
        val connection = (p as CraftPlayer).handle.playerConnection
        val npc = fakePlayerHandles[id]?.handle ?: return

        connection.sendPacket(PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, npc))
        connection.sendPacket(PacketPlayOutEntityDestroy(npc.id))
    }

    fun sendFakePlayerRelLook(player: Player, id: Int, pitch: Float, yaw: Float) {
        val packet = PacketContainer(PacketType.Play.Server.REL_ENTITY_MOVE_LOOK)
        packet.integers.write(0, id) // Entity id
        packet.integers.write(1, 0) // Dx
        packet.integers.write(2, 0) // Dy
        packet.integers.write(3, 0) // Dz
        packet.bytes.write(0, (yaw * 256.0F / 360.0F).toByte()) // Yaw
        packet.bytes.write(1, (pitch * 256.0F / 360.0F).toByte()) // Pitch
        packet.booleans.write(0, true)
        QuestEngine.protocolManager!!.sendServerPacket(player, packet)
    }

    fun sendFakeHeadRotation(p: Player, id: Int, yaw: Float) {
        val packet = PacketContainer(PacketType.Play.Server.ENTITY_HEAD_ROTATION)
        packet.integers.write(0, id) // Entity id
        packet.bytes.write(0, (yaw * 256.0F / 360.0F).toByte()) // Yaw
        QuestEngine.protocolManager!!.sendServerPacket(p, packet)
    }

    fun registerAll() {
        val playerInteractAdapter = object : PacketAdapter(QuestEngine.instance, PacketType.Play.Client.USE_ENTITY) {
            override fun onPacketReceiving(event: PacketEvent?) {
                val lastHit = fakePlayerInteractCooldown[event!!.player.uniqueId]
                when {
                    lastHit == null -> {
                        fakePlayerInteractCooldown[event.player.uniqueId] = System.currentTimeMillis()
                    }
                    lastHit + 750 > System.currentTimeMillis() -> return
                    else -> {
                        fakePlayerInteractCooldown[event.player.uniqueId] = System.currentTimeMillis()
                    }
                }
                val container = event.packet as PacketContainer
                val id = container.integers.read(0)

                PacketHandler.fakePlayerHandles[id]?.let {
                    val ev = PlayerInteractEntityEvent(event.player, it.handle.bukkitEntity)
                    it.interactEvent.invoke(ev)
                }
            }
        }

        QuestEngine.protocolManager!!.addPacketListener(playerInteractAdapter)
    }

    fun stealSkin(name: String): Array<WrappedSignedProperty> {
        var profile = WrappedGameProfile.fromOfflinePlayer(Bukkit.getOfflinePlayer(name))
        val cbServer = (Bukkit.getServer() as CraftServer).server
        val cbSessions: MinecraftSessionService = cbServer.javaClass.methods.first {
            m-> m.returnType.simpleName.equals("MinecraftSessionService", true)}
                .invoke(cbServer) as MinecraftSessionService
        val handle = profile.handle as GameProfile
        try {
            cbSessions.fillProfileProperties(handle, true)
        } catch(e: Exception) {
            println("Could not fill skin request!")
            e.printStackTrace()
        }
        profile = WrappedGameProfile.fromHandle(handle)
        return profile.properties.get("textures")!!.toTypedArray()
    }
}