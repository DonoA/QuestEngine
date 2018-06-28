package io.dallen.questengine

import com.mojang.authlib.GameProfile
import org.bukkit.Bukkit
import org.bukkit.entity.Player

import org.bukkit.craftbukkit.v1_12_R1.CraftServer
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer

import net.minecraft.server.v1_12_R1.*
import org.bukkit.Location

import java.util.*
import com.comphenix.protocol.wrappers.WrappedGameProfile
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter



object CraftBukkitHandler {

    val playerHandles: HashMap<UUID, EntityPlayer> = HashMap()

    fun registerNPC(name: String, location: Location): UUID {
        val nmsServer = (Bukkit.getServer() as CraftServer).server
        val nmsWorld = (Bukkit.getWorlds()[0] as CraftWorld).handle
        val uuid = UUID.randomUUID()
        val npc = EntityPlayer(nmsServer, nmsWorld, GameProfile(uuid, name), PlayerInteractManager(nmsWorld))
        npc.setLocation(location.x, location.y, location.z, location.yaw, location.pitch)
        playerHandles[uuid] = npc
        return uuid
    }

    fun spawnPlayerEntity(p: Player, uuid: UUID) {
        val connection = (p as CraftPlayer).handle.playerConnection
        val npc = playerHandles[uuid] ?: return

        connection.sendPacket(PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, npc))
        connection.sendPacket(PacketPlayOutNamedEntitySpawn(npc))
    }

    fun removePlayerEntity(p: Player, uuid: UUID) {
        val connection = (p as CraftPlayer).handle.playerConnection
        val npc = playerHandles[uuid] ?: return

        connection.sendPacket(PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, npc))
        connection.sendPacket(PacketPlayOutEntityDestroy(npc.id))
    }

    data class InteractPacket(val entityId: Int, val hand: String)

    fun decodeInteractPacket(event: PacketEvent): InteractPacket {
        val useEntityPacket = event!!.packet.handle as PacketPlayInUseEntity
        val getIdField = useEntityPacket.javaClass.declaredFields.first { f -> f.type == Integer.TYPE }
        getIdField.trySetAccessible()
        val getHandField = useEntityPacket.javaClass.declaredFields.first { f -> f.type == EnumHand::class.java }
        getHandField.trySetAccessible()
        return InteractPacket(getIdField.getInt(useEntityPacket), (getHandField.get(useEntityPacket) as EnumHand).name)
    }
}