package dev.edgellm.domain.templates

class ChatTemplateRegistry {

    private val templates: Map<String, ChatTemplate> = mapOf(
        "gemma" to GemmaTemplate(),
        "falcon-e" to FalconEdgeTemplate(),
    )

    fun templateFor(family: String): ChatTemplate? = templates[family]

    fun supportedFamilies(): Set<String> = templates.keys
}
