package daio7771.dioriteisntuseless.ability

import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.DioriteUselessness
import daio7771.dioriteisntuseless.config.DiuConfig
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.item.DioritinePickaxeItem
import daio7771.dioriteisntuseless.registry.ModItems
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import java.util.UUID

/**
 * Habilidad del Dioritine Pickaxe: al romper piedra o un mineral sin Shift, rompe también los
 * bloques de alrededor que sean piedra o mineral, en un cuadrado de 3x3 de frente a la cara
 * golpeada (pickaxe.sneakMode puede invertir lo del Shift).
 *
 * Como TreeFeller, se engancha a PlayerBlockBreakEvents.AFTER: el bloque golpeado ya no está y
 * vanilla aún no ha cobrado el pico ni soltado sus drops. Todo ocurre en el servidor lógico.
 *
 * Cada bloque de alrededor gasta 1 de durabilidad (el golpeado lo cobra vanilla). Una picada que
 * rompe al menos pickaxe.minBlocksForStrike bloques, contando el golpeado, cuenta: para el límite
 * de picadas del pico y para el Abuse Mode. La configuración se lee una vez por golpe.
 */
object AreaMiner {

    /** Más que el alcance de cualquier jugador: solo sirve para saber qué cara se ha golpeado. */
    private const val REACH = 8.0

    private val UNIT_CUBE = listOf(AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))

    /**
     * Jugadores con una picada en curso. Los eventos de rotura que dispara la propia picada (para
     * que otros mods puedan reaccionar) no deben volver a activar la habilidad. Solo se usa desde
     * el hilo del servidor.
     */
    private val mining = HashSet<UUID>()

    fun init() {
        PlayerBlockBreakEvents.AFTER.register(::onBlockBroken)
    }

    private fun onBlockBroken(level: Level, player: Player, pos: BlockPos, state: BlockState, blockEntity: BlockEntity?) {
        if (level !is ServerLevel || player !is ServerPlayer || player.uuid in mining) return
        val pickaxe = player.mainHandItem
        if (!pickaxe.`is`(ModItems.DIORITINE_PICKAXE) || !DioritinePickaxeItem.isMineable(state)) return

        val config = ModConfig.current.pickaxe
        // Desactivado o en la fase A del final del Abuse Mode: pico normal, un bloque cada vez.
        if (!config.enabled || DioriteUselessness.isUseless(player)) return
        if (!config.sneakMode.usesAbility(player.isShiftKeyDown)) return

        mining += player.uuid
        val extra = try {
            mineAround(level, player, pickaxe, pos, hitAxis(player, pos), config)
        } finally {
            mining -= player.uuid
        }
        // Bordes y esquinas que rompen menos bloques gastan durabilidad, pero no son una picada.
        if (extra + 1 >= config.minBlocksForStrike && !pickaxe.isEmpty) {
            DioritinePickaxeItem.addStrike(pickaxe, player)
            AbuseTracker.onStrike(player)
        }
    }

    /**
     * Eje de la cara golpeada: el 3x3 va en el plano perpendicular (pared: vertical; suelo o
     * techo: horizontal). El bloque ya es aire, así que se mira dónde corta la vista del jugador
     * al cubo que ocupaba. Si no lo corta (la vista ha cambiado justo ahora), el eje hacia el que
     * más mira.
     */
    private fun hitAxis(player: ServerPlayer, pos: BlockPos): Direction.Axis {
        val eye = player.eyePosition
        val hit = AABB.clip(UNIT_CUBE, eye, eye.add(player.lookAngle.scale(REACH)), pos)
        return (hit?.direction ?: Direction.orderedByNearest(player)[0]).axis
    }

    /** Rompe los 8 bloques de alrededor que pueda y devuelve cuántos ha roto. */
    private fun mineAround(
        level: ServerLevel,
        player: ServerPlayer,
        pickaxe: ItemStack,
        origin: BlockPos,
        axis: Direction.Axis,
        config: DiuConfig.Pickaxe,
    ): Int {
        val dropsAtOrigin = ArrayList<ItemStack>()
        var mined = 0
        for (pos in around(origin, axis)) {
            // Sin durabilidad para uno más (el último punto es del golpeado) o ya roto: se para aquí.
            if (pickaxe.isEmpty || !DioritinePickaxeItem.canAffordExtraBlock(pickaxe, player)) break
            if (!level.isLoaded(pos) || !isMineableAt(level, pos, level.getBlockState(pos))) continue
            val drops = BlockBreaker.breakAsPlayer(level, player, pos, pickaxe) { isMineableAt(level, pos, it) } ?: continue
            mined++
            DioritinePickaxeItem.addBlockWear(pickaxe, player)
            if (config.dropsAtOrigin) {
                dropsAtOrigin += drops
            } else {
                for (drop in drops) Block.popResource(level, pos, drop)
            }
        }
        BlockBreaker.dropAllAt(level, origin, dropsAtOrigin)
        return mined
    }

    /**
     * Solo piedra y sus minerales (el tag del pico), y por si un datapack mete otra cosa en el tag:
     * nada irrompible ni con entidad de bloque (cofres, hornos...).
     */
    private fun isMineableAt(level: ServerLevel, pos: BlockPos, state: BlockState): Boolean =
        DioritinePickaxeItem.isMineable(state) && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0f

    /** Los 8 bloques que rodean a [origin] en el plano perpendicular a [axis]. */
    private fun around(origin: BlockPos, axis: Direction.Axis): List<BlockPos> {
        val result = ArrayList<BlockPos>(8)
        for (a in -1..1) for (b in -1..1) {
            if (a == 0 && b == 0) continue
            result += when (axis) {
                Direction.Axis.X -> origin.offset(0, a, b)
                Direction.Axis.Y -> origin.offset(a, 0, b)
                Direction.Axis.Z -> origin.offset(a, b, 0)
            }
        }
        return result
    }
}
