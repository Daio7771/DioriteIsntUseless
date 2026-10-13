package daio7771.dioriteisntuseless.entity

import daio7771.dioriteisntuseless.abuse.AbuseData
import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.visitor.MinerNotes
import daio7771.dioriteisntuseless.abuse.visitor.MinerQuests
import daio7771.dioriteisntuseless.abuse.visitor.MinerSpeech
import daio7771.dioriteisntuseless.abuse.visitor.MinerState
import daio7771.dioriteisntuseless.abuse.visitor.MinerTrades
import daio7771.dioriteisntuseless.abuse.world.Visibility
import daio7771.dioriteisntuseless.entity.ai.MinerGoals
import daio7771.dioriteisntuseless.registry.ModSounds
import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
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
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.pathfinder.BlockPathTypes
import net.minecraft.world.phys.AABB
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
 * His sounds (vanilla sounds under his own events in sounds.json, see ModSounds), all positional
 * and only for the visited player:
 * - Walking: a lantern jingling and the metal of his tools, on his steps.
 * - Standing at levels 0-1: a pickaxe tapping stone, slow and never in a steady rhythm.
 * - Always: water dripping very low around him, as if the mine travelled with him.
 * - A deal (a trade, or a new job): a deep "hmm".
 * - Level 4: nothing at all. No cough, no sigh, ever.
 *
 * Right-clicking him: he greets them and pays and gives jobs (MinerSpeech, MinerQuests), then the
 * vanilla trading screen opens; when it is closed, his visit is over (he leaves without being seen
 * leaving). His lines come one at a time, and he does not leave while he still has something to say.
 * At the key moments he also hands over a note (MinerNotes), in its place among his lines.
 *
 * At level 4 there is nothing to trade: as soon as they see him (or talk to him) he says goodbye,
 * and once the last line is said he is gone for good for that player.
 */
class TheMinerEntity(type: EntityType<out TheMinerEntity>, level: Level) : VisitorEntity(type, level), Merchant {

    private var tradingPlayer: Player? = null
    private var offers = MerchantOffers()

    /** He has already talked to the player in this visit: no second greeting or job reminder. */
    private var talked = false

    /** What he still has to say or hand over, in order, and ticks until the next one. */
    private val speech = ArrayDeque<Said>()
    private var lineCountdown = 0

    private sealed interface Said {
        class Line(val text: Component) : Said
        class Note(val note: MinerNotes.Note) : Said
    }

    /** His goodbye has started (level 4). */
    private var sayingGoodbye = false

    /** Where he arrived was under a roof (a cave, a house): he only walks to spots like that one. */
    private var arrivedCovered = false

    /** Where he is looking because The "Useful" Owner is about to appear there ([foresee]), and for how long. */
    var omenSpot: Vec3? = null
        private set
    private var omenTicks = 0

    /** Ticks until his next pickaxe tap and his next drip; when his last "hmm" was. */
    private var tapCountdown = 0
    private var dripCountdown = 0
    private var lastHmmAt = Int.MIN_VALUE / 2

    override val busy: Boolean get() = tradingPlayer != null || speech.isNotEmpty()

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
        // He arrives in silence: the first drip and the first tap come a while later.
        dripCountdown = DRIP_MIN_TICKS + random.nextInt(DRIP_EXTRA_TICKS + 1)
        tapCountdown = TAP_RESUME_MIN_TICKS + random.nextInt(TAP_RESUME_EXTRA_TICKS + 1)
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

    /** Taller for culling only: his speech bubble (MinerBubbles) is drawn even with his body off the screen. */
    override fun getBoundingBoxForCulling(): AABB = super.getBoundingBoxForCulling().expandTowards(0.0, BUBBLE_ROOM, 0.0)

    /** At level 4 he stands still: his body does not turn after his head (unless the head cannot turn any further). */
    override fun createBodyControl(): BodyRotationControl = MinerBodyControl(this)

    override fun visitTick(player: ServerPlayer) {
        if (tickCount % MOOD_CHECK_TICKS == 0) updateMood(player)
        // Out of reach of his goals, or at level 4: he stays where he is.
        if (mood == MinerSpeech.Mood.FAREWELL || nearPlayer == null) navigation.stop()
        if (omenTicks > 0 && --omenTicks == 0) omenSpot = null
        tickSounds()
        if (!sayingGoodbye && distanceTo(player) <= GOODBYE_DISTANCE && Visibility.mightSee(player, boundingBox) &&
            mood == MinerSpeech.Mood.FAREWELL
        ) {
            sayGoodbye(player)
        }
        tickSpeech(player)
    }

    // ---- Sounds (only the visited player hears them, and only from near enough)

    /** Taps and drips, on their irregular schedules. At level 4, silence. */
    private fun tickSounds() {
        val player = nearPlayer
        if (player == null || mood == MinerSpeech.Mood.FAREWELL) return
        if (--dripCountdown <= 0) {
            dripCountdown = DRIP_MIN_TICKS + random.nextInt(DRIP_EXTRA_TICKS + 1)
            val spread = DRIP_SPREAD
            playTo(
                player, ModSounds.MINER_DRIP,
                x + (random.nextDouble() * 2 - 1) * spread, y + 1.5 + random.nextDouble() * 1.5, z + (random.nextDouble() * 2 - 1) * spread,
                pitch = 0.9f + random.nextFloat() * 0.2f,
            )
        }
        // Standing, at levels 0-1, not busy with them: tapping. Walking or talking stops it, and it
        // starts again only a while after.
        if (mood != MinerSpeech.Mood.CALM || isTalking || !navigation.isDone) {
            tapCountdown = TAP_RESUME_MIN_TICKS + random.nextInt(TAP_RESUME_EXTRA_TICKS + 1)
            return
        }
        if (--tapCountdown > 0) return
        playTo(player, ModSounds.MINER_TAP, x, y + 0.5, z, 0.8f + random.nextFloat() * 0.2f, 0.9f + random.nextFloat() * 0.2f)
        // Never a steady rhythm: a random wait each time, and now and then a second tap right after.
        tapCountdown = if (random.nextFloat() < TAP_DOUBLE_CHANCE) {
            TAP_DOUBLE_MIN_TICKS + random.nextInt(TAP_DOUBLE_EXTRA_TICKS + 1)
        } else {
            TAP_MIN_TICKS + random.nextInt(TAP_EXTRA_TICKS + 1)
        }
    }

    /** His steps (Entity.move): instead of footsteps, the lantern, and often the metal of his tools. */
    override fun playStepSound(pos: BlockPos, state: BlockState) {
        val player = nearPlayer ?: return
        if (mood == MinerSpeech.Mood.FAREWELL) return
        playTo(player, ModSounds.MINER_LANTERN, x, y + 1.0, z, pitch = 0.9f + random.nextFloat() * 0.2f)
        if (random.nextFloat() < TOOLS_CHANCE) {
            playTo(player, ModSounds.MINER_TOOLS, x, y + 0.9, z, pitch = 0.85f + random.nextFloat() * 0.3f)
        }
    }

    /** A deal is made: "hmm". Once, even if several trades happen at once (shift-click). */
    private fun hmm() {
        val player = nearPlayer ?: return
        if (mood == MinerSpeech.Mood.FAREWELL || tickCount - lastHmmAt < HMM_COOLDOWN_TICKS) return
        lastHmmAt = tickCount
        playTo(player, ModSounds.MINER_HMM, x, eyeY, z, pitch = 0.95f + random.nextFloat() * 0.1f)
    }

    /** One of his sounds at ([x], [y], [z]), only for [player]. Volume and pitch multiply those in sounds.json. */
    private fun playTo(player: ServerPlayer, sound: SoundEvent, x: Double, y: Double, z: Double, volume: Float = 1f, pitch: Float = 1f) {
        player.connection.send(
            ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.NEUTRAL, x, y, z, volume, pitch, random.nextLong(),
            )
        )
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
        if (!talked) {
            val greeting = MinerSpeech.greeting(player, state.miner, mood, !MinerNotes.given(state.miner, MinerNotes.Note.WARNING))
            for ((index, line) in greeting.lines.withIndex()) {
                speech += Said.Line(line)
                // What he could not say out loud, he writes down: the note comes right after the cut.
                if (index == 0 && greeting.cutOff) handOver(state.miner, MinerNotes.Note.WARNING)
            }
        }
        val jobBefore = state.miner.questType
        MinerQuests.onTalk(player, state, !talked, mood).forEach { speech += Said.Line(it) }
        // A new job is never of the same kind as the one before: a different kind means a new deal.
        if (state.miner.questType.isNotEmpty() && state.miner.questType != jobBefore) hmm()
        if (state.level == MinerNotes.LEVEL_3_NOTE_LEVEL) handOver(state.miner, MinerNotes.Note.LEVEL_3)
        talked = true
        data.setDirty()
        if (mood == MinerSpeech.Mood.FAREWELL) {
            if (!sayingGoodbye) sayGoodbye(player)
            return
        }
        navigation.stop()
        offers = MinerTrades.offersFor(state.level)
        MinerQuests.sendNote(player, state, mood)
        tradingPlayer = player
        openTradingScreen(player, displayName, 0)
        // If the screen could not open, he is not left waiting for nobody.
        if (player.containerMenu === player.inventoryMenu) tradingPlayer = null
    }

    private fun sayGoodbye(player: ServerPlayer) {
        sayingGoodbye = true
        talked = true
        MinerSpeech.farewell().forEach { speech += Said.Line(it) }
        handOver(AbuseData.get(player.server).get(player.uuid).miner, MinerNotes.Note.FAREWELL)
    }

    /** Queues [note] after what he is saying, unless they already have it from him (or it is already queued). */
    private fun handOver(miner: MinerState, note: MinerNotes.Note) {
        if (MinerNotes.given(miner, note) || speech.any { it is Said.Note && it.note == note }) return
        speech += Said.Note(note)
    }

    /**
     * One thing at a time: each line when the one before has been typed and read, each note a
     * moment after the line before it. The first one right away.
     */
    private fun tickSpeech(player: ServerPlayer) {
        if (speech.isEmpty()) return
        if (--lineCountdown > 0) return
        when (val next = speech.removeFirst()) {
            is Said.Line -> lineCountdown = MinerSpeech.say(player, id, next.text, mood)
            is Said.Note -> {
                val data = AbuseData.get(player.server)
                MinerNotes.give(player, this, data.get(player.uuid).miner, next.note)
                data.setDirty()
                lineCountdown = NOTE_PAUSE_TICKS
            }
        }
        if (speech.isEmpty() && sayingGoodbye) {
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
        if (!level().isClientSide) AbuseMode.guard("miner trade sound") { hmm() }
    }

    override fun notifyTradeUpdated(stack: ItemStack) {}

    override fun getVillagerXp(): Int = 0

    override fun overrideXp(xp: Int) {}

    override fun showProgressBar(): Boolean = false

    /** Vanilla never plays this one on the server (MerchantMenu.playTradeSound): his "hmm" comes from [notifyTrade]. */
    override fun getNotifyTradeSound(): SoundEvent = ModSounds.MINER_HMM

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

        /** After handing over a note, this long before his next line. */
        private const val NOTE_PAUSE_TICKS = 30

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

        /** Room above his head for his speech bubble, in blocks. */
        private const val BUBBLE_ROOM = 2.0

        /** Slow: an old man. */
        const val MOVEMENT_SPEED = 0.25

        /** Drips: every 1-4 s, somewhere up to [DRIP_SPREAD] blocks around him. */
        private const val DRIP_MIN_TICKS = 20
        private const val DRIP_EXTRA_TICKS = 60
        private const val DRIP_SPREAD = 2.5

        /** Taps: 1.5-5 s apart, sometimes ([TAP_DOUBLE_CHANCE]) a second one after a quarter to half a second. */
        private const val TAP_MIN_TICKS = 30
        private const val TAP_EXTRA_TICKS = 70
        private const val TAP_DOUBLE_CHANCE = 0.25f
        private const val TAP_DOUBLE_MIN_TICKS = 5
        private const val TAP_DOUBLE_EXTRA_TICKS = 5

        /** After walking or talking, 2-5 s before he taps again. */
        private const val TAP_RESUME_MIN_TICKS = 40
        private const val TAP_RESUME_EXTRA_TICKS = 60

        /** How often a step also rattles his tools. */
        private const val TOOLS_CHANCE = 0.6f

        private const val HMM_COOLDOWN_TICKS = 20
    }
}
