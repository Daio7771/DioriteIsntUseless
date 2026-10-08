package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.abuse.morse.MorseBeeper
import daio7771.dioriteisntuseless.abuse.morse.MorsePhrases
import daio7771.dioriteisntuseless.abuse.signal.AbuseSignals
import daio7771.dioriteisntuseless.abuse.signal.NonsenseNameSignal
import daio7771.dioriteisntuseless.config.DiuConfig
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.registry.ModItems
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stat
import net.minecraft.stats.Stats
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/**
 * Cuenta el abuso de cada jugador y sube su nivel (HORROR_DESIGN.md, apartado 3).
 *
 * Los contadores salen de las estadísticas vanilla en el momento en que se conceden (ver
 * ServerPlayerMixin): "bloque minado" y "objeto fabricado" ya cubren todas las formas normales de
 * minar o fabricar, incluidas la talada del hacha y el Shift+clic en la mesa, y siguen las mismas
 * reglas (en creativo no cuentan). Se guardan contadores propios en vez de leer las estadísticas
 * porque "Start over" tiene que poder ponerlos a 0 sin tocar las estadísticas del jugador.
 *
 * Todo ocurre en el hilo del servidor.
 */
object AbuseTracker {

    const val TICKS_PER_DAY = 24_000L
    const val MAX_LEVEL = DiuConfig.AbuseMode.LEVELS

    /** Cada cuánto (en ticks jugados) se comprueba si sube de nivel. */
    private const val CHECK_INTERVAL = 20L

    fun init() {
        MorsePhrases.init()
        ServerLifecycleEvents.SERVER_STARTING.register { AbuseMode.resetSession() }
        // En un solo jugador el juego sigue abierto entre mundos: no se arrastra nada al siguiente.
        ServerLifecycleEvents.SERVER_STOPPED.register { MorseBeeper.clear() }
        ServerTickEvents.END_SERVER_TICK.register(::onServerTick)
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            if (AbuseMode.healthy) AbuseMode.guard("player join") { StartOver.onJoin(handler.player) }
        }
    }

    /** Lo llama ServerPlayerMixin cada vez que el jugador recibe una estadística. */
    @JvmStatic
    fun onStatAwarded(player: ServerPlayer, stat: Stat<*>, amount: Int) {
        if (amount <= 0 || !AbuseMode.active) return
        AbuseMode.guard("stat counting") {
            val type = stat.type
            val value = stat.value
            if (type == Stats.BLOCK_MINED && value is Block) {
                onBlockMined(player, value, amount)
            } else if (type == Stats.ITEM_CRAFTED && value == ModItems.DIORITINE_AXE) {
                data(player).get(player.uuid).axesCrafted += amount
            }
        }
    }

    private fun onBlockMined(player: ServerPlayer, block: Block, amount: Int) {
        when {
            block == Blocks.DIORITE -> data(player).get(player.uuid).dioriteMined += amount
            block == Blocks.STONE || block == Blocks.DEEPSLATE -> data(player).get(player.uuid).stoneMined += amount
            // Talado con el hacha: el jugador la lleva en la mano mientras se rompe el tronco.
            block.defaultBlockState().`is`(BlockTags.LOGS) && player.mainHandItem.`is`(ModItems.DIORITINE_AXE) ->
                data(player).get(player.uuid).logsFelled += amount
        }
    }

    private fun onServerTick(server: MinecraftServer) {
        if (AbuseMode.healthy) AbuseMode.guard("world restoration") { WorldChanges.tick(server) }
        if (!AbuseMode.active) {
            // Desactivado en caliente: las señales se detienen al momento, también los pitidos.
            MorseBeeper.clear()
            return
        }
        AbuseMode.guard("server tick") {
            MorseBeeper.tick(server)
            val players = server.playerList.players
            if (players.isEmpty()) return
            val data = AbuseData.get(server)
            val config = ModConfig.current.abuseMode
            for (player in players) {
                val state = data.get(player.uuid)
                state.playTicks++
                if (state.playTicks % CHECK_INTERVAL != 0L) continue
                if (tryLevelUp(state, config)) {
                    // En debug para no destripar nada a quien lea el log.
                    LOGGER.debug("Abuse mode: {} reached level {}.", player.gameProfile.name, state.level)
                    AbuseSignals.schedule(player, state)
                }
                AbuseSignals.tick(player, state)
            }
            data.setDirty()
        }
    }

    /**
     * Sube un nivel si se cumplen las tres condiciones: puntuación, más diorita que piedra minada
     * y tiempo mínimo desde el nivel anterior. Como mucho un nivel cada vez.
     */
    private fun tryLevelUp(state: PlayerAbuse, config: DiuConfig.AbuseMode): Boolean {
        val index = state.level
        if (index >= MAX_LEVEL) return false
        if (state.score < config.levelThresholds[index]) return false
        if (state.dioriteMined <= state.stoneMined) return false
        if (state.playTicks - state.levelReachedAt < config.minDaysBetweenLevels[index] * TICKS_PER_DAY) return false
        state.level++
        state.levelReachedAt = state.playTicks
        return true
    }

    /** Para el comando de pruebas: pone el nivel directamente y reinicia la cuenta de días. */
    fun setLevel(server: MinecraftServer, player: ServerPlayer, level: Int) {
        val data = AbuseData.get(server)
        val state = data.get(player.uuid)
        state.level = level.coerceIn(0, MAX_LEVEL)
        state.levelReachedAt = state.playTicks
        AbuseSignals.schedule(player, state)
        NonsenseNameSignal.clear(player)
        data.setDirty()
    }

    /** "Start over" de [player]. Devuelve false si no se ha podido (error interno; ver el log). */
    fun startOver(server: MinecraftServer, player: ServerPlayer): Boolean {
        if (!AbuseMode.healthy) return false
        AbuseMode.guard("start over") { StartOver.run(server, player.uuid) }
        return AbuseMode.healthy
    }

    /** Para el comando de pruebas: un cambio en el mundo registrado, como los de las señales. */
    fun testWorldChange(player: ServerPlayer, level: ServerLevel, pos: BlockPos, state: BlockState): Boolean {
        if (!AbuseMode.healthy) return false
        var changed = false
        AbuseMode.guard("test world change") { changed = WorldChanges.change(level, pos, state, player.uuid) }
        return changed
    }

    fun worldChangeCount(server: MinecraftServer, player: ServerPlayer): Int = WorldChanges.get(server).count(player.uuid)

    /** Para el comando de pruebas: lanza una señal ya. Devuelve false si no hay ninguna posible. */
    fun forceSignal(server: MinecraftServer, player: ServerPlayer): Boolean {
        if (!AbuseMode.active) return false
        var ran = false
        AbuseMode.guard("forced signal") {
            val data = AbuseData.get(server)
            ran = AbuseSignals.forceNow(player, data.get(player.uuid))
            data.setDirty()
        }
        return ran
    }

    fun state(server: MinecraftServer, player: ServerPlayer): PlayerAbuse = AbuseData.get(server).get(player.uuid)

    private fun data(player: ServerPlayer): AbuseData = AbuseData.get(player.server).also { it.setDirty() }
}
