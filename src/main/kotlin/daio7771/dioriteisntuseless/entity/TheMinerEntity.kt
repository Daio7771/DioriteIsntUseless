package daio7771.dioriteisntuseless.entity

import daio7771.dioriteisntuseless.abuse.AbuseData
import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.visitor.MinerQuests
import daio7771.dioriteisntuseless.abuse.visitor.MinerSpeech
import daio7771.dioriteisntuseless.abuse.visitor.MinerTrades
import daio7771.dioriteisntuseless.abuse.world.Visibility
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.Merchant
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.item.trading.MerchantOffers
import net.minecraft.world.level.Level

/**
 * The Miner: an old miner who buys Diorite Crystals (MinerTrades). He stands where he appeared and
 * follows the player with his eyes. Everything he remembers of the player is in their MinerState,
 * not here.
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

    override val busy: Boolean get() = tradingPlayer != null || lines.isNotEmpty()

    override fun visitTick(player: ServerPlayer) {
        turnHeadTowards(player, HEAD_TURN_DEGREES)
        if (!sayingGoodbye && distanceTo(player) <= GOODBYE_DISTANCE && Visibility.mightSee(player, boundingBox) &&
            mood(player) == MinerSpeech.Mood.FAREWELL
        ) {
            sayGoodbye()
        }
        tickLines(player)
    }

    override fun interact(player: Player, hand: InteractionHand): InteractionResult {
        // On the client he only exists for the player he visits.
        if (level().isClientSide) return InteractionResult.SUCCESS
        if (player !is ServerPlayer || player.uuid != visitedPlayer) return InteractionResult.PASS
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
        offers = MinerTrades.offersFor(state.level)
        tradingPlayer = player
        openTradingScreen(player, displayName, 0)
        // If the screen could not open, he is not left waiting for nobody.
        if (player.containerMenu === player.inventoryMenu) tradingPlayer = null
    }

    private fun mood(player: ServerPlayer): MinerSpeech.Mood =
        MinerSpeech.mood(AbuseData.get(player.server).get(player.uuid).level)

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

    private companion object {
        const val HEAD_TURN_DEGREES = 10f
        const val LINE_GAP_TICKS = 40

        /** At level 4 he starts his goodbye when they see him this close. */
        const val GOODBYE_DISTANCE = 8f
    }
}
