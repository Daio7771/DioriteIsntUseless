package daio7771.dioriteisntuseless.abuse.morse

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.abuse.text.TextListResource

/**
 * Phrases of the Morse messages, in data/dioriteisntuseless/abuse/morse_phrases.json:
 *
 * ```
 * { "replace": false, "phrases": ["DELETE THIS MOD", "..."] }
 * ```
 *
 * Datapacks can add more (see TextListResource). They are translated to Morse when loaded.
 */
object MorsePhrases {

    class Phrase(val text: String, val code: String)

    private val resource = TextListResource("abuse/morse_phrases.json", "phrases") { texts ->
        phrases = texts.mapNotNull(::encode)
    }

    /** Phrases already translated to Morse. Replaced as a whole on reload. */
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
