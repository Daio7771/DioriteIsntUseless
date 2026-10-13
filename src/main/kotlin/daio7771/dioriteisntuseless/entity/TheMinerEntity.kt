package daio7771.dioriteisntuseless.entity

import daio7771.dioriteisntuseless.abuse.AbuseData
import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.visitor.MinerNotes
import daio7771.dioriteisntuseless.abuse.visitor.MinerQuests
import daio7771.dioriteisntuseless.abuse.visitor.MinerSpeech
import daio7771.dioriteisntuseless.abuse.visitor.MinerState
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
 * The Miner: an old miner who asks favors (MinerQuests). Everything he remembers of the
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
 * - A deal (a new favor): a deep "hmm".
 * - Level 4: nothing at all. No cough, no sigh, ever.
 *
 * Right-clicking him: he talks (MinerSpeech): the first time in a visit he greets them; every time
 * he pays a finished favor and asks a new one, or reminds them of the current one and how far
 * along it is (MinerQuests). There is no trading screen. His lines come one at a time, and he does
 * not leave while he still has something to say. At the key moments he also hands over a note
 * (MinerNotes), in its place among his lines. On his first visit, when they come near, he tosses
 * them his instructions book. While they are near him, a note on their screen shows his favor
 * (MinerQuests.sendNote).
 *
 * He waits for them: once they have seen him, if they stay within [NOTICE_DISTANCE] without
 * talking to him for [NO_RESPONSE_TICKS] (15 s), he leaves (far away, the count stops). Leaving,
 * he turns and limps away, and is gone once out of their sight (VisitorEntity); talking to him
 * as he goes makes him stay.
 *
 * At level 4 there are no more favors: as soon as they see him (or talk to him) he says goodbye,
 * and once the last line is said he is gone for good for that player.
 */
class TheMinerEntity(type: EntityType<out TheMinerEntity>, level: Level) : VisitorEntity(type, level) {

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

    /** The note on their screen (MinerQuests.sendNote) has been sent for this visit. */
    private var noteSent = false

    /** They have seen him in this visit: from then on, he only waits [NO_RESPONSE_TICKS] for them. */
    private var noticed = false
    private var unansweredTicks = 0

    /** His instructions book: already tossed, or they already had it. */
    private var instructionsDone = false

    /** Ticks until his next pickaxe tap and his next drip; when his last "hmm" was. */
    private var tapCountdown = 0
    private var dripCountdown = 0
    private var lastHmmAt = Int.MIN_VALUE / 2

    override val busy: Boolean get() = speech.isNotEmpty()

    /** Talking to the player: he stands and looks at them. */
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
        roamHere()
        updateMood(player)
        // He arrives in silence: the first drip and the first tap come a while later.
        dripCountdown = DRIP_MIN_TICKS + random.nextInt(DRIP_EXTRA_TICKS + 1)
        tapCountdown = TAP_RESUME_MIN_TICKS + random.nextInt(TAP_RESUME_EXTRA_TICKS + 1)
    }

    /** He walks a few blocks around where he is: in the open, or less under a roof (caves, houses). */
    private fun roamHere() {
        restrictTo(blockPosition(), if (arrivedCovered) COVERED_ROAM_RADIUS else ROAM_RADIUS)
    }

    /** Leaving, he may walk beyond his few blocks (MinerGoals' walk away). */
    override fun onLeave() {
        clearRestriction()
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
        if (!noteSent) sendNote(player)
        // Out of reach of his goals, or at level 4 (unless he is leaving): he stays where he is.
        if ((mood == MinerSpeech.Mood.FAREWELL && !isLeaving) || nearPlayer == null) navigation.stop()
        if (omenTicks > 0 && --omenTicks == 0) omenSpot = null
        tickSounds()
        tickInstructions(player)
        tickPatience(player)
        if (!sayingGoodbye && distanceTo(player) <= GOODBYE_DISTANCE && Visibility.mightSee(player, boundingBox) &&
            mood == MinerSpeech.Mood.FAREWELL
        ) {
            sayGoodbye(player)
        }
        tickSpeech(player)
    }

    /**
     * His first visit (in each cycle): when they come within [THROW_DISTANCE] and he can see them,
     * he tosses his instructions book at their feet (MinerNotes). Not at level 4.
     */
    private fun tickInstructions(player: ServerPlayer) {
        if (instructionsDone || tickCount % INSTRUCTIONS_CHECK_TICKS != 0 || busy || isLeaving) return
        if (mood == MinerSpeech.Mood.FAREWELL) return
        val data = AbuseData.get(player.server)
        val miner = data.get(player.uuid).miner
        if (MinerNotes.given(miner, MinerNotes.Note.INSTRUCTIONS)) {
            instructionsDone = true
            return
        }
        if (distanceToSqr(player) > THROW_DISTANCE * THROW_DISTANCE || !hasLineOfSight(player)) return
        swing(InteractionHand.MAIN_HAND)
        MinerNotes.throwTo(player, this, miner, MinerNotes.Note.INSTRUCTIONS)
        data.setDirty()
        instructionsDone = true
        // A book landing at their feet: they know he is here.
        noticed = true
    }

    /**
     * Once they have seen him: [NO_RESPONSE_TICKS] within [NOTICE_DISTANCE] without talking to him
     * and he leaves. Farther away, the count waits; talking to him starts it again.
     */
    private fun tickPatience(player: ServerPlayer) {
        if (isLeaving || sayingGoodbye) return
        if (busy) {
            unansweredTicks = 0
            return
        }
        if (!noticed && Visibility.sees(player, boundingBox)) noticed = true
        if (!noticed || distanceToSqr(player) > NOTICE_DISTANCE * NOTICE_DISTANCE) return
        if (++unansweredTicks >= NO_RESPONSE_TICKS) leave()
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

    /** A deal is made (a new favor): "hmm". Never twice in a row within a second. */
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
        if (mood == this.mood) return
        entityData.set(DATA_MOOD, mood.ordinal.toByte())
        // The note on their screen changes with his mood (and is gone at level 4).
        if (noteSent) sendNote(player)
    }

    private fun sendNote(player: ServerPlayer) {
        noteSent = true
        MinerQuests.sendNote(player, AbuseData.get(player.server).get(player.uuid), id)
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
        // One thing at a time: while he talks, clicking him again does nothing.
        if (busy || !AbuseMode.active) return
        if (isLeaving) {
            // His goodbye is final; otherwise they answered after all, and he stays.
            if (sayingGoodbye) return
            stay()
            roamHere()
        }
        unansweredTicks = 0
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
        MinerQuests.onTalk(player, state, mood).forEach { speech += Said.Line(it) }
        // A new job is never of the same kind as the one before: a different kind means a new deal.
        if (state.miner.questType.isNotEmpty() && state.miner.questType != jobBefore) hmm()
        if (state.level == MinerNotes.LEVEL_3_NOTE_LEVEL) handOver(state.miner, MinerNotes.Note.LEVEL_3)
        talked = true
        data.setDirty()
        sendNote(player)
        if (mood == MinerSpeech.Mood.FAREWELL) {
            if (!sayingGoodbye) sayGoodbye(player)
            return
        }
        navigation.stop()
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

    /**
     * Vanilla's body control, except at level 4: then his body only turns when his head cannot
     * turn any further, so he stands still and only his head follows the player. It runs on both
     * sides (the client works out the body on its own), so it reads the synced [mood].
     */
    private class MinerBodyControl(private val miner: TheMinerEntity) : BodyRotationControl(miner) {
        override fun clientTick() {
            // Walking (also away, after his goodbye), the body follows his steps as usual.
            val moving = (miner.x - miner.xo) * (miner.x - miner.xo) + (miner.z - miner.zo) * (miner.z - miner.zo) > MOVING_SQR
            if (miner.mood != MinerSpeech.Mood.FAREWELL || moving) {
                super.clientTick()
                return
            }
            miner.yBodyRot = Mth.rotateIfNecessary(miner.yBodyRot, miner.yHeadRot, miner.maxHeadYRot.toFloat())
        }
    }

    companion object {
        private val DATA_MOOD: EntityDataAccessor<Byte> =
            SynchedEntityData.defineId(TheMinerEntity::class.java, EntityDataSerializers.BYTE)

        /** Vanilla's threshold for "moving" (BodyRotationControl). */
        private const val MOVING_SQR = 2.5000003E-7

        /** He waits this long for them to talk to him, once they have seen him and while they are this close. */
        const val NO_RESPONSE_TICKS = 15 * 20
        const val NOTICE_DISTANCE = 8.0

        /** He tosses his instructions book when they come this close (and checks this often). */
        private const val THROW_DISTANCE = 6.0
        private const val INSTRUCTIONS_CHECK_TICKS = 10

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
