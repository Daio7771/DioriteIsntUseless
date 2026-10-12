package daio7771.dioriteisntuseless.entity

import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.world.Visibility
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.DamageTypeTags
import net.minecraft.util.Mth
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.UUID
import kotlin.math.atan2

/**
 * A figure that visits one player (The Miner, The "Useful" Owner). The rules every visitor follows:
 *
 * - Only the visited player sees it ([broadcastToPlayer]): for anyone else it does not exist.
 * - It cannot be hurt, pushed, carried or targeted, makes no sound and does not attack.
 * - It is never saved with the world (its type is noSave): everything it has to remember about
 *   the player lives in their Abuse Mode state. If the world closes, it is simply gone.
 * - It goes away by itself as soon as something is off: the player is no longer in its world or
 *   is too far away, or the Abuse Mode is disabled or has stopped after an error.
 *
 * Everything that decides goes on the server; the client only draws it.
 */
abstract class VisitorEntity(type: EntityType<out VisitorEntity>, level: Level) : LivingEntity(type, level) {

    /** The only player who sees it. Set once, before it is added to the world. */
    var visitedPlayer: UUID? = null
        private set

    /** Ticks until it starts leaving, or -1 if it stays until it is sent away. */
    private var ticksLeft = -1

    /** Leaving: it vanishes as soon as the player is not looking (or after [MAX_LEAVING_TICKS]). */
    private var leaving = false
    private var leavingTicks = 0

    init {
        isSilent = true
        isInvulnerable = true
    }

    /** Before adding it to the world: who it visits and for how long ([ticks] -1: until sent away). */
    fun visit(player: ServerPlayer, ticks: Int) {
        visitedPlayer = player.uuid
        ticksLeft = ticks
    }

    /** Starts leaving without ever vanishing in front of the player's eyes. */
    fun leave() {
        leaving = true
    }

    override fun broadcastToPlayer(player: ServerPlayer): Boolean =
        player.uuid == visitedPlayer && super.broadcastToPlayer(player)

    override fun tick() {
        super.tick()
        if (level().isClientSide || isRemoved) return
        AbuseMode.guard("visitor tick") { serverTick() }
        // Also after an error inside it: with the Abuse Mode stopped, no visitor stays.
        if (!AbuseMode.active && !isRemoved) discard()
    }

    private fun serverTick() {
        val player = visitedPlayer?.let { level().server?.playerList?.getPlayer(it) }
        if (player == null || player.level() !== level() || distanceToSqr(player) > MAX_DISTANCE_SQR) {
            discard()
            return
        }
        visitTick(player)
        if (isRemoved || busy) return
        if (ticksLeft > 0 && --ticksLeft == 0) leave()
        if (leaving && (!Visibility.mightSee(player, boundingBox) || ++leavingTicks >= MAX_LEAVING_TICKS)) discard()
    }

    /** Busy with the player (trading): its time does not run and it does not leave meanwhile. */
    protected open val busy: Boolean get() = false

    /** Every server tick while it is with [player]. */
    protected open fun visitTick(player: ServerPlayer) {}

    /** Turns body and head towards [target] at once (when it appears). */
    fun face(target: Entity) {
        val yaw = yawTowards(target)
        yRot = yaw
        yRotO = yaw
        yHeadRot = yaw
        yHeadRotO = yaw
        yBodyRot = yaw
        yBodyRotO = yaw
    }

    /** Turns the head towards [target] at most [maxStep] degrees per tick; the body follows on its own. */
    protected fun turnHeadTowards(target: Entity, maxStep: Float) {
        val yaw = Mth.approachDegrees(yHeadRot, yawTowards(target), maxStep)
        yHeadRot = yaw
        yRot = yaw
        val dy = target.eyeY - eyeY
        val horizontal = Math.sqrt(distanceToSqr(target.x, y, target.z))
        xRot = Mth.approachDegrees(xRot, (-Math.toDegrees(atan2(dy, horizontal))).toFloat(), maxStep)
    }

    private fun yawTowards(target: Entity): Float =
        (Math.toDegrees(atan2(target.z - z, target.x - x)) - 90.0).toFloat()

    // ---- Untouchable and harmless

    override fun isInvulnerableTo(source: DamageSource): Boolean =
        !source.`is`(DamageTypeTags.BYPASSES_INVULNERABILITY)

    override fun hurt(source: DamageSource, amount: Float): Boolean =
        !isInvulnerableTo(source) && super.hurt(source, amount)

    override fun causeFallDamage(distance: Float, multiplier: Float, source: DamageSource): Boolean = false

    override fun isAttackable(): Boolean = false

    override fun canBeSeenAsEnemy(): Boolean = false

    override fun canBeHitByProjectile(): Boolean = false

    override fun isPushable(): Boolean = false

    override fun isPushedByFluid(): Boolean = false

    override fun doPush(entity: Entity) {}

    override fun pushEntities() {}

    override fun ignoreExplosion(): Boolean = true

    override fun isAffectedByPotions(): Boolean = false

    override fun startRiding(vehicle: Entity, force: Boolean): Boolean = false

    // ---- No equipment

    override fun getArmorSlots(): Iterable<ItemStack> = emptyList()

    override fun getItemBySlot(slot: EquipmentSlot): ItemStack = ItemStack.EMPTY

    override fun setItemSlot(slot: EquipmentSlot, stack: ItemStack) {}

    override fun getMainArm(): HumanoidArm = HumanoidArm.RIGHT

    companion object {
        /** Farther than this from the player (or in another world), it goes away. */
        private const val MAX_DISTANCE_SQR = 48.0 * 48.0

        /** If the player keeps looking at it for this long while it is leaving, it vanishes anyway. */
        private const val MAX_LEAVING_TICKS = 60 * 20
    }
}
