package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import daio7771.dioriteisntuseless.network.MinerNotePacket
import daio7771.dioriteisntuseless.registry.ModEntities
import daio7771.dioriteisntuseless.registry.ModItems
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * The Miner's jobs: one at a time, paid in emeralds or iron. They count what the player does, not
 * marked items (which would not stack):
 * - Fell N trees with the Dioritine Axe, and make N 3x3 strikes with the Dioritine Pickaxe: the
 *   same counters as the Abuse Mode (PlayerAbuse.treesFelled and strikes), counted from the moment
 *   the job was given. Like those counters, they do not count in creative.
 * - Bring him N Diorite Crystals: handed over when talking to him, if they carry that many.
 *
 * Jobs are given and paid when talking to him (right-click, before his trades open); he says it in
 * chat, only to that player, in the mood of their abuse level (MinerSpeech). At level 4 he only
 * pays: no more jobs. When one is done away from him, a quiet line above the hotbar says so.
 * While his trading screen is open, a note at its top shows the job ([sendNote]).
 *
 * Only used from the server thread.
 */
object MinerQuests {

    enum class Type(val id: String) {
        FELL_TREES("fell_trees"),
        STRIKES("strikes"),
        BRING_CRYSTALS("bring_crystals");

        companion object {
            fun byId(id: String): Type? = entries.firstOrNull { it.id == id }
        }
    }

    /** How much a job asks for and what it pays: [emeralds] or, the other half of the time, [iron] ingots. */
    private class Tier(val amount: Int, val emeralds: Int, val iron: Int)

    private val TIERS = mapOf(
        Type.FELL_TREES to listOf(Tier(3, 2, 5), Tier(5, 3, 8), Tier(8, 5, 12)),
        Type.STRIKES to listOf(Tier(5, 2, 5), Tier(10, 3, 8), Tier(15, 5, 12)),
        // Better than selling them to him, even at the best price (12 per emerald).
        Type.BRING_CRYSTALS to listOf(Tier(32, 3, 8), Tier(64, 6, 16)),
    )

    private const val LANG = "chat.${Dioriteisntuseless.MOD_ID}.miner"
    private const val NOTE = "note.${Dioriteisntuseless.MOD_ID}.miner"

    /**
     * Talking to him (right-click). Pays a finished job (right away) and gives a new one; if the
     * current one is not finished, reminds them of it, only on the first talk of each visit
     * ([firstTalk]). Returns what he says about it, for him to say in order.
     */
    fun onTalk(player: ServerPlayer, state: PlayerAbuse, firstTalk: Boolean, mood: MinerSpeech.Mood): List<Component> {
        val lines = ArrayList<Component>()
        val miner = state.miner
        val type = Type.byId(miner.questType)
        if (type != null && tryFinish(player, state, type)) {
            lines += Component.translatable("$LANG.quest.done${mood.suffix}", reward(miner))
            pay(player, miner)
            miner.clearQuest()
        }
        if (mood == MinerSpeech.Mood.FAREWELL) return lines
        val current = Type.byId(miner.questType)
        if (current == null) {
            assign(player, state)
            lines += Component.translatable("$LANG.quest.new${mood.suffix}", description(miner), reward(miner))
        } else if (firstTalk) {
            lines += Component.translatable(
                "$LANG.quest.reminder${mood.suffix}", description(miner), progress(player, state, current), miner.questAmount,
            )
        }
        return lines
    }

    /** From AbuseTracker after every tree or strike: if it finishes the job, a quiet line says so. */
    fun onAbuseAction(player: ServerPlayer, state: PlayerAbuse) {
        val type = Type.byId(state.miner.questType) ?: return
        // Exactly at the amount: only the action that finishes it, not every one after it.
        if (type == Type.BRING_CRYSTALS || counter(state, type) - state.miner.questStart != state.miner.questAmount.toLong()) return
        player.displayClientMessage(
            Component.translatable("$LANG.quest.finished", description(state.miner), ModEntities.THE_MINER.description)
                .withStyle(ChatFormatting.GRAY),
            true,
        )
    }

    /**
     * Right before his trading screen opens: the note at its top (MinerNotePacket), with the job
     * they have from him or, if none, his phrase of the level. Not at level 4 (no trading there),
     * nor to clients without the mod.
     */
    fun sendNote(player: ServerPlayer, state: PlayerAbuse, mood: MinerSpeech.Mood) {
        if (mood == MinerSpeech.Mood.FAREWELL || !ServerPlayNetworking.canSend(player, MinerNotePacket.TYPE)) return
        val miner = state.miner
        val type = Type.byId(miner.questType)
        val note = if (type == null) {
            MinerNotePacket(Component.empty(), Component.translatable("$NOTE.wants${mood.suffix}"), 0, 0, false, Component.empty())
        } else {
            MinerNotePacket(
                Component.translatable("$NOTE.job${mood.suffix}"), description(miner), miner.questAmount,
                progress(player, state, type).toInt(), type == Type.BRING_CRYSTALS, reward(miner),
            )
        }
        ServerPlayNetworking.send(player, note)
    }

    /** A new random job, never the same kind twice in a row. Also used by the testing command. */
    fun assign(player: ServerPlayer, state: PlayerAbuse) {
        val miner = state.miner
        val types = Type.entries.filter { it.id != miner.lastQuestType }
        val type = types[player.random.nextInt(types.size)]
        val tier = TIERS.getValue(type).let { it[player.random.nextInt(it.size)] }
        val iron = player.random.nextBoolean()
        miner.questType = type.id
        miner.lastQuestType = type.id
        miner.questAmount = tier.amount
        miner.questStart = counter(state, type)
        miner.questRewardIron = iron
        miner.questRewardCount = if (iron) tier.iron else tier.emeralds
    }

    /** What has been done for the job so far. For crystals, how many they carry right now. */
    fun progress(player: ServerPlayer, state: PlayerAbuse, type: Type): Long = when (type) {
        Type.BRING_CRYSTALS -> countCrystals(player, 0).toLong()
        else -> (counter(state, type) - state.miner.questStart).coerceIn(0, state.miner.questAmount.toLong())
    }.coerceAtMost(state.miner.questAmount.toLong())

    /** "Fell 3 trees with your Dioritine Axe". */
    fun description(miner: MinerState): Component = Component.translatable("quest.${Dioriteisntuseless.MOD_ID}.${miner.questType}", miner.questAmount)

    private fun reward(miner: MinerState): Component =
        Component.translatable("$LANG.reward.${if (miner.questRewardIron) "iron" else "emerald"}", miner.questRewardCount)

    private fun counter(state: PlayerAbuse, type: Type): Long = when (type) {
        Type.FELL_TREES -> state.treesFelled
        Type.STRIKES -> state.strikes
        Type.BRING_CRYSTALS -> 0
    }

    private fun tryFinish(player: ServerPlayer, state: PlayerAbuse, type: Type): Boolean {
        val amount = state.miner.questAmount
        if (type != Type.BRING_CRYSTALS) return progress(player, state, type) >= amount
        if (countCrystals(player, 0) < amount) return false
        return countCrystals(player, amount) == amount
    }

    /**
     * With [take] 0, counts the crystals they carry (inventory, crafting grid and cursor);
     * otherwise takes up to [take] of them and returns how many were taken.
     */
    private fun countCrystals(player: ServerPlayer, take: Int): Int {
        val taken = player.inventory.clearOrCountMatchingItems({ it.`is`(ModItems.DIORITE_CRYSTAL) }, take, player.inventoryMenu.craftSlots)
        if (take > 0) player.containerMenu.broadcastChanges()
        return taken
    }

    /** Into their inventory; whatever does not fit drops at their feet. */
    private fun pay(player: ServerPlayer, miner: MinerState) {
        val item = if (miner.questRewardIron) Items.IRON_INGOT else Items.EMERALD
        player.inventory.placeItemBackInInventory(ItemStack(item, miner.questRewardCount))
    }
}
