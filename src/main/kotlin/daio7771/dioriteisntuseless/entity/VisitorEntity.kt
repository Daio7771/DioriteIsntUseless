package daio7771.dioriteisntuseless.entity

import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.world.Visibility
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.UUID
import kotlin.math.atan2

/**
 * A figure that visits one player (The Miner, The "Useful" Owner). The rules every visitor follows:
 *
 * - Only the visited player sees it ([broadcastToPlayer]): for anyone else it does not exist.
 * - It cannot be hurt, pushed, carried, leashed, named or targeted, makes no vanilla sound and
 *   does not attack. Its own sounds (The Miner's) only reach the visited player, and it gives off
 *   no vibrations (sculk sensors, wardens).
 * - It is never saved with the world (its type is noSave) and never despawns on its own: everything
 *   it has to remember about the player lives in their Abuse Mode state. If the world closes, it is
 *   simply gone.
 * - It goes away by itself as soon as something is off: the player is no longer in its world or
 *   is too far away, or the Abuse Mode is disabled or has stopped after an error.
 *
 * It is a mob so it can use Minecraft's goals (The Miner walks and looks around with them), but it
 * has no goals unless the subclass adds them. Everything that decides goes on the server; the
 * client only draws it.
 */
abstract class VisitorEntity(type: EntityType<out VisitorEntity>, level: Level) : PathfinderMob(type, level) {

    /** The only player who sees it. Set once, before it is added to the world. */
    var visitedPlayer: UUID? = null
        private set

    /** The visited player, updated every server tick (null once they are gone). */
    var visiting: ServerPlayer? = null
        private set

    /** Ticks until it starts leaving, or -1 if it stays until it is sent away. */
    private var ticksLeft = -1

    /** Leaving: it vanishes as soon as the player is not looking (or after [MAX_LEAVING_TICKS]). */
    var isLeaving = false
        private set
    private var leavingTicks = 0

    init {
        isSilent = true
        isInvulnerable = true
    }

    /** Before adding it to the world, already in its spot: who it visits and for how long ([ticks] -1: until sent away). */
    fun visit(player: ServerPlayer, ticks: Int) {
        visitedPlayer = player.uuid
        ticksLeft = ticks
        onArrival(player)
    }

    /** At the end of [visit]: it is in its spot, not yet in the world. */
    protected open fun onArrival(player: ServerPlayer) {}

    /** Starts leaving without ever vanishing in front of the player's eyes. */
    fun leave() {
        if (isLeaving) return
        isLeaving = true
        leavingTicks = 0
        onLeave()
    }

    /** When it starts leaving. */
    protected open fun onLeave() {}

    /** It was leaving, but it stays after all (The Miner, when they talk to him as he walks away). */
    protected fun stay() {
        isLeaving = false
        leavingTicks = 0
    }

    override fun broadcastToPlayer(player: ServerPlayer): Boolean =
        player.uuid == visitedPlayer && super.broadcastToPlayer(player)

    override fun tick() {
        if (level().isClientSide) {
            super.tick()
            return
        }
        // The vanilla tick too: its goals (The Miner's) run inside it, and an error there must not
        // crash the server (golden rule 1).
        AbuseMode.guard("visitor tick") {
            super.tick()
            if (!isRemoved) serverTick()
        }
        // Also after an error inside it: with the Abuse Mode stopped, no visitor stays.
        if (!AbuseMode.active && !isRemoved) discard()
    }

    private fun serverTick() {
        val player = visitedPlayer?.let { level().server?.playerList?.getPlayer(it) }
        if (player == null || player.level() !== level() || distanceToSqr(player) > MAX_DISTANCE_SQR) {
            visiting = null
            discard()
            return
        }
        visiting = player
        visitTick(player)
        if (isRemoved || busy) return
        if (ticksLeft > 0 && --ticksLeft == 0) leave()
        if (isLeaving && (!Visibility.mightSee(player, boundingBox) || ++leavingTicks >= MAX_LEAVING_TICKS)) discard()
    }

    /** Busy with the player (trading, talking): its time does not run and it does not leave meanwhile. */
    protected open val busy: Boolean get() = false

    /** Every server tick while it is with [player] (after its goals have run). */
    protected open fun visitTick(player: ServerPlayer) {}

    /**
     * Right-clicked by [player] (see [init]): on the client, whether to send it to the server; on
     * the server, what it does. Nothing by default, and never anything vanilla does with mobs.
     */
    protected open fun use(player: Player, hand: InteractionHand): InteractionResult = InteractionResult.FAIL

    /** Turns body and head towards [target] at once (when it appears). */
    fun face(target: Entity) {
        val yaw = (Math.toDegrees(atan2(target.z - z, target.x - x)) - 90.0).toFloat()
        yRot = yaw
        yRotO = yaw
        yHeadRot = yaw
        yHeadRotO = yaw
        yBodyRot = yaw
        yBodyRotO = yaw
    }

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

    override fun canBeLeashed(player: Player): Boolean = false

    // ---- Nobody else can notice it: no vibrations for sculk sensors or wardens, not even its steps

    override fun getMovementEmission(): MovementEmission = MovementEmission.SOUNDS

    override fun dampensVibrations(): Boolean = true

    // ---- Never despawns: it goes away by its own rules (serverTick), and nothing saves it

    override fun checkDespawn() {}

    override fun removeWhenFarAway(distance: Double): Boolean = false

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

        /**
         * Right-clicking a visitor only does what [use] says. Mob.interact is final and would first
         * try name tags, leads and spawn eggs on it, so the click is caught before vanilla sees it.
         * The first call of a click carries the exact point hit (interactAt): that one is left to
         * vanilla, which does nothing with mobs, so the second call (interact) comes as usual.
         */
        fun init() {
            UseEntityCallback.EVENT.register { player, _, hand, entity, hitResult ->
                if (entity !is VisitorEntity || hitResult != null || player.isSpectator) {
                    InteractionResult.PASS
                } else {
                    entity.use(player, hand)
                }
            }
        }
    }
}
