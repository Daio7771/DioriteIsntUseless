package daio7771.dioriteisntuseless.config

/**
 * Values of config/dioriteisntuseless.json, already validated. It is immutable: to change anything
 * the whole instance is replaced in ModConfig, so whoever reads it never sees a mix of old and new
 * values.
 */
data class DiuConfig(
    val diorite: Diorite = Diorite(),
    val treeFelling: TreeFelling = TreeFelling(),
    val axe: Axe = Axe(),
    val pickaxe: Pickaxe = Pickaxe(),
    val abuseMode: AbuseMode = AbuseMode(),
    val client: Client = Client(),
) {
    /** Synced to clients (see ConfigSyncPacket). */
    data class Diorite(
        /** false: diorite goes back to the vanilla values and the other two are ignored. */
        val enabled: Boolean = true,
        val hardness: Float = 2.0f,
        val blastResistance: Float = 12.0f,
    ) {
        companion object {
            /** What a server without the mod has. */
            val VANILLA = Diorite(enabled = false)
        }
    }

    data class TreeFelling(
        /** false: the axe only breaks the log it hits and uses 1 per log, like a normal axe. */
        val enabled: Boolean = true,
        /** Most logs per felling, counting the one the player hits. */
        val maxLogs: Int = 128,
        /** Logs that have to be broken to use 1 durability point. */
        val logsPerDurabilityPoint: Int = 2,
        /** true: all drops come out together at the log hit; false: each one where it was. */
        val dropsAtOrigin: Boolean = true,
        val sneakMode: SneakMode = SneakMode.SNEAK_DISABLES,
        /**
         * Whole trees an axe fells before breaking; 0 = no limit. Repairing it on an anvil with
         * ingots resets the count. Synced to clients (for the bar).
         */
        val treesBeforeBreaking: Int = 17,
    ) {
        companion object {
            /** Default value up to version 1 of the file; ConfigCodec migrates it to the new one. */
            const val OLD_DEFAULT_TREES_BEFORE_BREAKING = 7
        }
    }

    /** Applies to the axe and to the pickaxe: they are made of the same material (DioritineTier). */
    data class Axe(
        /** Only applied at startup: the maximum durability is fixed when the items are registered. */
        val durability: Int = 1000,
        /** true: only Efficiency and Mending. */
        val restrictEnchantments: Boolean = true,
    )

    /** See docs/claudeplans/PICKAXE_DESIGN.md. */
    data class Pickaxe(
        /** false: the pickaxe only breaks the block it hits, like a normal pickaxe. */
        val enabled: Boolean = true,
        /**
         * 3x3 strikes a pickaxe makes before breaking. Repairing it on an anvil with ingots resets
         * the count. Synced to clients (for the bar).
         */
        val strikesBeforeBreaking: Int = 10,
        val sneakMode: SneakMode = SneakMode.SNEAK_DISABLES,
        /** true: all drops come out together at the block hit; false: each one where it was. */
        val dropsAtOrigin: Boolean = true,
        /** Blocks a strike has to break, counting the one hit, to count as a strike. */
        val minBlocksForStrike: Int = 5,
    )

    /** See docs/claudeplans/HORROR_DESIGN.md. Only used by the server. */
    data class AbuseMode(
        /** false: nothing is counted and there are no signals. It does not undo what was done. */
        val enabled: Boolean = true,
        /**
         * Whole trees that have to be felled with the axe at each level (0 to 3) to reach the
         * next one (1 to 4). Counted from the moment the current level was reached.
         */
        val treesPerLevel: List<Int> = listOf(17, 20, 22, 24),
        /** Whole trees that have to be felled at level 4 to reach the ending (besides THE sign). */
        val treesUntilEnding: Int = 26,
        /**
         * 3x3 pickaxe strikes each step asks for: going up to levels 1 to 4 and, the last one, the
         * ending. Trees and strikes add up as fractions (see AbuseTracker.stepReached).
         */
        val pickaxeSteps: List<Int> = listOf(10, 15, 15, 15, 15),
    ) {
        companion object {
            /** Size of treesPerLevel. */
            const val LEVELS = 4

            /** Size of pickaxeSteps: the levels and the ending. */
            const val STEPS = LEVELS + 1
        }
    }

    /** Client only: not synced and not used by the server. */
    data class Client(
        /** The warning screen has already been shown (it is only shown once). */
        val warningShown: Boolean = false,
    )

    /** Accepted ranges. Values out of range are moved to the nearest limit. */
    object Limits {
        val HARDNESS = 1.5f..50.0f
        val BLAST_RESISTANCE = 6.0f..1200.0f
        val MAX_LOGS = 1..512
        val LOGS_PER_DURABILITY_POINT = 1..10
        val TREES_BEFORE_BREAKING = 0..1000
        val AXE_DURABILITY = 1..10000
        val STRIKES_BEFORE_BREAKING = 1..1000
        val MIN_BLOCKS_FOR_STRIKE = 1..9
        val ABUSE_TREES = 1..1000
        val ABUSE_STRIKES = 1..1000
    }
}

/** Whether sneaking turns the tool's ability (felling the whole tree, mining 3x3) off or on. */
enum class SneakMode {
    /** Without sneaking the ability is used; while sneaking, the tool breaks a single block. */
    SNEAK_DISABLES,

    /** The other way round: without sneaking, a single block; while sneaking, the ability is used. */
    SNEAK_ENABLES;

    fun usesAbility(sneaking: Boolean): Boolean = if (this == SNEAK_DISABLES) !sneaking else sneaking
}
