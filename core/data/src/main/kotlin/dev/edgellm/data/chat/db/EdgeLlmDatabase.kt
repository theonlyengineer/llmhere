package dev.edgellm.data.chat.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class EdgeLlmDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
