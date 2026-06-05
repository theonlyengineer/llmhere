package dev.edgellm

import android.app.Application
import androidx.room.Room
import dev.edgellm.data.chat.RoomChatRepository
import dev.edgellm.data.chat.db.EdgeLlmDatabase
import dev.edgellm.data.settings.DataStoreGenerationSettingsRepository
import dev.edgellm.engine.llamacpp.LlamaCppEngine
import dev.edgellm.engine.llamacpp.NativeBindingsImpl
import kotlinx.coroutines.Dispatchers

class EdgeLlmApplication : Application() {

    lateinit var dependencies: AppDependencies
        private set

    override fun onCreate() {
        super.onCreate()
        val engine = LlamaCppEngine(
            bindings = NativeBindingsImpl(),
            inferenceDispatcher = Dispatchers.IO,
        )
        val modelsDir = filesDir.resolve("models").absolutePath

        val database = Room.databaseBuilder(
            this,
            EdgeLlmDatabase::class.java,
            "edgellm.db",
        ).build()

        dependencies = AppDependencies(
            engine = engine,
            modelsDir = modelsDir,
            settingsRepository = DataStoreGenerationSettingsRepository(this),
            chatRepository = RoomChatRepository(database.chatDao()),
        )

        val catalogJson = assets.open("catalog.json").bufferedReader().readText()
        dependencies.loadCatalog(catalogJson)
    }
}
