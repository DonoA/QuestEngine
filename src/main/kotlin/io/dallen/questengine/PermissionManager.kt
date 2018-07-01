package io.dallen.questengine

import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault



object PermissionManager {

    val staffPermission = Permission("questengine.staff", PermissionDefault.OP)

    val adminPermission = Permission("questengine.admin", PermissionDefault.OP)

    val questingPermission = Permission("questengine.questing", PermissionDefault.FALSE)

    // Create a list of all the perms to remove from questing players (Nodes of the perms they have with the negative flag)

    // Create functions that will allow the perms to be set/unset quickly

    // Ref: https://github.com/lucko/LuckPerms/wiki/Developer-API:-Usage#unsettransientpermissionnode
}