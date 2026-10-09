package daio7771.dioriteisntuseless.item

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.DiggerItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/**
 * Pico de dioritina: muy bueno con la piedra y sus minerales (el tag
 * dioriteisntuseless:dioritine_pickaxe_mineable) e inútil para todo lo demás: ahí va a la mitad
 * que a mano y no suelta nada que pida pico. El 3x3 lo hace AreaMiner.
 *
 * Es un DiggerItem y no un PickaxeItem porque PickaxeItem solo admite daños enteros y el pico,
 * como el hacha, hace 0.5. Mina con nivel de hierro (diamante y esmeralda incluidos).
 */
class DioritinePickaxeItem(properties: Item.Properties) :
    DiggerItem(ATTACK_DAMAGE_MODIFIER, ATTACK_SPEED_MODIFIER, DioritineTier, MINEABLE, properties) {

    /** Velocidad del tier en piedra y minerales; en cualquier otro bloque, la mitad que a mano. */
    override fun getDestroySpeed(stack: ItemStack, state: BlockState): Float =
        if (isMineable(state)) speed else NON_STONE_SPEED

    /**
     * Gasta 1 de durabilidad, como vanilla, y rompe el pico si ya ha dado todas sus picadas.
     * Vanilla llama a esto después de decidir los drops del bloque golpeado (con una copia del
     * pico), así que el último bloque también suelta lo suyo. Los demás bloques de una picada
     * los cobra AreaMiner. En creativo vanilla no llama a esto.
     */
    override fun mineBlock(stack: ItemStack, level: Level, state: BlockState, pos: BlockPos, miner: LivingEntity): Boolean {
        val result = super.mineBlock(stack, level, state, pos, miner)
        if (level.isClientSide || miner !is Player || stack.isEmpty) return result
        // Si se baja el límite en la configuración, un pico que ya lo pase se rompe al siguiente bloque.
        if (strikes(stack) >= ModConfig.current.pickaxe.strikesBeforeBreaking) ToolWear.breakInMainHand(stack, miner)
        return result
    }

    // La barra muestra lo que esté más cerca de romper el pico: la durabilidad o las picadas.

    override fun isBarVisible(stack: ItemStack): Boolean = stack.isDamaged || remainingStrikesFraction(stack) < 1f

    override fun getBarWidth(stack: ItemStack): Int = ToolWear.barWidth(remainingFraction(stack))

    override fun getBarColor(stack: ItemStack): Int = ToolWear.barColor(remainingFraction(stack))

    private fun remainingFraction(stack: ItemStack): Float = ToolWear.remainingFraction(stack, remainingStrikesFraction(stack))

    private fun remainingStrikesFraction(stack: ItemStack): Float = ToolWear.remainingUses(strikes(stack), strikesBeforeBreaking())

    companion object {
        // Se suman a los valores base del jugador: 1.0 de daño y 4.0 de velocidad de ataque.
        private const val ATTACK_DAMAGE_MODIFIER = -0.5f  // daño total 0.5, como el hacha
        private const val ATTACK_SPEED_MODIFIER = -3.0f   // velocidad total 1.0, como el hacha

        private const val NON_STONE_SPEED = 0.5f

        /** Lo único que pica bien (y en 3x3): piedra del Overworld, adoquines y sus minerales. */
        val MINEABLE: TagKey<Block> = TagKey.create(Registries.BLOCK, Dioriteisntuseless.id("dioritine_pickaxe_mineable"))

        fun isMineable(state: BlockState): Boolean = state.`is`(MINEABLE)

        /** Picadas 3x3 dadas desde que se hizo o se reparó en el yunque con lingotes, en el NBT del pico. */
        private const val STRIKES_TAG = "DioritineStrikes"

        /**
         * pickaxe.strikesBeforeBreaking del servidor remoto al que está conectado este cliente, o
         * null (servidor dedicado, un solo jugador o sin conectar: vale la configuración local).
         * Solo lo usa la barra; la rotura la decide siempre el servidor con su configuración.
         */
        @Volatile
        var serverStrikesBeforeBreaking: Int? = null

        private fun strikesBeforeBreaking(): Int =
            serverStrikesBeforeBreaking ?: ModConfig.current.pickaxe.strikesBeforeBreaking

        fun strikes(stack: ItemStack): Int = stack.tag?.getInt(STRIKES_TAG) ?: 0

        /** true si [stack] es un pico de dioritina con picadas en la cuenta. */
        @JvmStatic
        fun hasStrikes(stack: ItemStack): Boolean = stack.`is`(ModItems.DIORITINE_PICKAXE) && strikes(stack) > 0

        /** Para el yunque: reparar con lingotes reinicia la cuenta. A otros ítems no les hace nada. */
        @JvmStatic
        fun resetStrikes(stack: ItemStack) {
            if (stack.`is`(ModItems.DIORITINE_PICKAXE)) stack.removeTagKey(STRIKES_TAG)
        }

        /**
         * Cuenta una picada. No rompe el pico aquí: lo hace [mineBlock] justo después, cuando el
         * bloque golpeado ya tiene sus drops. En creativo no cuenta.
         */
        fun addStrike(stack: ItemStack, player: Player) {
            if (player.abilities.instabuild || stack.isEmpty) return
            stack.orCreateTag.putInt(STRIKES_TAG, strikes(stack) + 1)
        }

        /** 1 de durabilidad por cada bloque de alrededor que rompe una picada. En creativo, nada. */
        fun addBlockWear(stack: ItemStack, player: Player) {
            if (player.abilities.instabuild || stack.isEmpty) return
            stack.hurtAndBreak(1, player) { it.broadcastBreakEvent(EquipmentSlot.MAINHAND) }
        }

        /**
         * true si aún puede romper un bloque de alrededor sin quedarse sin durabilidad: el último
         * punto se guarda para el bloque golpeado, que se cobra después y tiene que soltar lo suyo.
         */
        fun canAffordExtraBlock(stack: ItemStack, player: Player): Boolean =
            player.abilities.instabuild || !stack.isDamageableItem || stack.maxDamage - stack.damageValue > 1
    }
}
