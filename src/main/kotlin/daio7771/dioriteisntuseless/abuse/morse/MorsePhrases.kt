package daio7771.dioriteisntuseless.abuse.morse

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.abuse.text.TextListResource

/**
 * Frases de los mensajes en Morse, en data/dioriteisntuseless/abuse/morse_phrases.json:
 *
 * ```
 * { "replace": false, "phrases": ["DELETE THIS MOD", "..."] }
 * ```
 *
 * Ampliables con datapacks (ver TextListResource). Se traducen a Morse al cargarlas.
 */
object MorsePhrases {

    class Phrase(val text: String, val code: String)

    private val resource = TextListResource("abuse/morse_phrases.json", "phrases") { texts ->
        phrases = texts.mapNotNull(::encode)
    }

    /** Frases ya traducidas a Morse. Se sustituye entera al recargar. */
    @Volatile
    var phrases: List<Phrase> = emptyList()
        private set

    fun init() = resource.register()

    private fun encode(text: String): Phrase? {
        val encoded = MorseCode.encode(text)
        if (encoded.unsupported.isNotEmpty()) {
            LOGGER.warn("{}: \"{}\" has characters that do not exist in Morse code ({}); they are left out.",
                resource, text, encoded.unsupported.joinToString(" "))
        }
        if (encoded.code.isEmpty()) return null
        return Phrase(text, encoded.code)
    }
}
