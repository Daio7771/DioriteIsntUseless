package daio7771.dioriteisntuseless.abuse.morse

import daio7771.dioriteisntuseless.registry.ModSounds
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import java.util.UUID

/**
 * Morse beeps (level 2 and up), in sync with the message: each dot and each dash is sent on its
 * own tick. Only the affected player hears them.
 *
 * Standard Morse timing with a unit of [UNIT_TICKS]: dot 1, dash 3, gap between symbols 1,
 * between letters 3 and between words 7. The .ogg files last exactly one dot (100 ms) and one dash (300 ms).
 *
 * Only used from the server thread.
 */
object MorseBeeper {

    private const val UNIT_TICKS = 2
    private const val VOLUME = 0.35f

    private class Beep(val atTick: Int, val dash: Boolean)

    /** Pending beeps of each player, in order. */
    private val pending = HashMap<UUID, ArrayDeque<Beep>>()

    /** Starts beeping [code] (MorseCode format) for [player]; replaces whatever was playing. */
    fun start(server: MinecraftServer, player: ServerPlayer, code: String) {
        val beeps = ArrayDeque<Beep>()
        var tick = server.tickCount + UNIT_TICKS  // a moment after the message
        code.split(" / ").forEachIndexed { w, word ->
            if (w > 0) tick += 6 * UNIT_TICKS  // with the 1 from the end of the symbol, 7 between words
            word.split(' ').forEachIndexed { l, letter ->
                if (l > 0) tick += 2 * UNIT_TICKS  // with the 1 from the end of the symbol, 3 between letters
                for (symbol in letter) {
                    val dash = symbol == '-'
                    beeps += Beep(tick, dash)
                    tick += (if (dash) 3 else 1) * UNIT_TICKS + UNIT_TICKS
                }
            }
        }
        pending[player.uuid] = beeps
    }

    fun tick(server: MinecraftServer) {
        if (pending.isEmpty()) return
        val iterator = pending.entries.iterator()
        while (iterator.hasNext()) {
            val (uuid, beeps) = iterator.next()
            val player = server.playerList.getPlayer(uuid)
            if (player == null) {
                iterator.remove()  // they disconnected: the rest of the message is lost
                continue
            }
            while (beeps.isNotEmpty() && beeps.first().atTick <= server.tickCount) {
                val sound = if (beeps.removeFirst().dash) ModSounds.MORSE_DASH else ModSounds.MORSE_DOT
                player.playNotifySound(sound, SoundSource.AMBIENT, VOLUME, 1f)
            }
            if (beeps.isEmpty()) iterator.remove()
        }
    }

    /** Stops the beeps of one player ("Start over"). */
    fun stop(uuid: UUID) {
        pending.remove(uuid)
    }

    /** Stops every beep (Abuse Mode disabled, server stopped...). */
    fun clear() {
        pending.clear()
    }
}
