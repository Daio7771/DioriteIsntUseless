package daio7771.dioriteisntuseless.abuse.morse

import java.util.Locale

/**
 * International Morse code. Letters are separated by a space and words by " / "
 * ("DELETE THIS MOD" -> "-.. . .-.. . - . / - .... .. ... / -- --- -..").
 * Everything is translated, punctuation too (the comma is --..--).
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

    /** Typographic variants that are written with the character from the table. */
    private val ALIASES: Map<Char, Char> = mapOf('’' to '\'', '‘' to '\'', '“' to '"', '”' to '"')

    /** Result of [encode]: the Morse code and the characters that do not exist in Morse (left out). */
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
