package daio7771.dioriteisntuseless.abuse.morse

import daio7771.dioriteisntuseless.registry.ModSounds
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import java.util.UUID

/**
 * Pitidos del Morse (nivel 2 en adelante), sincronizados con el mensaje: cada punto y cada raya
 * se manda en su tick. Solo los oye el jugador afectado.
 *
 * Ritmo estándar del Morse con una unidad de [UNIT_TICKS]: punto 1, raya 3, hueco entre señales 1,
 * entre letras 3 y entre palabras 7. Los .ogg duran exactamente un punto (100 ms) y una raya (300 ms).
 *
 * Solo se usa desde el hilo del servidor.
 */
object MorseBeeper {

    private const val UNIT_TICKS = 2
    private const val VOLUME = 0.35f

    private class Beep(val atTick: Int, val dash: Boolean)

    /** Pitidos pendientes de cada jugador, en orden. */
    private val pending = HashMap<UUID, ArrayDeque<Beep>>()

    /** Empieza a pitar [code] (formato de MorseCode) para [player]; sustituye lo que estuviera sonando. */
    fun start(server: MinecraftServer, player: ServerPlayer, code: String) {
        val beeps = ArrayDeque<Beep>()
        var tick = server.tickCount + UNIT_TICKS  // un instante después del mensaje
        code.split(" / ").forEachIndexed { w, word ->
            if (w > 0) tick += 6 * UNIT_TICKS  // con la 1 de fin de señal, 7 entre palabras
            word.split(' ').forEachIndexed { l, letter ->
                if (l > 0) tick += 2 * UNIT_TICKS  // con la 1 de fin de señal, 3 entre letras
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
                iterator.remove()  // se ha desconectado: el resto del mensaje se pierde
                continue
            }
            while (beeps.isNotEmpty() && beeps.first().atTick <= server.tickCount) {
                val sound = if (beeps.removeFirst().dash) ModSounds.MORSE_DASH else ModSounds.MORSE_DOT
                player.playNotifySound(sound, SoundSource.AMBIENT, VOLUME, 1f)
            }
            if (beeps.isEmpty()) iterator.remove()
        }
    }

    /** Corta los pitidos de un jugador ("Start over"). */
    fun stop(uuid: UUID) {
        pending.remove(uuid)
    }

    /** Corta todos los pitidos (Abuse Mode desactivado, servidor parado...). */
    fun clear() {
        pending.clear()
    }
}
