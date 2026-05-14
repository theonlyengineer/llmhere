package dev.edgellm.domain.chat

enum class Role { System, User, Assistant }

data class ChatMessage(val role: Role, val content: String)
