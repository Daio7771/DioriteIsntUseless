package daio7771.dioriteisntuseless.abuse.signal

import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import daio7771.dioriteisntuseless.abuse.morse.MorseBeeper
import daio7771.dioriteisntuseless.abuse.morse.MorsePhrases
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * A Morse message in chat, without a sender and in gray, only for that player. At level 1 it is
 * just text (so it looks like a chat glitch); from level 2 on, soft beeps come with it.
 * Never repeats the previous phrase if there is another one.
 */
object MorseSignal : AbuseSignal {

    private const val BEEPS_FROM_LEVEL = 2

    override val id = "morse"
    override val minLevel = 1

    override fun canRun(player: ServerPlayer, state: PlayerAbuse): Boolean = MorsePhrases.phrases.isNotEmpty()

    override fun run(player: ServerPlayer, state: PlayerAbuse): Boolean {
        val phrases = MorsePhrases.phrases
        val options = phrases.filter { it.text != state.lastMorsePhrase }.ifEmpty { phrases }
        send(player, state, options[player.random.nextInt(options.size)])
        return true
    }

    /** Sends [phrase] (with beeps from level 2 on). Also used by the ending. */
    fun send(player: ServerPlayer, state: PlayerAbuse, phrase: MorsePhrases.Phrase) {
        state.lastMorsePhrase = phrase.text
        // System message: no "<name>" in front, and it does not end up in the server log.
        player.sendSystemMessage(Component.literal(phrase.code).withStyle(ChatFormatting.GRAY))
        if (state.level >= BEEPS_FROM_LEVEL) MorseBeeper.start(player.server, player, phrase.code)
    }
}
