package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.abuse.AbuseData
import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import daio7771.dioriteisntuseless.registry.ModItems
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.LightLayer

/**
 * When The Miner visits a player. His visits do not depend on the abuse level:
 *
 * - The first one: the first time they take Diorite Crystals out of a furnace or blast furnace,
 *   at any level. It waits for as long as it takes.
 * - After that, only in the morning, when they get out of a bed after sleeping through the night,
 *   and only if they did the job he gave them (MinerQuests.isDone; without a job, he comes to give
 *   one). At level 4 he comes anyway: to say goodbye. There is no timer: it is only checked when
 *   they wake up.
 * - Not if the bed is where no sunlight reaches (no sky light at all: a cave).
 * - Each visit lasts until he has waited 15 s for them in vain (TheMinerEntity), [STAY_TICKS] at
 *   most; then he limps away and is gone once out of their sight (VisitorEntity.leave).
 *
 * He always arrives out of sight, at a calm moment: if it is not one, or there is no spot for
 * him, he tries again every check, for [MORNING_WINDOW_TICKS] after they wake up (then he does not
 * come that morning). At the ending he does not come.
 *
 * Only used from the server thread.
 */
object MinerVisits {

    /** About 5 minutes. */
    const val STAY_TICKS = 5 * 60 * 20

    /** After they wake up, how long he has to find a calm moment and a spot. */
    private const val MORNING_WINDOW_TICKS = 30 * 20L

    /** Waking up before this time of day (out of 24000) is waking up in the morning. */
    private const val MORNING_END = 2000L

    fun init() {
        EntitySleepEvents.STOP_SLEEPING.register { entity, bed ->
            if (entity is ServerPlayer && AbuseMode.active) AbuseMode.guard("miner morning visit") { onWakeUp(entity, bed) }
        }
    }

    /** From FurnaceResultSlotMixin, on both sides: only the server decides. */
    @JvmStatic
    fun onFurnaceTake(player: Player, stack: ItemStack) {
        if (player !is ServerPlayer || !stack.`is`(ModItems.DIORITE_CRYSTAL) || !AbuseMode.active) return
        AbuseMode.guard("miner first appearance") {
            val data = AbuseData.get(player.server)
            val miner = data.get(player.uuid).miner
            if (miner.met || miner.gone || miner.visitPendingUntil >= 0) return
            miner.visitPendingUntil = MinerState.NO_DEADLINE
            data.setDirty()
        }
    }

    private fun onWakeUp(player: ServerPlayer, bed: BlockPos) {
        val level = player.serverLevel()
        if (level.dayTime % AbuseTracker.TICKS_PER_DAY >= MORNING_END) return
        if (maxOf(level.getBrightness(LightLayer.SKY, bed), level.getBrightness(LightLayer.SKY, bed.above())) == 0) return
        val data = AbuseData.get(player.server)
        val state = data.get(player.uuid)
        val miner = state.miner
        if (!miner.met || miner.gone || state.level >= AbuseTracker.LEVEL_FINAL) return
        val farewell = MinerSpeech.mood(state.level) == MinerSpeech.Mood.FAREWELL
        if (!farewell && miner.questType.isNotEmpty() && !MinerQuests.isDone(player, state)) return
        miner.visitPendingUntil = state.playTicks + MORNING_WINDOW_TICKS
        data.setDirty()
    }

    /** From the AbuseTracker player tick (every few ticks). */
    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        val miner = state.miner
        if (Visitors.current(player.uuid, Visitors.Kind.MINER) == null) MinerQuests.returnPaper(player, state)
        if (miner.visitPendingUntil < 0) return
        if (miner.gone || state.level >= AbuseTracker.LEVEL_FINAL || state.playTicks > miner.visitPendingUntil) {
            miner.visitPendingUntil = -1
            return
        }
        if (Visitors.current(player.uuid, Visitors.Kind.MINER) != null) return
        if (!Visitors.isCalm(player) || Visitors.bring(player, Visitors.Kind.MINER, STAY_TICKS) == null) return
        miner.met = true
        miner.visitPendingUntil = -1
    }

    /** For the testing command: he comes now, as if they had just woken up (the first time, as if crystals were taken). */
    fun testVisitNow(player: ServerPlayer): Boolean {
        if (!AbuseMode.active) return false
        AbuseMode.guard("test miner visit") {
            val data = AbuseData.get(player.server)
            val state = data.get(player.uuid)
            state.miner.gone = false
            state.miner.visitPendingUntil = state.playTicks + MORNING_WINDOW_TICKS
            data.setDirty()
        }
        return AbuseMode.active
    }

}
