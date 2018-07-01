package io.dallen.questengine

import net.md_5.bungee.api.ChatColor
import org.bukkit.entity.Player
import java.util.*
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.event.Listener

object ChatMenuController {
    val openMenus: HashMap<UUID, ChatMenu> = HashMap()

    fun sendMenu(player: Player, menu: ChatMenu) {
        val menuId = UUID.randomUUID()
        val baseMessage = TextComponent("")
        for((i, op) in menu.options.withIndex()) {
            val option = TextComponent(op.name)
            option.color = op.color
            option.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/quests bknd ${menuId} $i")
            baseMessage.addExtra("[")
            baseMessage.addExtra(option)
            baseMessage.addExtra("]")
        }
        player.spigot().sendMessage(baseMessage)
        openMenus[menuId] = menu
    }

    fun handleClick(uuid: UUID, id: Int) {
        val menu = openMenus.remove(uuid)
        menu?.handler?.invoke(id, menu?.options!![id])
    }

    data class ChatMenuOption(val name: String, val color: ChatColor)

    data class ChatMenu(val options: List<ChatMenuOption>, val handler: (Int, ChatMenuOption) -> Unit)
}
