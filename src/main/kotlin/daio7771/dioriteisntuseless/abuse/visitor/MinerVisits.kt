package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.abuse.AbuseData
import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * When The Miner visits a player. His visits do not depend on the abuse level, only how often
 * they happen:
 *
 * - The first one: the first time they take Diorite Crystals out of a furnace or blast furnace,
 *   at any level.
 * - After that he comes back on his own, every [visitInterval] ticks played (2-3 game days at
 *   levels 0-1, 1-2 at the higher ones), counted from his last arrival.
 * - Each visit lasts [STAY_TICKS] (or until they close his trades), then he leaves without being
 *   seen leaving (VisitorEntity.leave).
 *
 * He always arrives out of sight, at a calm moment: if it is not one, or there is no spot for
 * him, he tries again a few seconds later. At the ending he does not come.
 *
 * Only used from the server thread.
 */
object MinerVisits {

    /** About 5 minutes. */
    const val STAY_TICKS = 5 * 60 * 20

    /** If he cannot arrive right now, he tries again after this long. */
    private const val RETRY_TICKS = 5 * 20L

    /** From FurnaceResultSlotMixin, on both sides: only the server decides. */
    @JvmStatic
    fun onFurnaceTake(player: Player, stack: ItemStack) {
        if (player !is ServerPlayer || !stack.`is`(ModItems.DIORITE_CRYSTAL) || !AbuseMode.active) return
        AbuseMode.guard("miner first appearance") {
            val data = AbuseData.get(player.server)
            val miner = data.get(player.uuid).miner
            if (miner.met || miner.gone || miner.nextVisitAt >= 0) return
            miner.nextVisitAt = data.get(player.uuid).playTicks  // right away (on the next check)
            data.setDirty()
        }
    }

    /** From the AbuseTracker player tick (every few ticks). */
    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        val miner = state.miner
        if (miner.gone || miner.nextVisitAt < 0 || state.playTicks < miner.nextVisitAt) return
        if (state.level >= AbuseTracker.LEVEL_FINAL || Visitors.current(player.uuid, Visitors.Kind.MINER) != null) return
        if (!Visitors.isCalm(player) || Visitors.bring(player, Visitors.Kind.MINER, STAY_TICKS) == null) {
            miner.nextVisitAt = state.playTicks + RETRY_TICKS
            return
        }
        miner.met = true
        miner.nextVisitAt = state.playTicks + visitInterval(player, state.level)
    }

    /** Ticks until his next visit: 2-3 game days at levels 0-1, 1-2 from level 2 on. */
    private fun visitInterval(player: ServerPlayer, level: Int): Long {
        val days = if (level <= 1) 2.0 + player.random.nextDouble() else 1.0 + player.random.nextDouble()
        return (days * AbuseTracker.TICKS_PER_DAY).toLong()
    }

    /** For the testing command: his next visit is due now (the first one too, as if crystals were taken). */
    fun testVisitNow(player: ServerPlayer): Boolean {
        if (!AbuseMode.active) return false
        AbuseMode.guard("test miner visit") {
            val data = AbuseData.get(player.server)
            val state = data.get(player.uuid)
            state.miner.gone = false
            state.miner.nextVisitAt = state.playTicks
            data.setDirty()
        }
        return AbuseMode.active
    }
}
