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
 * Carteles con una palabra (nivel 3 en adelante): "stop", "too much", "why", "enough"...
 *
 * Aparecen cerca de la base del jugador (su punto de reaparición, si está en esta dimensión y
 * cargado; si no, cerca de él), en el suelo, mirando hacia la base y fuera de la vista de todos.
 * No repite la palabra anterior si hay otra.
 */
object WordSignSignal : AbuseSignal {

    private val DISTANCE = 4..16
    private const val VERTICAL_SCAN = 4
    private const val ATTEMPTS = 200

    override val id = "word_sign"
    override val minLevel = 3

    /** En el final (apartado 5.2) solo hay nombres, bloques y Morse. */
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
            // De arriba abajo: el primer hueco de aire con suelo firme debajo.
            for (dy in VERTICAL_SCAN downTo -VERTICAL_SCAN) {
                val pos = BlockPos(x, base.y + dy, z)
                val sign = AbuseSigns.standing(level, pos, faceTowards) ?: continue
                if (!Visibility.hiddenFromEveryone(level, pos) || !AbuseProtection.mayChange(level, pos, player)) break
                val words = SignWords.words
                val options = words.filter { it != state.lastSignWord }.ifEmpty { words }
                val word = options[random.nextInt(options.size)]
                // En la segunda línea, como si alguien lo hubiera escrito deprisa.
                if (AbuseSigns.place(level, pos, sign, listOf(Component.empty(), Component.literal(word)), player)) {
                    state.lastSignWord = word
                    return true
                }
                break
            }
        }
        return false
    }

    /** Su punto de reaparición si está en esta dimensión y cargado; si no, donde está él. */
    private fun base(player: ServerPlayer): BlockPos {
        val respawn = player.respawnPosition
        if (respawn != null && player.respawnDimension == player.level().dimension() && player.serverLevel().isLoaded(respawn)) {
            return respawn
        }
        return player.blockPosition()
    }
}
