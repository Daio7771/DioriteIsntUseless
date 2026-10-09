package daio7771.dioriteisntuseless.registry

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.sounds.SoundEvent

/**
 * Sonidos propios (assets/dioriteisntuseless/sounds.json). Los .ogg los generan
 * tools/generate_sounds.py (Morse) y tools/generate_ambience.py (fondo).
 */
object ModSounds {

    /** Pitido corto y suave del Morse (punto). */
    val MORSE_DOT: SoundEvent = register("morse.dot")

    /** Pitido largo y suave del Morse (raya). */
    val MORSE_DASH: SoundEvent = register("morse.dash")

    /** Fondo inquietante del Abuse Mode, en bucle: niveles 1 a 4 y el final (índice = nivel - 1). */
    val AMBIENCE: List<SoundEvent> = listOf("level1", "level2", "level3", "level4", "final").map { register("ambience.$it") }

    private fun register(name: String): SoundEvent {
        val id = Dioriteisntuseless.id(name)
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))
    }

    /** Llamar desde onInitialize: acceder al objeto ya registra los sonidos. */
    fun init() {}
}
