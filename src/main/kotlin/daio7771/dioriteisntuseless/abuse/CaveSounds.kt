package daio7771.dioriteisntuseless.abuse

import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sonidos de cueva de Minecraft (ambient.cave) mientras se abusa del mod, también en la superficie
 * talando árboles (decidido por Daio): a veces, unos segundos después de un árbol o de una picada.
 * Desde el nivel 1; cuanto más alto, más a menudo, pero siempre con un rato de silencio entre uno
 * y otro. El último suena al acabar el final, cuando se abren los créditos ([playFinal]).
 *
 * Solo los oye el jugador afectado. Suenan a unos bloques de él, como los de las cuevas de verdad
 * y a un volumen parecido, en la categoría "Ambiente" (regla de oro 2: nada fuerte de golpe).
 *
 * Solo se usa desde el hilo del servidor, a través de AbuseTracker y Ending (que ya van protegidos).
 */
object CaveSounds {

    /** Probabilidad de que un árbol o una picada traiga un sonido, en los niveles 1 a 4. */
    private val CHANCE = floatArrayOf(0.20f, 0.25f, 0.30f, 0.35f)

    /** Silencio mínimo después de cada sonido, en ticks, en los niveles 1 a 4 (de 2 min a 1 min). */
    private val QUIET_TICKS = longArrayOf(2_400L, 2_000L, 1_600L, 1_200L)

    /** Del árbol o la picada al sonido (2 a 8 s), para que no parezca que lo causa. */
    private val DELAY_TICKS = 40L..160L

    /** Distancia al jugador, en bloques: dentro de lo que se oye (16), pero no encima. */
    private const val MIN_DISTANCE = 5.0
    private const val MAX_DISTANCE = 9.0
    private const val FINAL_DISTANCE = 4.0
    private const val VOLUME = 0.9f

    private class Timing {
        /** Tick del servidor en que suena el próximo, o -1 si no hay ninguno en camino. */
        var playAt = -1L

        /** Hasta este tick del servidor, ninguno nuevo. */
        var quietUntil = 0L
    }

    /** No se guarda: al reiniciar el servidor (o cambiar de mundo) empieza de cero. */
    private val timings = HashMap<UUID, Timing>()

    /** Lo llama AbuseTracker con cada árbol y cada picada que cuenta. */
    fun onAbuseAction(player: ServerPlayer, state: PlayerAbuse) {
        val index = state.level - 1
        if (index !in CHANCE.indices) return
        val now = player.server.tickCount.toLong()
        val timing = timings.getOrPut(player.uuid, ::Timing)
        if (timing.playAt >= 0 || now < timing.quietUntil || player.random.nextFloat() >= CHANCE[index]) return
        timing.playAt = now + DELAY_TICKS.first + player.random.nextInt((DELAY_TICKS.last - DELAY_TICKS.first + 1).toInt())
        timing.quietUntil = timing.playAt + QUIET_TICKS[index]
    }

    /** Desde el tick del jugador (cada segundo): si toca, suena. */
    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        val timing = timings[player.uuid] ?: return
        if (timing.playAt < 0 || player.server.tickCount < timing.playAt) return
        timing.playAt = -1
        // Si entre tanto ha empezado de nuevo o ha llegado al final, ya no.
        if (state.level in 1..AbuseTracker.MAX_LEVEL) {
            play(player, MIN_DISTANCE + player.random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE))
        }
    }

    /** El último sonido del final, un poco más cerca, justo cuando tocan los créditos. */
    fun playFinal(player: ServerPlayer) = play(player, FINAL_DISTANCE)

    /** Abuse Mode desactivado o servidor que arranca: nada pendiente. */
    fun clear() = timings.clear()

    /** Uno de los sonidos de cueva (el cliente elige cuál con la semilla), a [distance] bloques. */
    private fun play(player: ServerPlayer, distance: Double) {
        val random = player.random
        val angle = random.nextDouble() * 2 * PI
        val x = player.x + cos(angle) * distance
        val y = player.eyeY + random.nextDouble() * 4 - 2
        val z = player.z + sin(angle) * distance
        player.connection.send(ClientboundSoundPacket(SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, x, y, z, VOLUME, 1f, random.nextLong()))
    }
}
