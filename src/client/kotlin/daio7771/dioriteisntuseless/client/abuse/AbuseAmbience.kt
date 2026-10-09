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
 * El fondo inquietante del Abuse Mode: un bucle por nivel (ModSounds.AMBIENCE), desde el nivel 1,
 * muy bajo, hasta el final. Al cambiar de nivel el bucle anterior se apaga mientras entra el nuevo,
 * y nada empieza ni acaba de golpe (regla de oro). Suena en la categoría "Ambiente": el jugador
 * puede bajarlo con su control de volumen.
 *
 * Mientras hay fondo, la música de Minecraft no suena (MusicManagerMixin): el fondo manda.
 *
 * Todo se usa desde el hilo del cliente.
 */
object AbuseAmbience {

    /** Volumen de los bucles; los niveles bajos ya van más flojos en el propio archivo. */
    private const val VOLUME = 0.6f

    /** Al empezar el nivel 1 o al entrar al mundo con nivel. */
    private const val FADE_IN_TICKS = 200
    /** De un nivel al siguiente. */
    private const val CROSSFADE_TICKS = 160
    /** Al llegar al final: más rápido, se nota. */
    private const val FINAL_CROSSFADE_TICKS = 60
    /** Al acabar (Start over o Abuse Mode desactivado). */
    private const val FADE_OUT_TICKS = 100
    /** Si el motor de sonido corta el bucle (recarga de recursos...), espera antes de volver a ponerlo. */
    private const val RESTART_DELAY_TICKS = 100

    private var current: AmbienceSound? = null
    private var currentLevel = 0
    private var restartIn = 0

    /** Un error lo ha apagado hasta reiniciar el juego (regla de oro 1). */
    private var failed = false

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    /** Para MusicManagerMixin: hay (o va a haber) fondo, así que la música no suena. */
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
                // Fuera del mundo: Minecraft ya ha parado todos los sonidos al salir.
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
                // El motor de sonido lo ha cortado: vuelve a entrar despacio, pasado un rato.
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
     * Un bucle sin posición (se oye igual en todas partes, en estéreo) cuyo volumen sube al empezar
     * y, cuando se le pide, baja hasta pararse. El motor de sonido le llama cada tick.
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

        /** Empieza a volumen 0 (si no, el motor no lo pondría). */
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

        /** Curva suave de 0 a 1, sin arranques ni paradas bruscas. */
        private fun smooth(x: Float): Float = x * x * (3 - 2 * x)
    }
}
