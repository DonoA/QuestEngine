package io.dallen.questengine

import net.md_5.bungee.api.ChatColor
import org.bukkit.entity.Player
import java.util.*
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.TextComponent

object ChatMenuController {
    val openMenus: HashMap<UUID, ChatMenu> = HashMap()

    fun sendMenu(player: Player, menu: ChatMenu) {
        val baseMessage = TextComponent("")
        for((i, op) in menu.options.withIndex()) {
            val option = TextComponent(op.name)
            option.color = op.color
            option.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/quests bknd ${player.uniqueId} $i")
            baseMessage.addExtra("[")
            baseMessage.addExtra(option)
            baseMessage.addExtra("]")
        }
        player.spigot().sendMessage(baseMessage)
        openMenus[player.uniqueId] = menu
    }

    fun handleClick(uuid: UUID, id: Int) {
        openMenus[uuid]?.handler?.invoke(id, openMenus[uuid]?.options!![id])
        openMenus.remove(uuid)
    }

    data class ChatMenuOption(val name: String, val color: ChatColor)

    data class ChatMenu(val options: List<ChatMenuOption>, val handler: (Int, ChatMenuOption) -> Unit)
}
