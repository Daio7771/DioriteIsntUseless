package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.network.MinerLinePacket
import daio7771.dioriteisntuseless.registry.ModEntities
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import kotlin.math.ceil

/**
 * What The Miner says (lang: chat.dioriteisntuseless.miner.*), only to the player he visits: in a
 * bubble above his head, typed letter by letter with his voice, and then copied to their chat
 * ("The Miner: ..."), all of it on their client (MinerLinePacket, MinerBubbles). A line ending in
 * "—" is cut off: text and voice stop dead there. His mood follows their abuse level:
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

    /** His greeting: its lines, and whether it is a warning he cut off (the first line, then his recovery). */
    class Greeting(val lines: List<Component>, val cutOff: Boolean)

    /**
     * The first thing he says in a visit, when they talk to him (one or two lines). At levels 2-3,
     * sometimes ([CUTOFF_CHANCE]) a warning he cannot finish; always one if [mustCutOff] (his first
     * one, which comes with a note: MinerNotes).
     */
    fun greeting(player: ServerPlayer, miner: MinerState, mood: Mood, mustCutOff: Boolean): Greeting = when {
        mood == Mood.CALM -> Greeting(listOf(pick(player, miner, "greeting", CALM_LINES)), false)
        mood == Mood.EAGER && (mustCutOff || player.random.nextFloat() < CUTOFF_CHANCE) -> Greeting(
            listOf(pick(player, miner, "cutoff", CUTOFF_LINES), pick(player, miner, "recovery", RECOVERY_LINES)), true,
        )
        mood == Mood.EAGER -> Greeting(listOf(pick(player, miner, "greeting.eager", EAGER_LINES)), false)
        else -> Greeting(emptyList(), false)
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

    /**
     * How fast his lines are typed, in letters per tick: 30 a second at levels 0-1; slower at
     * level 4. At levels 2-3 it is irregular, with hesitations (MinerBubbles):
     * [EAGER_CHARS_PER_TICK] is about its average.
     */
    const val CALM_CHARS_PER_TICK = 1.5f
    const val EAGER_CHARS_PER_TICK = 0.55f
    const val FAREWELL_CHARS_PER_TICK = 1.0f

    fun charsPerTick(mood: Mood): Float = when (mood) {
        Mood.CALM -> CALM_CHARS_PER_TICK
        Mood.EAGER -> EAGER_CHARS_PER_TICK
        Mood.FAREWELL -> FAREWELL_CHARS_PER_TICK
    }

    /** After a line is typed, this long to read it before the next one. */
    private const val READ_TICKS = 40

    /**
     * [line], said by The Miner ([minerId]) in [mood], only for [player]: in his bubble or, if
     * their client does not have the mod, in their chat. Returns how many ticks to wait before his
     * next line (typing it and a moment to read it; the client also waits for a line to be typed
     * before starting the next one).
     */
    fun say(player: ServerPlayer, minerId: Int, line: Component, mood: Mood): Int {
        if (ServerPlayNetworking.canSend(player, MinerLinePacket.TYPE)) {
            ServerPlayNetworking.send(player, MinerLinePacket(minerId, line, mood))
        } else {
            player.sendSystemMessage(chatLine(line))
        }
        // On a dedicated server this is the English text (Fabric loads the mod's en_us there): close enough.
        return ceil(line.string.length / charsPerTick(mood)).toInt() + READ_TICKS
    }

    /** A line of his as it goes to the chat: "The Miner: ...". */
    fun chatLine(line: Component): Component = Component.translatable("$LANG.says", ModEntities.THE_MINER.description, line)
}
