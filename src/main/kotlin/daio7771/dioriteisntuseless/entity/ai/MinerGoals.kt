package daio7771.dioriteisntuseless.entity.ai

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.abuse.visitor.MinerSpeech.Mood
import daio7771.dioriteisntuseless.entity.TheMinerEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.GoalSelector
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.RandomStrollGoal
import net.minecraft.world.entity.ai.util.LandRandomPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.EnumSet

/**
 * What The Miner does, as Minecraft goals (TheMinerEntity explains the behavior by level). The
 * smaller the number, the more it matters: looking where the statue will appear beats talking,
 * talking beats stepping back, and so on. Walking ([LimpingStroll]) only takes his legs, so he can
 * watch the player ([WatchPlayer]) while he walks.
 *
 * Every goal needs the player close by (TheMinerEntity.nearPlayer): with nobody near, nothing runs.
 * He never attacks, flees, breaks or places anything: there are simply no goals for that.
 */
object MinerGoals {

    /** The blocks he stares at, at levels 2-3 (data/dioriteisntuseless/tags/blocks/miner_stares_at.json). */
    val STARES_AT: TagKey<Block> = TagKey.create(Registries.BLOCK, Dioriteisntuseless.id("miner_stares_at"))

    fun addTo(miner: TheMinerEntity, goals: GoalSelector) {
        goals.addGoal(0, LookAtOmen(miner))
        goals.addGoal(1, AttendPlayer(miner))
        goals.addGoal(2, StepBack(miner))
        goals.addGoal(3, StareAtPlayer(miner))
        goals.addGoal(4, StareAtDiorite(miner))
        goals.addGoal(5, LimpingStroll(miner))
        goals.addGoal(6, WatchPlayer(miner))
        goals.addGoal(7, LookAround(miner))
    }

    /** Head turning speeds, in degrees per tick: slow, he is old. Staring is slower still. */
    private const val TURN_SPEED = 10f
    private const val SLOW_TURN_SPEED = 5f

    private fun lookAt(miner: TheMinerEntity, point: Vec3, speed: Float = TURN_SPEED) {
        miner.lookControl.setLookAt(point.x, point.y, point.z, speed, speed)
    }

    private abstract class MinerGoal(protected val miner: TheMinerEntity, vararg flags: Flag) : Goal() {
        init {
            setFlags(EnumSet.noneOf(Flag::class.java).apply { addAll(flags) })
        }

        protected val player: ServerPlayer? get() = miner.nearPlayer

        protected val mood: Mood get() = miner.mood

        protected val now: Int get() = miner.tickCount

        /** The ones that look at something keep looking every tick (LookControl forgets after 2). */
        override fun requiresUpdateEveryTick(): Boolean = true
    }

    /** A few seconds before The "Useful" Owner appears, he turns towards the spot and stays still (OwnerStatue). */
    private class LookAtOmen(miner: TheMinerEntity) : MinerGoal(miner, Flag.MOVE, Flag.LOOK) {
        override fun canUse(): Boolean = miner.omenSpot != null

        override fun start() {
            miner.navigation.stop()
        }

        override fun tick() {
            miner.omenSpot?.let { lookAt(miner, it, SLOW_TURN_SPEED) }
        }
    }

    /** Trading or talking: he stands and looks at them (or at their tool). */
    private class AttendPlayer(miner: TheMinerEntity) : MinerGoal(miner, Flag.MOVE, Flag.LOOK) {
        override fun canUse(): Boolean = miner.isTalking && player != null

        override fun start() {
            miner.navigation.stop()
        }

        override fun tick() {
            player?.let { lookAt(miner, miner.lookPointOn(it)) }
        }
    }

    /** Levels 0-1: if they come closer than [DISTANCE], one step back. Not fleeing: one step, then a pause. */
    private class StepBack(miner: TheMinerEntity) : MinerGoal(miner, Flag.MOVE) {
        private var nextAt = 0
        private var target: Vec3? = null

        override fun canUse(): Boolean {
            val player = player ?: return false
            if (mood != Mood.CALM || miner.isTalking || now < nextAt || miner.distanceToSqr(player) >= DISTANCE * DISTANCE) {
                return false
            }
            target = LandRandomPos.getPosAway(miner, STEP, 1, player.position())
            return target != null
        }

        override fun start() {
            target?.let { miner.navigation.moveTo(it.x, it.y, it.z, SPEED) }
        }

        override fun canContinueToUse(): Boolean = !miner.navigation.isDone && !miner.isTalking

        override fun stop() {
            miner.navigation.stop()
            target = null
            nextAt = now + PAUSE_TICKS
        }

        companion object {
            const val DISTANCE = 2.0
            const val STEP = 2
            const val SPEED = 0.6
            const val PAUSE_TICKS = 40
        }
    }

    /** Levels 2-3: now and then he stands still and stares at them for 10-20 s. */
    private class StareAtPlayer(miner: TheMinerEntity) : MinerGoal(miner, Flag.MOVE, Flag.LOOK) {
        private var nextAt = 0
        private var endAt = 0

        override fun canUse(): Boolean {
            val player = player ?: return false
            return mood == Mood.EAGER && !miner.isTalking && now >= nextAt &&
                miner.random.nextInt(reducedTickDelay(CHANCE_TICKS)) == 0 &&
                miner.distanceToSqr(player) <= RANGE * RANGE && miner.hasLineOfSight(player)
        }

        override fun start() {
            endAt = now + MIN_TICKS + miner.random.nextInt(MAX_TICKS - MIN_TICKS + 1)
            miner.navigation.stop()
        }

        override fun canContinueToUse(): Boolean = now < endAt && player != null && mood == Mood.EAGER && !miner.isTalking

        override fun tick() {
            player?.let { lookAt(miner, miner.lookPointOn(it), SLOW_TURN_SPEED) }
        }

        override fun stop() {
            nextAt = now + PAUSE_MIN_TICKS + miner.random.nextInt(PAUSE_MAX_TICKS - PAUSE_MIN_TICKS + 1)
        }

        companion object {
            const val RANGE = 16.0
            const val CHANCE_TICKS = 400
            const val MIN_TICKS = 10 * 20
            const val MAX_TICKS = 20 * 20
            const val PAUSE_MIN_TICKS = 15 * 20
            const val PAUSE_MAX_TICKS = 30 * 20
        }
    }

    /** Levels 2-3: now and then he stares at a diorite block nearby that he can see, for 5-10 s. */
    private class StareAtDiorite(miner: TheMinerEntity) : MinerGoal(miner, Flag.MOVE, Flag.LOOK) {
        private var nextAt = 0
        private var endAt = 0
        private var target: BlockPos? = null

        override fun canUse(): Boolean {
            if (mood != Mood.EAGER || miner.isTalking || player == null || now < nextAt) return false
            if (miner.random.nextInt(reducedTickDelay(CHANCE_TICKS)) != 0) return false
            target = findDiorite()
            // Nothing in sight: he does not look again for a while.
            if (target == null) nextAt = now + PAUSE_MIN_TICKS
            return target != null
        }

        override fun start() {
            endAt = now + MIN_TICKS + miner.random.nextInt(MAX_TICKS - MIN_TICKS + 1)
            miner.navigation.stop()
        }

        override fun canContinueToUse(): Boolean {
            val target = target ?: return false
            return now < endAt && player != null && mood == Mood.EAGER && !miner.isTalking &&
                miner.level().getBlockState(target).`is`(STARES_AT)
        }

        override fun tick() {
            target?.let { lookAt(miner, Vec3.atCenterOf(it), SLOW_TURN_SPEED) }
        }

        override fun stop() {
            target = null
            nextAt = now + PAUSE_MIN_TICKS + miner.random.nextInt(PAUSE_MAX_TICKS - PAUSE_MIN_TICKS + 1)
        }

        /** A random diorite block within [RANGE] that nothing hides from his eyes. */
        private fun findDiorite(): BlockPos? {
            val level = miner.level()
            val center = miner.blockPosition()
            val found = ArrayList<BlockPos>()
            for (pos in BlockPos.betweenClosed(center.offset(-RANGE, -HEIGHT, -RANGE), center.offset(RANGE, HEIGHT, RANGE))) {
                if (level.getBlockState(pos).`is`(STARES_AT)) found += pos.immutable()
            }
            found.shuffle()
            val eyes = miner.eyePosition
            return found.take(MAX_CHECKS).firstOrNull { pos ->
                val hit = level.clip(ClipContext(eyes, Vec3.atCenterOf(pos), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, miner))
                hit.type == HitResult.Type.BLOCK && (hit as BlockHitResult).blockPos == pos
            }
        }

        companion object {
            const val RANGE = 8
            const val HEIGHT = 4
            const val MAX_CHECKS = 16
            const val CHANCE_TICKS = 500
            const val MIN_TICKS = 5 * 20
            const val MAX_TICKS = 10 * 20
            const val PAUSE_MIN_TICKS = 20 * 20
            const val PAUSE_MAX_TICKS = 40 * 20
        }
    }

    /**
     * Levels 0-3: a slow, limping walk (TheMinerEntity.getSpeed) a few blocks from where he
     * arrived (his restriction), less often from level 2 on. Where he may end up is decided by
     * TheMinerEntity.getWalkTargetValue (never into a house).
     */
    private class LimpingStroll(private val miner: TheMinerEntity) :
        RandomStrollGoal(miner, SPEED, CALM_INTERVAL, false) {

        override fun canUse(): Boolean {
            if (miner.nearPlayer == null || miner.isTalking) return false
            setInterval(
                when (miner.mood) {
                    Mood.CALM -> CALM_INTERVAL
                    Mood.EAGER -> EAGER_INTERVAL
                    Mood.FAREWELL -> return false
                }
            )
            return super.canUse()
        }

        override fun canContinueToUse(): Boolean =
            super.canContinueToUse() && miner.nearPlayer != null && !miner.isTalking && miner.mood != Mood.FAREWELL

        override fun getPosition(): Vec3? = LandRandomPos.getPos(miner, RADIUS, 2)

        companion object {
            const val SPEED = 0.45
            const val RADIUS = 5
            const val CALM_INTERVAL = 100
            const val EAGER_INTERVAL = 400
        }
    }

    /**
     * He follows them with his eyes (their face, or their dioritine tool) when they are close:
     * [CALM_RANGE] at levels 0-1, [EAGER_RANGE] at 2-3. At level 4, always: it is all he does.
     */
    private class WatchPlayer(miner: TheMinerEntity) : MinerGoal(miner, Flag.LOOK) {
        override fun canUse(): Boolean = watches(0.0)

        /** A little farther to stop than to start, so he does not keep glancing away and back at the edge. */
        override fun canContinueToUse(): Boolean = watches(MARGIN)

        private fun watches(margin: Double): Boolean {
            val player = player ?: return false
            val range = when (mood) {
                Mood.CALM -> CALM_RANGE
                Mood.EAGER -> EAGER_RANGE
                Mood.FAREWELL -> return true
            } + margin
            return miner.distanceToSqr(player) <= range * range && miner.hasLineOfSight(player)
        }

        override fun tick() {
            player?.let { lookAt(miner, miner.lookPointOn(it)) }
        }

        companion object {
            const val CALM_RANGE = 6.0
            const val EAGER_RANGE = 10.0
            const val MARGIN = 2.0
        }
    }

    /** Levels 0-1, with nothing better to do: he looks around now and then, like vanilla mobs. */
    private class LookAround(private val miner: TheMinerEntity) : RandomLookAroundGoal(miner) {
        override fun canUse(): Boolean =
            miner.mood == Mood.CALM && miner.nearPlayer != null && !miner.isTalking && super.canUse()

        override fun canContinueToUse(): Boolean =
            miner.mood == Mood.CALM && miner.nearPlayer != null && !miner.isTalking && super.canContinueToUse()
    }
}
