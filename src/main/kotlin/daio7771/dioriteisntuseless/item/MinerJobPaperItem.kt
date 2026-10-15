package daio7771.dioriteisntuseless.item

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.abuse.visitor.MinerQuests
import daio7771.dioriteisntuseless.abuse.visitor.MinerState
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

/**
 * The job paper of The Miner (MinerQuests): the favor he asked for and what it pays, in its
 * tooltip. It is only a copy of the job, which lives in the player's MinerState: losing it loses
 * nothing, and he takes every one they carry when he arrives (they go back to him) and hands the
 * current one back after talking. It does not stack and is not in the creative tabs.
 *
 * Its tag keeps what the tooltip shows, as it was when he wrote it: [TAG_JOB] (MinerQuests.Type id),
 * [TAG_AMOUNT], [TAG_IRON] and [TAG_REWARD].
 */
class MinerJobPaperItem(properties: Properties) : Item(properties) {

    override fun appendHoverText(stack: ItemStack, level: Level?, tooltip: MutableList<Component>, flag: TooltipFlag) {
        val tag = stack.tag
        val type = tag?.getString(TAG_JOB)?.let { MinerQuests.Type.byId(it) }
        if (tag == null || type == null) {
            tooltip += Component.translatable("$LANG.blank").withStyle(ChatFormatting.GRAY)
            return
        }
        tooltip += MinerQuests.description(type, tag.getInt(TAG_AMOUNT)).withStyle(ChatFormatting.GRAY)
        tooltip += Component.translatable("$LANG.pays", MinerQuests.reward(tag.getBoolean(TAG_IRON), tag.getInt(TAG_REWARD)))
            .withStyle(ChatFormatting.DARK_GRAY)
    }

    companion object {
        private const val TAG_JOB = "Job"
        private const val TAG_AMOUNT = "Amount"
        private const val TAG_IRON = "PaysIron"
        private const val TAG_REWARD = "Pays"

        private const val LANG = "item.${Dioriteisntuseless.MOD_ID}.miner_job_paper"

        /** A paper with the job [miner] has right now. */
        fun create(item: Item, miner: MinerState): ItemStack = ItemStack(item).apply {
            val tag = orCreateTag
            tag.putString(TAG_JOB, miner.questType)
            tag.putInt(TAG_AMOUNT, miner.questAmount)
            tag.putBoolean(TAG_IRON, miner.questRewardIron)
            tag.putInt(TAG_REWARD, miner.questRewardCount)
        }

        /** Takes every job paper [player] carries (inventory, crafting grid and cursor). Returns how many. */
        fun takeAll(player: Player, item: Item): Int {
            val taken = player.inventory.clearOrCountMatchingItems({ it.`is`(item) }, -1, player.inventoryMenu.craftSlots)
            if (taken > 0) player.containerMenu.broadcastChanges()
            return taken
        }
    }
}
