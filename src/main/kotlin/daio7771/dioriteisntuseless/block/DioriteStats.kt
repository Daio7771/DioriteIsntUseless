package daio7771.dioriteisntuseless.block

import daio7771.dioriteisntuseless.config.DiuConfig

/**
 * Values the mod gives to minecraft:diorite (only that block; not polished diorite, slabs,
 * stairs or walls). Applied by BlockStateBaseMixin and ExplosionDamageCalculatorMixin.
 *
 * The mixins are called a huge number of times, so they only read [active]: a volatile field with
 * an immutable instance, with no locks or any other work. Writes (rare) are synchronized.
 *
 * On a client connected to a remote server the server's values apply (see ConfigSyncPacket);
 * otherwise, the local config. In single player the client and the integrated server share this
 * class and the same config, so they always match.
 */
object DioriteStats {

    @Volatile
    private var active = DiuConfig.Diorite()

    /** Values from config/dioriteisntuseless.json. */
    private var local = DiuConfig.Diorite()

    /** Values of the remote server this client is connected to, or null. */
    @Volatile
    private var server: DiuConfig.Diorite? = null

    /** true while the values of a remote server are used instead of the local ones. */
    val usingServerValues: Boolean get() = server != null

    /** Hardness of minecraft:diorite. [original] is the vanilla one, for when the change is disabled. */
    @JvmStatic
    fun hardness(original: Float): Float {
        val values = active
        return if (values.enabled) values.hardness else original
    }

    /** Blast resistance of minecraft:diorite. */
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
