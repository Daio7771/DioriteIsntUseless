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
 * Counts the abuse of every player and raises their level.
 *
 * Abuse is whole trees felled with the dioritine axe (counted by TreeFeller) and 3x3 strikes with
 * the pickaxe (counted by AreaMiner), the same ones that count towards each tool's limit; in
 * creative they don't count. Each level asks for abuseMode.treesPerLevel trees or
 * abuseMode.pickaxeSteps strikes since the previous one, or a mix (see [stepReached]), with no
 * minimum time: the mod gets fed up as soon as it is abused.
 *
 * Everything happens on the server thread.
 */
object AbuseTracker {

    const val TICKS_PER_DAY = 24_000L
    /** Last level reached through trees and strikes (the one with the sign). */
    const val MAX_LEVEL = DiuConfig.AbuseMode.LEVELS

    /** The ending (phases A and B, then the credits). Reached from level 4 (see Ending). */
    const val LEVEL_FINAL = MAX_LEVEL + 1

    /** How often (in ticks played) the level-up check runs. */
    private const val CHECK_INTERVAL = 20L

    /** [AbuseMode.active] on the previous tick: if it changes, clients are told (the background sound stops or comes back). */
    private var wasActive = false

    fun init() {
        MorsePhrases.init()
        SignWords.init()
        Ending.init()
        AbuseStateSync.init()
        ServerLifecycleEvents.SERVER_STARTING.register {
            AbuseMode.resetSession()
            wasActive = false
        }
        // In single player the game stays open between worlds: nothing carries over to the next one.
        ServerLifecycleEvents.SERVER_STOPPED.register {
            MorseBeeper.clear()
            CaveSounds.clear()
        }
        ServerTickEvents.END_SERVER_TICK.register(::onServerTick)
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> onJoin(handler.player) }
    }

    private fun onJoin(player: ServerPlayer) {
        if (!AbuseMode.healthy) return
        AbuseMode.guard("player join") {
            StartOver.onJoin(player)
            if (AbuseMode.active) Ending.onJoin(player, AbuseData.get(player.server))
            // Their client remembers nothing from the previous session.
            AbuseStateSync.sync(player)
        }
    }

    /** Called by TreeFeller every time [player] fells a whole tree with the axe. */
    fun onTreeFelled(player: ServerPlayer) = countAction(player, "tree counting") {
        it.treesFelled++
        it.treesAtLevel++
    }

    /** Called by AreaMiner every time [player] makes a 3x3 strike with the pickaxe. */
    fun onStrike(player: ServerPlayer) = countAction(player, "strike counting") {
        it.strikes++
        it.strikesAtLevel++
    }

    private inline fun countAction(player: ServerPlayer, what: String, crossinline count: (PlayerAbuse) -> Unit) {
        if (player.abilities.instabuild || !AbuseMode.active) return
        AbuseMode.guard(what) {
            val data = AbuseData.get(player.server)
            val state = data.get(player.uuid)
            count(state)
            AbuseSignals.onAbuseAction(player, state)
            CaveSounds.onAbuseAction(player, state)
            data.setDirty()
        }
    }

    /** What a step asks for: [trees] trees or [strikes] strikes (or a mix). */
    data class Step(val trees: Int, val strikes: Int)

    /**
     * What the next step from the level of [state] asks for (going up a level or, at level 4,
     * the ending), or null if it is already at the ending.
     */
    fun nextStep(state: PlayerAbuse, config: DiuConfig.AbuseMode = ModConfig.current.abuseMode): Step? = when {
        state.level < MAX_LEVEL -> Step(config.treesPerLevel[state.level], config.pickaxeSteps[state.level])
        state.level == MAX_LEVEL -> Step(config.treesUntilEnding, config.pickaxeSteps[MAX_LEVEL])
        else -> null
    }

    /**
     * Progress towards the next step: trees / trees asked for + strikes / strikes asked for.
     * At 1 (100 %) or more, it is due. Null if it is already at the ending.
     */
    fun progress(state: PlayerAbuse, config: DiuConfig.AbuseMode = ModConfig.current.abuseMode): Double? {
        val step = nextStep(state, config) ?: return null
        return state.treesAtLevel.toDouble() / step.trees + state.strikesAtLevel.toDouble() / step.strikes
    }

    /**
     * true if the player has done what the next step asks for: for example, at level 0, 17 trees,
     * 10 strikes or 9 trees and 5 strikes. Computed with integers so that 17 out of 17 is
     * exactly 100 %.
     */
    fun stepReached(state: PlayerAbuse, config: DiuConfig.AbuseMode = ModConfig.current.abuseMode): Boolean {
        val step = nextStep(state, config) ?: return false
        return state.treesAtLevel * step.strikes + state.strikesAtLevel * step.trees >= step.trees.toLong() * step.strikes
    }

    private fun onServerTick(server: MinecraftServer) {
        if (AbuseMode.healthy) AbuseMode.guard("world restoration") { WorldChanges.tick(server) }
        val active = AbuseMode.active
        if (active != wasActive) {
            wasActive = active
            AbuseMode.guard("state sync") { server.playerList.players.forEach(AbuseStateSync::sync) }
        }
        if (!active) {
            // Disabled while running: signals stop right away, beeps too.
            MorseBeeper.clear()
            CaveSounds.clear()
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
                    // At debug level so nothing is spoiled for whoever reads the log.
                    LOGGER.debug("Abuse mode: {} reached level {}.", player.gameProfile.name, state.level)
                    AbuseSignals.schedule(player, state)
                    AbuseStateSync.sync(player)  // their background sound changes level
                }
                AbuseSignals.tick(player, state)
                CaveSounds.tick(player, state)
                FinalSign.tick(player, state)
                Ending.tick(player, state)
            }
            data.setDirty()
        }
    }

    /**
     * Goes up one level if the player has done what this level asks for ([stepReached]). At most
     * one level at a time; the counts of the new level start at 0. Level 4 to the ending is not
     * handled here (see Ending).
     */
    private fun tryLevelUp(state: PlayerAbuse, config: DiuConfig.AbuseMode): Boolean {
        if (state.level >= MAX_LEVEL || !stepReached(state, config)) return false
        state.level++
        state.treesAtLevel = 0
        state.strikesAtLevel = 0
        return true
    }

    /**
     * For the testing command: sets the level directly and resets the counts of the level.
     * [LEVEL_FINAL] runs phase A right away (without waiting for a join or a sleep). Leaving the
     * ending this way does not give back what was taken: that is what "Start over" is for.
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
            state.strikesAtLevel = 0
            // Below 4, going back to 4 brings THE sign out again (the previous one stays recorded).
            if (target < MAX_LEVEL) state.finalSignPlacedAt = -1
            state.endingDueAt = -1
            state.endingStartedAt = -1
            state.endingMessagesSent = 0
            state.dioriteUseless = false
            AbuseStateSync.sync(player)
            AbuseSignals.schedule(player, state)
        }
        data.setDirty()
    }

    /** For the testing command: takes the player to the credits (running phase A if needed). */
    fun skipToCredits(server: MinecraftServer, player: ServerPlayer): Boolean {
        if (!AbuseMode.active) return false
        AbuseMode.guard("skip to credits") {
            val data = AbuseData.get(server)
            Ending.skipToCredits(player, data.get(player.uuid))
            data.setDirty()
        }
        return AbuseMode.active
    }

    /** "Start over" for [player]. Returns false if it could not be done (internal error; see the log). */
    fun startOver(server: MinecraftServer, player: ServerPlayer): Boolean {
        if (!AbuseMode.healthy) return false
        AbuseMode.guard("start over") { StartOver.run(server, player.uuid) }
        return AbuseMode.healthy
    }

    /** For the testing command: a recorded world change, like the ones made by signals. */
    fun testWorldChange(player: ServerPlayer, level: ServerLevel, pos: BlockPos, state: BlockState): Boolean {
        if (!AbuseMode.healthy) return false
        var changed = false
        AbuseMode.guard("test world change") { changed = WorldChanges.change(level, pos, state, player.uuid) }
        return changed
    }

    fun worldChangeCount(server: MinecraftServer, player: ServerPlayer): Int = WorldChanges.get(server).count(player.uuid)

    /** For the testing command: fires a signal now. Returns false if none is possible. */
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
