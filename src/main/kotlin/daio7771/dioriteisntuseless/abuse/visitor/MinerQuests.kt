package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import daio7771.dioriteisntuseless.item.MinerJobPaperItem
import daio7771.dioriteisntuseless.registry.ModEntities
import daio7771.dioriteisntuseless.registry.ModItems
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * The Miner's favors (jobs): one at a time, paid in emeralds or iron. They are all he asks for. They count what the player does, not
 * marked items (which would not stack):
 * - Fell N trees with the Dioritine Axe, and make N 3x3 strikes with the Dioritine Pickaxe: the
 *   same counters as the Abuse Mode (PlayerAbuse.treesFelled and strikes), counted from the moment
 *   the job was given. Like those counters, they do not count in creative.
 * - Bring him N Diorite Crystals: handed over when talking to him, if they carry that many.
 *
 * Jobs are given and paid when talking to him (right-click); he says it in his bubble, only to that
 * player, in the mood of their abuse level (MinerSpeech). At level 4 he only pays: no more jobs.
 * When one is done away from him, a quiet line above the hotbar says so.
 *
 * Each job comes on a paper (MinerJobPaperItem): the first one together with his instructions book,
 * the next ones when he pays the one before. When he arrives he takes back every paper they carry
 * ([takePapers]) and, after talking, hands them the current one ([handPaper]). If he leaves before
 * that, it is back in their pocket all the same ([returnPaper]).
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
        // Crystals take smelting: they pay the most.
        Type.BRING_CRYSTALS to listOf(Tier(32, 3, 8), Tier(64, 6, 16)),
    )

    private const val LANG = "chat.${Dioriteisntuseless.MOD_ID}.miner"

    /**
     * Talking to him (right-click), every time. Pays a finished job (right away) and gives a new
     * one; if the current one is not finished, reminds them of it and how far along it is. Returns
     * what he says about it, for him to say in order.
     */
    fun onTalk(player: ServerPlayer, state: PlayerAbuse, mood: MinerSpeech.Mood): List<Component> {
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
        } else {
            lines += Component.translatable(
                "$LANG.quest.reminder${mood.suffix}", description(miner), progress(player, state, current), miner.questAmount,
            )
        }
        return lines
    }

    /**
     * From AbuseTracker after every tree or strike: if it finishes the job, a quiet line says so.
     */
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

    /** When he arrives: he takes back every job paper they carry; the current job's is in his hands now. */
    fun takePapers(player: ServerPlayer, state: PlayerAbuse) {
        val taken = MinerJobPaperItem.takeAll(player, ModItems.MINER_JOB_PAPER)
        if (Type.byId(state.miner.questType) != null) state.miner.paperHeld = true
        if (taken > 0) player.displayClientMessage(Component.translatable("$LANG.paper_taken").withStyle(ChatFormatting.GRAY), true)
    }

    /** He hands them the paper of their job, if he has it: into their inventory or at his feet. */
    fun handPaper(player: ServerPlayer, from: Entity, state: PlayerAbuse) {
        val miner = state.miner
        if (!miner.paperHeld) return
        miner.paperHeld = false
        if (Type.byId(miner.questType) == null) return
        val inPocket = MinerNotes.handTo(player, from, paper(miner))
        player.displayClientMessage(
            Component.translatable("$LANG.paper_${if (inPocket) "given" else "dropped"}").withStyle(ChatFormatting.GRAY), true,
        )
    }

    /** On his first visit, together with his instructions book: the first job, tossed at their feet. */
    fun tossFirstPaper(player: ServerPlayer, from: LivingEntity, state: PlayerAbuse) {
        if (Type.byId(state.miner.questType) == null) assign(player, state)
        state.miner.paperHeld = false
        MinerNotes.toss(player, from, paper(state.miner))
    }

    /**
     * From MinerVisits, while he is not with them: he left with their paper in his hands (they did
     * not talk to him, they disconnected...). It goes back into their inventory, quietly.
     */
    fun returnPaper(player: ServerPlayer, state: PlayerAbuse) {
        val miner = state.miner
        if (!miner.paperHeld) return
        miner.paperHeld = false
        if (Type.byId(miner.questType) != null) player.inventory.placeItemBackInInventory(paper(miner))
    }

    private fun paper(miner: MinerState): ItemStack = MinerJobPaperItem.create(ModItems.MINER_JOB_PAPER, miner)

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
        // He writes it down: the paper is in his hands until he gives it to them.
        miner.paperHeld = true
    }

    /** The job they have from him is done (the crystals: they carry them right now). False if they have none. */
    fun isDone(player: ServerPlayer, state: PlayerAbuse): Boolean {
        val type = Type.byId(state.miner.questType) ?: return false
        return progress(player, state, type) >= state.miner.questAmount
    }

    /** What has been done for the job so far. For crystals, how many they carry right now. */
    fun progress(player: ServerPlayer, state: PlayerAbuse, type: Type): Long = when (type) {
        Type.BRING_CRYSTALS -> countCrystals(player, 0).toLong()
        else -> (counter(state, type) - state.miner.questStart).coerceIn(0, state.miner.questAmount.toLong())
    }.coerceAtMost(state.miner.questAmount.toLong())

    /** "Fell 3 trees with your Dioritine Axe". */
    fun description(miner: MinerState): Component = Component.translatable("quest.${Dioriteisntuseless.MOD_ID}.${miner.questType}", miner.questAmount)

    fun description(type: Type, amount: Int): MutableComponent = Component.translatable("quest.${Dioriteisntuseless.MOD_ID}.${type.id}", amount)

    private fun reward(miner: MinerState): Component = reward(miner.questRewardIron, miner.questRewardCount)

    /** "5 emeralds", "8 iron ingots". */
    fun reward(iron: Boolean, count: Int): Component = Component.translatable("$LANG.reward.${if (iron) "iron" else "emerald"}", count)

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
