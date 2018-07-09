package io.dallen.questengine

import org.bukkit.entity.Player
import org.bukkit.Location

import java.util.*
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.wrappers.*
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
    // requires extensive byte encoding to create the needed Entity watcher object that NMS creates for us

    data class FakePlayerEntity(val location: Location, val name: String, val uuid: UUID, val id: Int,
                                val handle: EntityPlayer, val profile: WrappedGameProfile, val visible: (Player) -> Boolean,
                                val interactEvent: (PlayerInteractEntityEvent) -> Unit)

    val fakePlayerInteractCooldown: HashMap<UUID, Long> = HashMap()


    fun registerNPC(name: String, location: Location, skinId: String?, visible: (Player) -> Boolean,
                    handler: (PlayerInteractEntityEvent) -> Unit): FakePlayerEntity {
        val cbServer = (Bukkit.getServer() as CraftServer).server
        val cbWorld = (QuestEngine.setting!! as CraftWorld).handle
        val uuid = UUID.randomUUID()
        val profile = WrappedGameProfile(uuid, name)

        if(skinId != null) {
            val skinProps = DataManager.skinDirectory[skinId]!!.props.map { skin -> WrappedSignedProperty(skin.name,
                    skin.value, skin.sig) }
            profile.properties.removeAll("textures")
            profile.properties.putAll("textures", skinProps)
        }

        val npc = EntityPlayer(cbServer, cbWorld, profile.handle as GameProfile, PlayerInteractManager(cbWorld))
        npc.bukkitEntity.setAI(false)
        npc.bukkitEntity.isCustomNameVisible = false
        npc.setPositionRotation(location.x, location.y, location.z, location.yaw, location.pitch)
        val id = npc.id

        return FakePlayerEntity(location, name, uuid, id, npc, profile, visible, handler)
    }


    fun spawnPlayerEntity(p: Player, id: Int) {
        val connection = (p as CraftPlayer).handle.playerConnection
        val fakePlayer = NPCManager.fakePlayerHandles[id] ?: return
        val npc = fakePlayer.handle

        connection.sendPacket(PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, npc))
        connection.sendPacket(PacketPlayOutNamedEntitySpawn(npc))
        PacketHandler.sendFakeHeadRotation(p, id, fakePlayer.location.yaw)
        Bukkit.getScheduler().runTaskLater(QuestEngine.instance, {
            connection.sendPacket(PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, npc))
        }, 5)
    }

    fun removePlayerEntity(p: Player, id: Int) {
        val connection = (p as CraftPlayer).handle.playerConnection
        val npc = NPCManager.fakePlayerHandles[id]?.handle ?: return

        connection.sendPacket(PacketPlayOutEntityDestroy(npc.id))
    }

    fun sendFakePlayerInfoAdd(p: Player, fakePlayer: FakePlayerEntity) {
        val packet = PacketContainer(PacketType.Play.Server.PLAYER_INFO)
        packet.playerInfoAction.write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER)
        packet.playerInfoDataLists.write(0, listOf(PlayerInfoData(fakePlayer.profile, 0,
                EnumWrappers.NativeGameMode.CREATIVE, WrappedChatComponent.fromText(fakePlayer.name))))
        QuestEngine.protocolManager!!.sendServerPacket(p, packet)
    }

    fun sendFakePlayerSpawn(p: Player, fakePlayer: FakePlayerEntity) {
        val packet = PacketContainer(PacketType.Play.Server.NAMED_ENTITY_SPAWN)
        packet.integers.write(0, fakePlayer.id)
        packet.uuiDs.write(0, fakePlayer.uuid)
        packet.doubles.write(0, fakePlayer.location.x)
        packet.doubles.write(1, fakePlayer.location.y)
        packet.doubles.write(2, fakePlayer.location.z)
        packet.bytes.write(0, (fakePlayer.location.yaw * 256.0F / 360.0F).toByte())
        packet.bytes.write(0, (fakePlayer.location.pitch * 256.0F / 360.0F).toByte())
        packet.dataWatcherModifier.write(0, WrappedDataWatcher(fakePlayer.handle.dataWatcher))
        QuestEngine.protocolManager!!.sendServerPacket(p, packet)
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

                NPCManager.fakePlayerHandles[id]?.let {
                    val ev = PlayerInteractEntityEvent(event.player, null)
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