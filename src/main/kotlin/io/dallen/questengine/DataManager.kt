package io.dallen.questengine

import java.util.*

data class Quest(val id: Int, val name: String)

/*
{
    [
        name: "NPC 1",
        convoPoints: [
            id: 1, text: "Hello", options: [
                { text: "Hello, nextPoint: 1 }, { text: "Goodbye", nextPoint: -1 }
            ]
        ]
    ]
}
 */

data class ConversationPoint(val id: Int, val text: String, val options: HashMap<String, Int>)

data class NPC(val name: String, val rootConvoPoint: ConversationPoint)

object DataManager {
    
}