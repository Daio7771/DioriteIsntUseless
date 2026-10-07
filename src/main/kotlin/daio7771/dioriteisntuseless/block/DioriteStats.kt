package daio7771.dioriteisntuseless.block

import daio7771.dioriteisntuseless.config.DiuConfig

/**
 * Valores que el mod da a minecraft:diorite (solo a ese bloque; no a la diorita pulida,
 * losas, escaleras ni muros). Los aplican BlockStateBaseMixin y ExplosionDamageCalculatorMixin.
 *
 * Los mixins se llaman muchísimo, así que solo leen [active]: un campo volátil con una instancia
 * inmutable, sin bloqueos ni ningún otro trabajo. Las escrituras (raras) van sincronizadas.
 *
 * En un cliente conectado a un servidor remoto mandan los valores del servidor (ver
 * ConfigSyncPacket); si no, los de la configuración local. En un solo jugador cliente y servidor
 * integrado comparten esta clase y la misma configuración, así que siempre coinciden.
 */
object DioriteStats {

    @Volatile
    private var active = DiuConfig.Diorite()

    /** Valores de config/dioriteisntuseless.json. */
    private var local = DiuConfig.Diorite()

    /** Valores del servidor remoto al que está conectado este cliente, o null. */
    @Volatile
    private var server: DiuConfig.Diorite? = null

    /** true mientras se usan los valores de un servidor remoto en vez de los locales. */
    val usingServerValues: Boolean get() = server != null

    /** Dureza de minecraft:diorite. [original] es la vanilla, para cuando el cambio está desactivado. */
    @JvmStatic
    fun hardness(original: Float): Float {
        val values = active
        return if (values.enabled) values.hardness else original
    }

    /** Resistencia a explosiones de minecraft:diorite. */
    @JvmStatic
    fun explosionResistance(original: Float): Float {
        val values = active
        return if (values.enabled) values.blastResistance else original
    }

    @Synchronized
    fun applyLocal(values: DiuConfig.Diorite) {
        local = values
        if (server == null) active = values
    }

    @Synchronized
    fun applyServerValues(values: DiuConfig.Diorite) {
        server = values
        active = values
    }

    @Synchronized
    fun clearServerValues() {
        server = null
        active = local
    }
}
