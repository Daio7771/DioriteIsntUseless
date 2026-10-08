package daio7771.dioriteisntuseless.abuse.morse

import java.util.Locale

/**
 * Código Morse internacional. Las letras van separadas por un espacio y las palabras por " / "
 * ("DELETE THIS MOD" -> "-.. . .-.. . - . / - .... .. ... / -- --- -..").
 * Se traduce todo, también la puntuación (la coma es --..--).
 */
object MorseCode {

    private val TABLE: Map<Char, String> = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
        'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
        'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
        'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '\'' to ".----.", '!' to "-.-.--",
        '/' to "-..-.", '(' to "-.--.", ')' to "-.--.-", '&' to ".-...", ':' to "---...",
        ';' to "-.-.-.", '=' to "-...-", '+' to ".-.-.", '-' to "-....-", '_' to "..--.-",
        '"' to ".-..-.", '$' to "...-..-", '@' to ".--.-.",
    )

    /** Variantes tipográficas que se escriben con el carácter de la tabla. */
    private val ALIASES: Map<Char, Char> = mapOf('’' to '\'', '‘' to '\'', '“' to '"', '”' to '"')

    /** Resultado de [encode]: el Morse y los caracteres que no existen en Morse (se omiten). */
    class Encoded(val code: String, val unsupported: Set<Char>)

    fun encode(text: String): Encoded {
        val unsupported = LinkedHashSet<Char>()
        val words = text.uppercase(Locale.ROOT).split(' ', '\t', '\n').mapNotNull { word ->
            val letters = word.mapNotNull { char ->
                TABLE[ALIASES[char] ?: char].also { if (it == null) unsupported += char }
            }
            letters.takeIf { it.isNotEmpty() }?.joinToString(" ")
        }
        return Encoded(words.joinToString(" / "), unsupported)
    }
}
