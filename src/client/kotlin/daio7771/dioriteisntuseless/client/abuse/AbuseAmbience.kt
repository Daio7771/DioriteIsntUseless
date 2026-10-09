package daio7771.dioriteisntuseless.client.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.registry.ModSounds
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

/**
 * The unsettling Abuse Mode background: one loop per level (ModSounds.AMBIENCE), from level 1,
 * very quiet, up to the ending. On a level change the previous loop fades out while the new one
 * fades in, and nothing starts or stops suddenly (golden rule). It plays in the
 * "Ambient/Environment" category: the player can turn it down with that volume slider.
 *
 * While there is a background, Minecraft's music does not play (MusicManagerMixin): the
 * background rules.
 *
 * Everything is used from the client thread.
 */
object AbuseAmbience {

    /** Volume of the loops; the low levels are already quieter in the file itself. */
    private const val VOLUME = 0.6f

    /** When level 1 starts or when joining the world with a level. */
    private const val FADE_IN_TICKS = 200
    /** From one level to the next. */
    private const val CROSSFADE_TICKS = 160
    /** On reaching the ending: faster, so it is noticed. */
    private const val FINAL_CROSSFADE_TICKS = 60
    /** When it ends (Start over or Abuse Mode disabled). */
    private const val FADE_OUT_TICKS = 100
    /** If the sound engine cuts the loop (resource reload...), wait before starting it again. */
    private const val RESTART_DELAY_TICKS = 100

    private var current: AmbienceSound? = null
    private var currentLevel = 0
    private var restartIn = 0

    /** An error has turned it off until the game restarts (golden rule 1). */
    private var failed = false

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    /** For MusicManagerMixin: there is (or is about to be) a background, so the music does not play. */
    @JvmStatic
    fun blocksMusic(): Boolean = !failed && targetLevel(Minecraft.getInstance()) > 0

    private fun targetLevel(client: Minecraft): Int {
        if (client.level == null || client.player == null) return 0
        return ClientAbuseState.level.coerceIn(0, ModSounds.AMBIENCE.size)
    }

    private fun tick(client: Minecraft) {
        if (failed) return
        try {
            if (client.level == null) {
                // Out of the world: Minecraft already stopped every sound on leaving.
                current = null
                currentLevel = 0
                restartIn = 0
                return
            }
            if (restartIn > 0) {
                restartIn--
                return
            }
            val target = targetLevel(client)
            val sound = current
            if (target != currentLevel) {
                switchTo(client, target)
            } else if (sound != null && !client.soundManager.isActive(sound)) {
                // The sound engine cut it: it fades back in slowly, after a while.
                current = null
                currentLevel = 0
                restartIn = RESTART_DELAY_TICKS
            }
        } catch (e: Exception) {
            LOGGER.error("Abuse mode: the background sound failed; it is off until the game is restarted.", e)
            failed = true
            current?.let { client.soundManager.stop(it) }
            current = null
        }
    }

    private fun switchTo(client: Minecraft, level: Int) {
        val fade = when {
            level == 0 -> FADE_OUT_TICKS
            level == AbuseTracker.LEVEL_FINAL -> FINAL_CROSSFADE_TICKS
            currentLevel == 0 -> FADE_IN_TICKS
            else -> CROSSFADE_TICKS
        }
        current?.fadeOut(fade)
        current = null
        if (level > 0) {
            val sound = AmbienceSound(ModSounds.AMBIENCE[level - 1], fade)
            client.soundManager.play(sound)
            current = sound
        }
        currentLevel = level
    }

    /**
     * A loop with no position (it sounds the same everywhere, in stereo) whose volume rises when it
     * starts and, when asked, falls until it stops. The sound engine calls it every tick.
     */
    private class AmbienceSound(event: SoundEvent, private val fadeInTicks: Int) :
        AbstractTickableSoundInstance(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom()) {

        private var age = 0
        private var fadeOutTicks = 0
        private var fadeOutAge = 0
        private var fadeOutFrom = 0f

        init {
            looping = true
            delay = 0
            relative = true
            attenuation = SoundInstance.Attenuation.NONE
            volume = 0f
        }

        /** Starts at volume 0 (otherwise the engine would not play it). */
        override fun canStartSilent(): Boolean = true

        fun fadeOut(ticks: Int) {
            if (fadeOutTicks > 0) return
            fadeOutTicks = ticks.coerceAtLeast(1)
            fadeOutFrom = volume
        }

        override fun tick() {
            if (fadeOutTicks > 0) {
                fadeOutAge++
                if (fadeOutAge >= fadeOutTicks) {
                    volume = 0f
                    stop()
                } else {
                    volume = fadeOutFrom * smooth(1f - fadeOutAge.toFloat() / fadeOutTicks)
                }
            } else if (age < fadeInTicks) {
                age++
                volume = VOLUME * smooth(age.toFloat() / fadeInTicks)
            }
        }

        /** Smooth curve from 0 to 1, with no abrupt start or stop. */
        private fun smooth(x: Float): Float = x * x * (3 - 2 * x)
    }
}
