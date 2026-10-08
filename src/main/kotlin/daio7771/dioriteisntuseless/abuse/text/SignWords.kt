package daio7771.dioriteisntuseless.abuse.text

/**
 * Palabras de los carteles del nivel 3, en data/dioriteisntuseless/abuse/sign_words.json:
 *
 * ```
 * { "replace": false, "words": ["stop", "..."] }
 * ```
 *
 * Ampliables con datapacks (ver TextListResource). Deben caber en una línea de cartel.
 */
object SignWords {

    private val resource = TextListResource("abuse/sign_words.json", "words") { words = it }

    @Volatile
    var words: List<String> = emptyList()
        private set

    fun init() = resource.register()
}
