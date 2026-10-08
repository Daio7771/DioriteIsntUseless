package daio7771.dioriteisntuseless.abuse.signal

import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import daio7771.dioriteisntuseless.abuse.morse.MorseBeeper
import daio7771.dioriteisntuseless.abuse.morse.MorsePhrases
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Mensaje en Morse en el chat, sin remitente y en gris, solo para ese jugador. En el nivel 1 es
 * solo texto (que parezca un fallo del chat); desde el nivel 2 lo acompañan pitidos suaves.
 * Nunca repite la frase anterior si hay otra.
 */
object MorseSignal : AbuseSignal {

    private const val BEEPS_FROM_LEVEL = 2

    override val id = "morse"
    override val minLevel = 1

    override fun canRun(player: ServerPlayer, state: PlayerAbuse): Boolean = MorsePhrases.phrases.isNotEmpty()

    override fun run(player: ServerPlayer, state: PlayerAbuse): Boolean {
        val phrases = MorsePhrases.phrases
        val options = phrases.filter { it.text != state.lastMorsePhrase }.ifEmpty { phrases }
        val phrase = options[player.random.nextInt(options.size)]
        state.lastMorsePhrase = phrase.text
        // Mensaje de sistema: sin "<nombre>" delante, y no queda en el log del servidor.
        player.sendSystemMessage(Component.literal(phrase.code).withStyle(ChatFormatting.GRAY))
        if (state.level >= BEEPS_FROM_LEVEL) MorseBeeper.start(player.server, player, phrase.code)
        return true
    }
}
