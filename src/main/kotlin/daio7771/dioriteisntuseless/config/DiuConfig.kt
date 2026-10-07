package daio7771.dioriteisntuseless.config

/**
 * Valores de config/dioriteisntuseless.json, ya validados. Es inmutable: para cambiar algo se
 * sustituye la instancia entera en ModConfig, así quien la lee nunca ve una mezcla de valores
 * viejos y nuevos.
 */
data class DiuConfig(
    val diorite: Diorite = Diorite(),
    val treeFelling: TreeFelling = TreeFelling(),
    val axe: Axe = Axe(),
) {
    /** Se sincroniza con los clientes (ver ConfigSyncPacket). */
    data class Diorite(
        /** false: la diorita vuelve a los valores vanilla y se ignoran los otros dos. */
        val enabled: Boolean = true,
        val hardness: Float = 2.0f,
        val blastResistance: Float = 12.0f,
    ) {
        companion object {
            /** Lo que hay en un servidor sin el mod. */
            val VANILLA = Diorite(enabled = false)
        }
    }

    data class TreeFelling(
        /** false: el hacha rompe solo el tronco golpeado y gasta 1 por tronco, como un hacha normal. */
        val enabled: Boolean = true,
        /** Troncos como máximo por talada, contando el que golpea el jugador. */
        val maxLogs: Int = 128,
        /** Troncos que hay que romper para gastar 1 punto de durabilidad. */
        val logsPerDurabilityPoint: Int = 2,
        /** true: todos los drops salen juntos en el tronco golpeado; false: cada uno en su sitio. */
        val dropsAtOrigin: Boolean = true,
        val sneakMode: SneakMode = SneakMode.SNEAK_DISABLES,
    )

    data class Axe(
        /** Solo se aplica al arrancar: la durabilidad máxima se fija al registrar el ítem. */
        val durability: Int = 1000,
        /** true: solo Efficiency y Mending. */
        val restrictEnchantments: Boolean = true,
    )

    /** Rangos admitidos. Los valores fuera de rango se ajustan al límite más cercano. */
    object Limits {
        val HARDNESS = 1.5f..50.0f
        val BLAST_RESISTANCE = 6.0f..1200.0f
        val MAX_LOGS = 1..512
        val LOGS_PER_DURABILITY_POINT = 1..10
        val AXE_DURABILITY = 1..10000
    }
}

enum class SneakMode {
    /** Sin agacharse se tala el árbol; agachado, solo un tronco. */
    SNEAK_DISABLES,

    /** Al revés: sin agacharse, solo un tronco; agachado se tala el árbol. */
    SNEAK_ENABLES;

    fun fellsTree(sneaking: Boolean): Boolean = if (this == SNEAK_DISABLES) !sneaking else sneaking
}
