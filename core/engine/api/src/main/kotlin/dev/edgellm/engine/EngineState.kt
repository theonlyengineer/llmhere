package dev.edgellm.engine

sealed interface EngineState {
    data object Idle : EngineState
    data object Loading : EngineState
    data object Ready : EngineState
    data object Generating : EngineState
    data class Error(val cause: Throwable) : EngineState
}
