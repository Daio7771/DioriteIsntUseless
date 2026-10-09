package daio7771.dioriteisntuseless.abuse.signal

import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import daio7771.dioriteisntuseless.abuse.text.SignWords
import daio7771.dioriteisntuseless.abuse.world.AbuseProtection
import daio7771.dioriteisntuseless.abuse.world.AbuseSigns
import daio7771.dioriteisntuseless.abuse.world.Visibility
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/**
 * Signs with one word (level 3 and up): "stop", "too much", "why", "enough"...
 *
 * They appear near the player's base (their respawn point, if it is in this dimension and loaded;
 * otherwise, near them), on the ground, facing the base and out of everyone's sight.
 * Never repeats the previous word if there is another one.
 */
object WordSignSignal : AbuseSignal {

    private val DISTANCE = 4..16
    private const val VERTICAL_SCAN = 4
    private const val ATTEMPTS = 200

    override val id = "word_sign"
    override val minLevel = 3

    /** At the ending (section 5.2) there are only names, blocks and Morse. */
    override val maxLevel = 4

    override fun canRun(player: ServerPlayer, state: PlayerAbuse): Boolean = SignWords.words.isNotEmpty()

    override fun run(player: ServerPlayer, state: PlayerAbuse): Boolean {
        val level = player.serverLevel()
        val random = player.random
        val base = base(player)
        val faceTowards = Vec3.atBottomCenterOf(base)
        for (attempt in 0 until ATTEMPTS) {
            val angle = random.nextDouble() * 2 * Math.PI
            val distance = DISTANCE.first + random.nextDouble() * (DISTANCE.last - DISTANCE.first)
            val x = base.x + Math.round(Math.cos(angle) * distance).toInt()
            val z = base.z + Math.round(Math.sin(angle) * distance).toInt()
            // Top to bottom: the first air gap with solid ground below.
            for (dy in VERTICAL_SCAN downTo -VERTICAL_SCAN) {
                val pos = BlockPos(x, base.y + dy, z)
                val sign = AbuseSigns.standing(level, pos, faceTowards) ?: continue
                if (!Visibility.hiddenFromEveryone(level, pos) || !AbuseProtection.mayChange(level, pos, player)) break
                val words = SignWords.words
                val options = words.filter { it != state.lastSignWord }.ifEmpty { words }
                val word = options[random.nextInt(options.size)]
                // On the second line, as if someone had written it in a hurry.
                if (AbuseSigns.place(level, pos, sign, listOf(Component.empty(), Component.literal(word)), player)) {
                    state.lastSignWord = word
                    return true
                }
                break
            }
        }
        return false
    }

    /** Their respawn point if it is in this dimension and loaded; otherwise, where they are. */
    private fun base(player: ServerPlayer): BlockPos {
        val respawn = player.respawnPosition
        if (respawn != null && player.respawnDimension == player.level().dimension() && player.serverLevel().isLoaded(respawn)) {
            return respawn
        }
        return player.blockPosition()
    }
}
