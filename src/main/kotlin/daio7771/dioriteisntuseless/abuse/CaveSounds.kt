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
 * Minecraft cave sounds (ambient.cave) while the mod is being abused, also on the surface while
 * felling trees (decided by Daio): sometimes, a few seconds after a tree or a strike. From level 1
 * on; the higher the level, the more often, but always with a stretch of silence between one and
 * the next. The last one plays when the ending is over, as the credits open ([playFinal]).
 *
 * Only the affected player hears them. They play a few blocks away, like real cave sounds and at
 * a similar volume, in the "Ambient/Environment" category (golden rule 2: nothing loud and sudden).
 *
 * Only used from the server thread, through AbuseTracker and Ending (which are already guarded).
 */
object CaveSounds {

    /** Chance that a tree or a strike brings a sound, at levels 1 to 4. */
    private val CHANCE = floatArrayOf(0.20f, 0.25f, 0.30f, 0.35f)

    /** Minimum silence after each sound, in ticks, at levels 1 to 4 (from 2 min down to 1 min). */
    private val QUIET_TICKS = longArrayOf(2_400L, 2_000L, 1_600L, 1_200L)

    /** From the tree or strike to the sound (2 to 8 s), so it does not seem to be caused by it. */
    private val DELAY_TICKS = 40L..160L

    /** Distance from the player, in blocks: within hearing range (16), but not right on top. */
    private const val MIN_DISTANCE = 5.0
    private const val MAX_DISTANCE = 9.0
    private const val FINAL_DISTANCE = 4.0
    private const val VOLUME = 0.9f

    private class Timing {
        /** Server tick at which the next one plays, or -1 if none is on its way. */
        var playAt = -1L

        /** No new one until this server tick. */
        var quietUntil = 0L
    }

    /** Not saved: restarting the server (or switching worlds) starts from scratch. */
    private val timings = HashMap<UUID, Timing>()

    /** Called by AbuseTracker with every tree and every strike it counts. */
    fun onAbuseAction(player: ServerPlayer, state: PlayerAbuse) {
        val index = state.level - 1
        if (index !in CHANCE.indices) return
        val now = player.server.tickCount.toLong()
        val timing = timings.getOrPut(player.uuid, ::Timing)
        if (timing.playAt >= 0 || now < timing.quietUntil || player.random.nextFloat() >= CHANCE[index]) return
        timing.playAt = now + DELAY_TICKS.first + player.random.nextInt((DELAY_TICKS.last - DELAY_TICKS.first + 1).toInt())
        timing.quietUntil = timing.playAt + QUIET_TICKS[index]
    }

    /** From the player tick (every second): plays it if it is due. */
    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        val timing = timings[player.uuid] ?: return
        if (timing.playAt < 0 || player.server.tickCount < timing.playAt) return
        timing.playAt = -1
        // Not if they have started over or reached the ending in the meantime.
        if (state.level in 1..AbuseTracker.MAX_LEVEL) {
            play(player, MIN_DISTANCE + player.random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE))
        }
    }

    /** The last sound of the ending, a bit closer, right when the credits are due. */
    fun playFinal(player: ServerPlayer) = play(player, FINAL_DISTANCE)

    /** Abuse Mode disabled or server starting: nothing pending. */
    fun clear() = timings.clear()

    /** One of the cave sounds (the client picks which one from the seed), [distance] blocks away. */
    private fun play(player: ServerPlayer, distance: Double) {
        val random = player.random
        val angle = random.nextDouble() * 2 * PI
        val x = player.x + cos(angle) * distance
        val y = player.eyeY + random.nextDouble() * 4 - 2
        val z = player.z + sin(angle) * distance
        player.connection.send(ClientboundSoundPacket(SoundEvents.AMBIENT_CAVE, SoundSource.AMBIENT, x, y, z, VOLUME, 1f, random.nextLong()))
    }
}
