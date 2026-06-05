package dev.edgellm.data.chat.db

import dev.edgellm.domain.chat.ChatMessage
import dev.edgellm.domain.chat.ChatSession
import dev.edgellm.domain.chat.Role

/** Maps a Room session-with-messages into the domain [ChatSession]. */
fun SessionWithMessages.toDomain(): ChatSession = ChatSession(
    id = session.id,
    title = session.title,
    modelId = session.modelId,
    messages = messages
        .sortedBy { it.position }
        .map { ChatMessage(role = Role.valueOf(it.role), content = it.content) },
    createdAt = session.createdAt,
    updatedAt = session.updatedAt,
)
