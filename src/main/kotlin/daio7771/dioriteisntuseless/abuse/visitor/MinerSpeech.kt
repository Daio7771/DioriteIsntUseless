package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.registry.ModEntities
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * What The Miner says (lang: chat.dioriteisntuseless.miner.*), always in chat and only to the
 * player he visits. His mood follows their abuse level:
 *
 * - [Mood.CALM] (levels 0-1): friendly, he wants more crystals.
 * - [Mood.EAGER] (levels 2-3): far too enthusiastic, odd. Sometimes he starts warning them and cuts
 *   himself off ("You should know that it—"), then shrugs it off. He knows what is out there, but
 *   he cannot tell, and he never says its name.
 * - [Mood.FAREWELL] (level 4): no going back. He says goodbye and never comes back for that player
 *   (MinerState.gone) until "Start over".
 *
 * The number of lines of each kind must match en_us.json.
 */
object MinerSpeech {

    enum class Mood(val suffix: String) {
        CALM(""),
        EAGER(".eager"),
        FAREWELL(".farewell"),
    }

    private const val LANG = "chat.${Dioriteisntuseless.MOD_ID}.miner"

    private const val CALM_LINES = 6
    private const val EAGER_LINES = 7
    private const val CUTOFF_LINES = 5
    private const val RECOVERY_LINES = 3
    const val FAREWELL_LINES = 3

    /** At levels 2-3, how often his greeting is a warning he cannot finish. */
    private const val CUTOFF_CHANCE = 0.35f

    fun mood(level: Int): Mood = when {
        level <= 1 -> Mood.CALM
        level < AbuseTracker.MAX_LEVEL -> Mood.EAGER
        else -> Mood.FAREWELL
    }

    /** The first thing he says in a visit, when they talk to him (one or two lines). */
    fun greeting(player: ServerPlayer, miner: MinerState, mood: Mood): List<Component> = when {
        mood == Mood.CALM -> listOf(pick(player, miner, "greeting", CALM_LINES))
        mood == Mood.EAGER && player.random.nextFloat() < CUTOFF_CHANCE ->
            listOf(pick(player, miner, "cutoff", CUTOFF_LINES), pick(player, miner, "recovery", RECOVERY_LINES))
        mood == Mood.EAGER -> listOf(pick(player, miner, "greeting.eager", EAGER_LINES))
        else -> emptyList()
    }

    /** His goodbye at level 4, in order. */
    fun farewell(): List<Component> = (1..FAREWELL_LINES).map { Component.translatable("$LANG.farewell.$it") }

    /** A random line of [kind], never the same one twice in a row. */
    private fun pick(player: ServerPlayer, miner: MinerState, kind: String, count: Int): Component {
        var index = 1 + player.random.nextInt(count)
        if ("$kind.$index" == miner.lastLine) index = index % count + 1
        miner.lastLine = "$kind.$index"
        return Component.translatable("$LANG.$kind.$index")
    }

    /** A line of his in chat, only for [player]: "<The Miner> ...". */
    fun say(player: ServerPlayer, line: Component) {
        player.sendSystemMessage(Component.translatable("chat.type.text", ModEntities.THE_MINER.description, line))
    }
}
