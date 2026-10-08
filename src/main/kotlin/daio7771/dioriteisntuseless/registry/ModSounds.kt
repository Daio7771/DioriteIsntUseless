package daio7771.dioriteisntuseless.registry

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.sounds.SoundEvent

/** Sonidos propios (assets/dioriteisntuseless/sounds.json; los .ogg los genera tools/generate_sounds.py). */
object ModSounds {

    /** Pitido corto y suave del Morse (punto). */
    val MORSE_DOT: SoundEvent = register("morse.dot")

    /** Pitido largo y suave del Morse (raya). */
    val MORSE_DASH: SoundEvent = register("morse.dash")

    private fun register(name: String): SoundEvent {
        val id = Dioriteisntuseless.id(name)
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))
    }

    /** Llamar desde onInitialize: acceder al objeto ya registra los sonidos. */
    fun init() {}
}
