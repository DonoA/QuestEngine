package io.dallen.questengine

import org.bukkit.entity.Player
import org.bukkit.entity.Entity
import org.bukkit.Location

import java.util.*
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter
import org.bukkit.event.player.PlayerInteractEntityEvent
import java.util.UUID
import org.bukkit.Bukkit

// imports needing updates follow: !!
import com.mojang.authlib.GameProfile
import net.minecraft.server.v1_12_R1.*
import org.bukkit.craftbukkit.v1_12_R1.CraftServer
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer


object PacketHandler {

    // Despite the disgusting mix of NMS, OBC, and protocol lib seen in this file, I think this is the easiest way
    // to accomplish what we want. NMS and OBC would require channel injection to sample packets while Protocol lib
    // requires extensive byte encoding to create the needed Entity watcher object that CBS creates for us

    data class EntityInteractEvent(val entityId: Int, val hand: Entity)

    data class FakePlayerEntity(val location: Location, val name: String, val uuid: UUID, val id: Int,
                                val handle: EntityPlayer, val interactEvent: (PlayerInteractEntityEvent) -> Unit)

    val fakePlayerHandles: HashMap<Int, FakePlayerEntity> = HashMap()

    val fakePlayerInteractCooldown: HashMap<UUID, Long> = HashMap()

    val renderedFakePlayers: HashMap<UUID, MutableSet<Int>> = HashMap()

    fun registerNPC(name: String, location: Location, handler: (PlayerInteractEntityEvent) -> Unit): Int {
        val nmsServer = (Bukkit.getServer() as CraftServer).server
        val nmsWorld = (Bukkit.getWorlds()[0] as CraftWorld).handle
        val uuid = UUID.randomUUID()
        val npc = EntityPlayer(nmsServer, nmsWorld, GameProfile(uuid, name), PlayerInteractManager(nmsWorld))
        npc.setLocation(location.x, location.y, location.z, location.yaw, location.pitch)
        fakePlayerHandles[npc.id] = FakePlayerEntity(location, name, uuid, npc.id, npc, handler)
        return npc.id
    }

    fun scanVisibleEntites(p: Player, loc: Location) {
        // TODO: this is quite poorly optimized and should fixed at some point
        PacketHandler.fakePlayerHandles.forEach { id, npc ->
            // View distance for npcs is 45, could be changed if needed
            if(npc.location.distance(loc) < 45.0) {
                PacketHandler.tryRenderPlayerEntity(p, id)
            } else {
                PacketHandler.tryRemoveRenderedPlayerEntity(p, id)
            }
        }
    }

    fun removeAllEntites(p: Player) {
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
        val npc = fakePlayerHandles[id]?.handle ?: return

        connection.sendPacket(PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, npc))
        connection.sendPacket(PacketPlayOutNamedEntitySpawn(npc))
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
                val target: Entity = PacketHandler.fakePlayerHandles[id]!!.handle.bukkitEntity

                val ev = PlayerInteractEntityEvent(event.player, target)
                fakePlayerHandles[id]!!.interactEvent.invoke(ev)

            }
        }

        QuestEngine.protocolManager!!.addPacketListener(playerInteractAdapter)
    }
}