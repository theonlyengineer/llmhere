package dev.edgellm.engine

data class Token(
    val text: String,
    val logprob: Float? = null,
    val isFinal: Boolean = false,
)
