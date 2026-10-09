package daio7771.dioriteisntuseless.abuse.text

/**
 * Words of the level 3 signs, in data/dioriteisntuseless/abuse/sign_words.json:
 *
 * ```
 * { "replace": false, "words": ["stop", "..."] }
 * ```
 *
 * Datapacks can add more (see TextListResource). They must fit on one sign line.
 */
object SignWords {

    private val resource = TextListResource("abuse/sign_words.json", "words") { words = it }

    @Volatile
    var words: List<String> = emptyList()
        private set

    fun init() = resource.register()
}
