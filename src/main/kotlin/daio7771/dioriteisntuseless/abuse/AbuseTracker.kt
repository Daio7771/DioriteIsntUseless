package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.abuse.morse.MorseBeeper
import daio7771.dioriteisntuseless.abuse.morse.MorsePhrases
import daio7771.dioriteisntuseless.abuse.signal.AbuseSignals
import daio7771.dioriteisntuseless.abuse.signal.NonsenseNameSignal
import daio7771.dioriteisntuseless.abuse.text.SignWords
import daio7771.dioriteisntuseless.config.DiuConfig
import daio7771.dioriteisntuseless.config.ModConfig
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState

/**
 * Cuenta el abuso de cada jugador y sube su nivel.
 *
 * El abuso son los árboles enteros talados con el hacha de dioritina (los cuenta TreeFeller, igual
 * que el límite de árboles del hacha; en creativo no cuentan). Cada nivel pide talar
 * abuseMode.treesPerLevel árboles desde el anterior, sin tiempo mínimo: el mod se harta en cuanto
 * se abusa de él.
 *
 * Todo ocurre en el hilo del servidor.
 */
object AbuseTracker {

    const val TICKS_PER_DAY = 24_000L
    /** Último nivel al que se sube por árboles (el del cartel). */
    const val MAX_LEVEL = DiuConfig.AbuseMode.LEVELS

    /** El final (fases A y B, y después los créditos). Se llega desde el 4 (ver Ending). */
    const val LEVEL_FINAL = MAX_LEVEL + 1

    /** Cada cuánto (en ticks jugados) se comprueba si sube de nivel. */
    private const val CHECK_INTERVAL = 20L

    fun init() {
        MorsePhrases.init()
        SignWords.init()
        Ending.init()
        AbuseStateSync.init()
        ServerLifecycleEvents.SERVER_STARTING.register { AbuseMode.resetSession() }
        // En un solo jugador el juego sigue abierto entre mundos: no se arrastra nada al siguiente.
        ServerLifecycleEvents.SERVER_STOPPED.register { MorseBeeper.clear() }
        ServerTickEvents.END_SERVER_TICK.register(::onServerTick)
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> onJoin(handler.player) }
    }

    private fun onJoin(player: ServerPlayer) {
        if (!AbuseMode.healthy) return
        AbuseMode.guard("player join") {
            StartOver.onJoin(player)
            if (AbuseMode.active) Ending.onJoin(player, AbuseData.get(player.server))
            // Su cliente no recuerda nada de la sesión anterior.
            AbuseStateSync.sync(player)
        }
    }

    /** Lo llama TreeFeller cada vez que [player] tala un árbol entero con el hacha. */
    fun onTreeFelled(player: ServerPlayer) {
        if (player.abilities.instabuild || !AbuseMode.active) return
        AbuseMode.guard("tree counting") {
            val data = AbuseData.get(player.server)
            val state = data.get(player.uuid)
            state.treesFelled++
            state.treesAtLevel++
            AbuseSignals.onTreeFelled(player, state)
            data.setDirty()
        }
    }

    /**
     * Árboles que tiene que talar en su nivel para el siguiente paso (subir de nivel o, en el 4,
     * el final), o null si ya está en el final.
     */
    fun treesNeeded(state: PlayerAbuse, config: DiuConfig.AbuseMode = ModConfig.current.abuseMode): Int? = when {
        state.level < MAX_LEVEL -> config.treesPerLevel[state.level]
        state.level == MAX_LEVEL -> config.treesUntilEnding
        else -> null
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
                FinalSign.tick(player, state)
                Ending.tick(player, state)
            }
            data.setDirty()
        }
    }

    /**
     * Sube un nivel si ha talado en este los árboles que pide. Como mucho un nivel cada vez; la
     * cuenta del nuevo nivel empieza en 0. Del 4 al final no se sube aquí (ver Ending).
     */
    private fun tryLevelUp(state: PlayerAbuse, config: DiuConfig.AbuseMode): Boolean {
        if (state.level >= MAX_LEVEL) return false
        if (state.treesAtLevel < config.treesPerLevel[state.level]) return false
        state.level++
        state.treesAtLevel = 0
        return true
    }

    /**
     * Para el comando de pruebas: pone el nivel directamente y reinicia la cuenta de árboles del nivel.
     * [LEVEL_FINAL] hace ya la fase A (sin esperar a que entre o duerma). Salir del final así
     * no devuelve lo retirado: para eso está "Start over".
     */
    fun setLevel(server: MinecraftServer, player: ServerPlayer, level: Int) {
        val data = AbuseData.get(server)
        val state = data.get(player.uuid)
        val target = level.coerceIn(0, LEVEL_FINAL)
        NonsenseNameSignal.clear(player)
        if (target == LEVEL_FINAL) {
            if (state.level != LEVEL_FINAL) Ending.start(player, state)
        } else {
            state.level = target
            state.treesAtLevel = 0
            // Por debajo del 4, volver al 4 vuelve a sacar EL cartel (el anterior sigue registrado).
            if (target < MAX_LEVEL) state.finalSignPlacedAt = -1
            state.endingDueAt = -1
            state.endingStartedAt = -1
            state.dioriteUseless = false
            AbuseStateSync.sync(player)
            AbuseSignals.schedule(player, state)
        }
        data.setDirty()
    }

    /** Para el comando de pruebas: lleva al jugador a los créditos (hace la fase A si hace falta). */
    fun skipToCredits(server: MinecraftServer, player: ServerPlayer): Boolean {
        if (!AbuseMode.active) return false
        AbuseMode.guard("skip to credits") {
            val data = AbuseData.get(server)
            Ending.skipToCredits(player, data.get(player.uuid))
            data.setDirty()
        }
        return AbuseMode.active
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
    fun forceSignal(server: MinecraftServer, player: ServerPlayer, id: String?): Boolean {
        if (!AbuseMode.active) return false
        var ran = false
        AbuseMode.guard("forced signal") {
            val data = AbuseData.get(server)
            ran = AbuseSignals.forceNow(player, data.get(player.uuid), id)
            data.setDirty()
        }
        return ran
    }

    fun state(server: MinecraftServer, player: ServerPlayer): PlayerAbuse = AbuseData.get(server).get(player.uuid)
}
