# QuestEngine
A simple yet robust quest engine for spigot written in Kotlin

Right now there are:
 - In house built NPCs that only appear/interact with the world when the player is questing.
 - NPCs do not appear in the tab list (except for a split second when loading, however this cannot be avoided)
 - NPCs can take any saved skin file I assign to them.
 - New skin files can be generated and saved off any minecraft account from an in game command.
 - NPCs can be configured with dialogue flows that can assign and complete quests when needed (however this is not required).
 - NPCs can change location and dialogue based on the current state of a players save file (their active quest, completed objectives, completed quests, etc)
 - Quest can be composed of many objectives, each objective can either require talking with an NPC or interacting with a location (more objective types to come)
 - Quests can be paused at any time, when resumed the player is teleported back to where they left off.

In future I would like to add:
 - Permissions to the players so when questing is active they cannot fly, warp, etc
 - Block spoofing so that interact objectives are not completable immediately.
 - An interface for people to easily add and update quests (or at least propose them).
 
The last point is the one I am most interested in. I have had several ideas on how to do this, however the best solution seems to be either in game commands (which would need to be very complicated in order to allow the customization required) or some sort of web interface through with people can create and update the quests (more work for me, however likely easier for everyone else). If you think there are better ways, please let me know. My target demographic for adding quests would be guides so I would need the interface to be relatively easy to understand and use.

NPC talking is currently run through a chat menu interface that is pretty easy to understand so in game commands could be made slightly easier that way, however they would still be rather complex.

All the backend data is encoded in JSON so it is pretty easy for developers to understand if anyone wants to take a look. Some sample configs can be found at:
http://tunnel.dallen.io/npcs/Bilbo.json
http://tunnel.dallen.io/quests/BilboCake.json
http://tunnel.dallen.io/skins/Bilbo.json