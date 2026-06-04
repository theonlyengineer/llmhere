package dev.edgellm.engine

import java.io.File

data class ModelHandle(
    val file: File,
    val family: String,
    val quantization: String,
    val contextLength: Int,
)
