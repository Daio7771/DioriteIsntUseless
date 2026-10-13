package daio7771.dioriteisntuseless.entity

import daio7771.dioriteisntuseless.abuse.AbuseData
import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.visitor.MinerQuests
import daio7771.dioriteisntuseless.abuse.visitor.MinerSpeech
import daio7771.dioriteisntuseless.abuse.visitor.MinerTrades
import daio7771.dioriteisntuseless.abuse.world.Visibility
import daio7771.dioriteisntuseless.entity.ai.MinerGoals
import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.ai.control.BodyRotationControl
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.Merchant
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.item.trading.MerchantOffers
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.pathfinder.BlockPathTypes
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Miner: an old miner who buys Diorite Crystals (MinerTrades). Everything he remembers of the
 * player is in their MinerState, not here.
 *
 * How he behaves (MinerGoals) follows the player's abuse level, his [mood]:
 * - Levels 0-1: he limps slowly around where he arrived, looks at the player when they are close
 *   and steps back if they come closer than about 2 blocks.
 * - Levels 2-3: he walks less. Sometimes he stands still staring at the player for 10-20 s, or at
 *   a diorite block nearby.
 * - Level 4: he stands still and only his head follows the player.
 * Always: he never attacks, never flees, never breaks or places blocks, never follows the player
 * around (he stays near where he arrived, [ROAM_RADIUS]), never goes through doors nor from open
 * ground to under a roof or the other way round (houses). If the player holds a dioritine axe or
 * pickaxe, he looks at it rather than at their face ([lookPointOn]). His goals only run with the
 * player close by ([nearPlayer]).
 *
 * Right-clicking him: he greets them and pays and gives jobs (MinerSpeech, MinerQuests), then the
 * vanilla trading screen opens; when it is closed, his visit is over (he leaves without being seen
 * leaving). His lines come one at a time, and he does not leave while he still has something to say.
 *
 * At level 4 there is nothing to trade: as soon as they see him (or talk to him) he says goodbye,
 * and once the last line is said he is gone for good for that player.
 */
class TheMinerEntity(type: EntityType<out TheMinerEntity>, level: Level) : VisitorEntity(type, level), Merchant {

    private var tradingPlayer: Player? = null
    private var offers = MerchantOffers()

    /** He has already talked to the player in this visit: no second greeting or job reminder. */
    private var talked = false

    /** What he still has to say, and ticks until the next line. */
    private val lines = ArrayDeque<Component>()
    private var lineCountdown = 0

    /** His goodbye has started (level 4). */
    private var sayingGoodbye = false

    /** Where he arrived was under a roof (a cave, a house): he only walks to spots like that one. */
    private var arrivedCovered = false

    /** Where he is looking because The "Useful" Owner is about to appear there ([foresee]), and for how long. */
    var omenSpot: Vec3? = null
        private set
    private var omenTicks = 0

    override val busy: Boolean get() = tradingPlayer != null || lines.isNotEmpty()

    /** Trading or talking with the player: he stands and looks at them. */
    val isTalking: Boolean get() = busy

    /** His mood with the visited player. Synced, so the client knows it too (his body, his voice). */
    val mood: MinerSpeech.Mood
        get() = MinerSpeech.Mood.entries.getOrElse(entityData.get(DATA_MOOD).toInt()) { MinerSpeech.Mood.CALM }

    /** The visited player if they are close enough for his goals to run, otherwise null (he just stands). */
    val nearPlayer: ServerPlayer?
        get() = visiting?.takeIf { it.isAlive && !it.isSpectator && distanceToSqr(it) <= AI_RANGE * AI_RANGE }

    override fun defineSynchedData() {
        super.defineSynchedData()
        entityData.define(DATA_MOOD, MinerSpeech.Mood.CALM.ordinal.toByte())
    }

    override fun registerGoals() {
        MinerGoals.addTo(this, goalSelector)
    }

    override fun createNavigation(level: Level): PathNavigation =
        GroundPathNavigation(this, level).apply {
            // Neither through doors (open or closed) nor swimming: he never walks into a house.
            setCanOpenDoors(false)
            setCanPassDoors(false)
            setCanFloat(false)
        }

    init {
        // He never sets foot in water.
        setPathfindingMalus(BlockPathTypes.WATER, -1f)
    }

    override fun onArrival(player: ServerPlayer) {
        arrivedCovered = isCovered(blockPosition())
        restrictTo(blockPosition(), if (arrivedCovered) COVERED_ROAM_RADIUS else ROAM_RADIUS)
        updateMood(player)
    }

    /**
     * Where random walks may end ([MinerGoals]): under a roof only if he arrived under one, and in
     * the open only if he arrived in the open. So he never wanders into a house (or out of a mine).
     */
    override fun getWalkTargetValue(pos: BlockPos, level: LevelReader): Float =
        if (isCovered(pos) == arrivedCovered) 0f else Float.NEGATIVE_INFINITY

    /** Something solid above (leaves do not count: a tree is not a roof). */
    private fun isCovered(pos: BlockPos): Boolean =
        level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.x, pos.z) > pos.y

    /** Old legs: he does not jump down from more than one block. */
    override fun getMaxFallDistance(): Int = 1

    /** He limps: every step, a moment dragging the bad leg (the client also draws it, VisitorRenderer). */
    override fun getSpeed(): Float =
        if (tickCount % LIMP_CYCLE_TICKS < LIMP_DRAG_TICKS) super.getSpeed() * LIMP_DRAG_SPEED else super.getSpeed()

    /** At level 4 he stands still: his body does not turn after his head (unless the head cannot turn any further). */
    override fun createBodyControl(): BodyRotationControl = MinerBodyControl(this)

    override fun visitTick(player: ServerPlayer) {
        if (tickCount % MOOD_CHECK_TICKS == 0) updateMood(player)
        // Out of reach of his goals, or at level 4: he stays where he is.
        if (mood == MinerSpeech.Mood.FAREWELL || nearPlayer == null) navigation.stop()
        if (omenTicks > 0 && --omenTicks == 0) omenSpot = null
        if (!sayingGoodbye && distanceTo(player) <= GOODBYE_DISTANCE && Visibility.mightSee(player, boundingBox) &&
            mood == MinerSpeech.Mood.FAREWELL
        ) {
            sayGoodbye()
        }
        tickLines(player)
    }

    private fun updateMood(player: ServerPlayer) {
        val mood = MinerSpeech.mood(AbuseData.get(player.server).get(player.uuid).level)
        entityData.set(DATA_MOOD, mood.ordinal.toByte())
    }

    /** OwnerStatue: The "Useful" Owner will appear at [spot] in a moment. He turns his head there for [ticks]. */
    fun foresee(spot: Vec3, ticks: Int) {
        omenSpot = spot
        omenTicks = ticks
    }

    /**
     * Where he looks at [player]: their face, or the tool if they hold a dioritine axe or pickaxe
     * (an estimate of where their hand is: at their side, a little in front).
     */
    fun lookPointOn(player: Player): Vec3 {
        val arm = when {
            isDioritineTool(player.mainHandItem) -> player.mainArm
            isDioritineTool(player.offhandItem) -> player.mainArm.opposite
            else -> return player.eyePosition
        }
        val yaw = Math.toRadians(player.yBodyRot.toDouble())
        val side = if (arm == HumanoidArm.RIGHT) HAND_SIDE else -HAND_SIDE
        // Facing (-sin, cos); their right hand side is (-cos, -sin).
        return Vec3(
            player.x - cos(yaw) * side - sin(yaw) * HAND_FORWARD,
            player.y + player.eyeHeight * HAND_HEIGHT,
            player.z - sin(yaw) * side + cos(yaw) * HAND_FORWARD,
        )
    }

    private fun isDioritineTool(stack: ItemStack): Boolean =
        stack.`is`(ModItems.DIORITINE_AXE) || stack.`is`(ModItems.DIORITINE_PICKAXE)

    /** On the client he only exists for the player he visits: the click goes to the server. */
    override fun use(player: Player, hand: InteractionHand): InteractionResult {
        if (level().isClientSide) return InteractionResult.SUCCESS
        if (player !is ServerPlayer || player.uuid != visitedPlayer) return InteractionResult.FAIL
        AbuseMode.guard("miner talk") { talk(player) }
        return InteractionResult.CONSUME
    }

    private fun talk(player: ServerPlayer) {
        if (tradingPlayer != null || !AbuseMode.active) return
        val data = AbuseData.get(player.server)
        val state = data.get(player.uuid)
        val mood = MinerSpeech.mood(state.level)
        if (!talked) lines += MinerSpeech.greeting(player, state.miner, mood)
        lines += MinerQuests.onTalk(player, state, !talked, mood)
        talked = true
        data.setDirty()
        if (mood == MinerSpeech.Mood.FAREWELL) {
            if (!sayingGoodbye) sayGoodbye()
            return
        }
        navigation.stop()
        offers = MinerTrades.offersFor(state.level)
        tradingPlayer = player
        openTradingScreen(player, displayName, 0)
        // If the screen could not open, he is not left waiting for nobody.
        if (player.containerMenu === player.inventoryMenu) tradingPlayer = null
    }

    private fun sayGoodbye() {
        sayingGoodbye = true
        talked = true
        lines += MinerSpeech.farewell()
    }

    /** One line every [LINE_GAP_TICKS]; the first one right away. */
    private fun tickLines(player: ServerPlayer) {
        if (lines.isEmpty()) return
        if (--lineCountdown > 0) return
        MinerSpeech.say(player, lines.removeFirst())
        lineCountdown = LINE_GAP_TICKS
        if (lines.isEmpty() && sayingGoodbye) {
            // Only now: if he went away halfway, he comes back to say it all.
            val data = AbuseData.get(player.server)
            data.get(player.uuid).miner.gone = true
            data.setDirty()
            leave()
        }
    }

    /** If he goes away for any reason, their trading screen closes and the crystals in it go back to them. */
    override fun remove(reason: RemovalReason) {
        (tradingPlayer as? ServerPlayer)?.closeContainer()
        super.remove(reason)
    }

    // ---- Merchant

    /** MerchantMenu.removed passes null: the trading screen was closed, so his visit is over. */
    override fun setTradingPlayer(player: Player?) {
        val wasTrading = tradingPlayer != null
        tradingPlayer = player
        if (wasTrading && player == null && !level().isClientSide) leave()
    }

    override fun getTradingPlayer(): Player? = tradingPlayer

    override fun getOffers(): MerchantOffers = offers

    override fun overrideOffers(offers: MerchantOffers) {
        this.offers = offers
    }

    override fun notifyTrade(offer: MerchantOffer) {
        offer.increaseUses()
    }

    override fun notifyTradeUpdated(stack: ItemStack) {}

    override fun getVillagerXp(): Int = 0

    override fun overrideXp(xp: Int) {}

    override fun showProgressBar(): Boolean = false

    override fun getNotifyTradeSound(): SoundEvent = SoundEvents.VILLAGER_YES

    override fun isClientSide(): Boolean = level().isClientSide

    /**
     * Vanilla's body control, except at level 4: then his body only turns when his head cannot
     * turn any further, so he stands still and only his head follows the player. It runs on both
     * sides (the client works out the body on its own), so it reads the synced [mood].
     */
    private class MinerBodyControl(private val miner: TheMinerEntity) : BodyRotationControl(miner) {
        override fun clientTick() {
            if (miner.mood != MinerSpeech.Mood.FAREWELL) {
                super.clientTick()
                return
            }
            miner.yBodyRot = Mth.rotateIfNecessary(miner.yBodyRot, miner.yHeadRot, miner.maxHeadYRot.toFloat())
        }
    }

    companion object {
        private val DATA_MOOD: EntityDataAccessor<Byte> =
            SynchedEntityData.defineId(TheMinerEntity::class.java, EntityDataSerializers.BYTE)

        private const val LINE_GAP_TICKS = 40

        /** At level 4 he starts his goodbye when they see him this close. */
        private const val GOODBYE_DISTANCE = 8f

        /** Farther than this from the player, his goals stop and he just stands (performance). */
        const val AI_RANGE = 24.0

        /** How far from where he arrived he may walk: in the open, and under a roof (caves, houses). */
        private const val ROAM_RADIUS = 6
        private const val COVERED_ROAM_RADIUS = 3

        private const val MOOD_CHECK_TICKS = 20

        /** Every [LIMP_CYCLE_TICKS], [LIMP_DRAG_TICKS] at [LIMP_DRAG_SPEED] of his speed. */
        private const val LIMP_CYCLE_TICKS = 14
        private const val LIMP_DRAG_TICKS = 5
        private const val LIMP_DRAG_SPEED = 0.3f

        /** Where a player's hand is, roughly: to the side, in front and at this fraction of their eye height. */
        private const val HAND_SIDE = 0.35
        private const val HAND_FORWARD = 0.2
        private const val HAND_HEIGHT = 0.55

        /** Slow: an old man. */
        const val MOVEMENT_SPEED = 0.25
    }
}
