package dev.edgellm

import android.app.Application
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

        dependencies = AppDependencies(
            engine = engine,
            modelsDir = modelsDir,
        )

        val catalogJson = assets.open("catalog.json").bufferedReader().readText()
        dependencies.loadCatalog(catalogJson)
    }
}
