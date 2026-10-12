package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.abuse.visitor.Visitors
import daio7771.dioriteisntuseless.entity.VisitorEntity
import daio7771.dioriteisntuseless.network.FreezePacket
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * The last moment before the credits, when phase B of the ending is over (it replaces opening the
 * credits straight away):
 *
 * 1. It waits for a safe moment: on the ground, no hostile mobs nearby, not in lava or water, not
 *    falling, no container open (Visitors.isCalm and a bit more).
 * 2. "USELESS" in red in their chat, line by line, [USELESS_LINES] times. Only for them.
 * 3. Meanwhile they cannot move (FreezePacket: their movement keys do nothing, they can still look
 *    around) and cannot be hurt.
 * 4. The "Useful" Owner appears in front of them and stands there, doing nothing.
 * 5. A few seconds later the credits open (the ones that already exist, AbuseStateSync).
 *
 * Golden rule, nobody is ever trapped: all of it lasts [FreezePacket.MAX_TICKS] (15 s) at most.
 * Whatever happens (an error, the Abuse Mode disabled, a disconnect), [safetyTick] gives control
 * back, even if the credits never come; the client also unfreezes on its own when the time is up.
 * The freeze and the protection only exist in memory: nothing about them is ever saved.
 *
 * If it is interrupted before the credits, it happens again at the next safe moment.
 * Only used from the server thread.
 */
object Finale {

    private const val USELESS_LINES = 8
    private const val LINE_GAP_TICKS = 10

    /** When it appears: right after the last USELESS. */
    private const val OWNER_AT = USELESS_LINES * LINE_GAP_TICKS + 10

    /** It stands there for 5 seconds before the credits. */
    private const val CREDITS_AT = OWNER_AT + 5 * 20

    /** Control comes back once the credits are on screen; long before the 15 s limit. */
    private const val RELEASE_AT = CREDITS_AT + 20

    /**
     * If no spot in front of them turns up for this long (always in tight places), it goes ahead
     * without The "Useful" Owner: better that than an ending that never comes.
     */
    private const val NO_SPOT_PATIENCE_TICKS = 2 * 60 * 20L

    private class Run(val uuid: UUID, val startTick: Int) {
        var linesSent = 0
        var owner: VisitorEntity? = null
        var ownerTried = false
        var creditsShown = false
    }

    private val runs = HashMap<UUID, Run>()

    /** PlayerAbuse.playTicks since which there has been no spot in front of them at safe moments. */
    private val noSpotSince = HashMap<UUID, Long>()

    fun init() {
        // While it runs, nothing hurts them. Only in memory: it cannot outlive the 15 s.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ -> entity !is ServerPlayer || entity.uuid !in runs }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, server -> stop(server, handler.player.uuid) }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            runs.clear()
            noSpotSince.clear()
        }
    }

    fun isRunning(uuid: UUID): Boolean = uuid in runs

    /** From Ending.tick when the credits are due and this has not happened yet. Inside AbuseMode.guard. */
    fun tryStart(player: ServerPlayer, state: PlayerAbuse) {
        if (player.uuid in runs || !isSafe(player)) return
        // There must be somewhere for it to stand in front of them (or they have waited long enough).
        if (Visitors.findSpot(player, Visitors.Kind.OWNER_IN_FRONT) == null) {
            val since = noSpotSince.getOrPut(player.uuid) { state.playTicks }
            if (state.playTicks - since < NO_SPOT_PATIENCE_TICKS) return
        }
        noSpotSince.remove(player.uuid)
        runs[player.uuid] = Run(player.uuid, player.server.tickCount)
        if (ServerPlayNetworking.canSend(player, FreezePacket.TYPE)) {
            ServerPlayNetworking.send(player, FreezePacket(FreezePacket.MAX_TICKS))
        }
        CaveSounds.playFinal(player)
    }

    private fun isSafe(player: ServerPlayer): Boolean =
        Visitors.isCalm(player) && player.fallDistance == 0f && player.containerMenu === player.inventoryMenu

    /**
     * Every server tick, whether the Abuse Mode is active or not: moves each run forward and,
     * above all, makes sure it ends. Each run on its own, so one failing cannot keep another going.
     */
    fun safetyTick(server: MinecraftServer) {
        if (runs.isEmpty()) return
        for (run in runs.values.toList()) {
            try {
                val elapsed = server.tickCount - run.startTick
                val player = server.playerList.getPlayer(run.uuid)
                if (player == null || !AbuseMode.active || elapsed !in 0 until FreezePacket.MAX_TICKS) {
                    stop(server, run.uuid)
                    continue
                }
                AbuseMode.guard("ending finale") { step(player, run, elapsed) }
                if (!AbuseMode.active || elapsed >= RELEASE_AT) stop(server, run.uuid)
            } catch (e: Throwable) {
                if (e is VirtualMachineError) throw e
                LOGGER.error("Abuse mode: error in the ending finale; giving control back.", e)
                try {
                    stop(server, run.uuid)
                } finally {
                    runs.remove(run.uuid)
                }
            }
        }
    }

    private fun step(player: ServerPlayer, run: Run, elapsed: Int) {
        while (run.linesSent < USELESS_LINES && elapsed >= run.linesSent * LINE_GAP_TICKS) {
            player.sendSystemMessage(Component.literal("USELESS").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
            run.linesSent++
        }
        if (!run.ownerTried && elapsed >= OWNER_AT) {
            run.ownerTried = true
            run.owner = Visitors.bring(player, Visitors.Kind.OWNER_IN_FRONT, -1)
        }
        if (!run.creditsShown && elapsed >= CREDITS_AT) {
            run.creditsShown = true
            val data = AbuseData.get(player.server)
            val state = data.get(player.uuid)
            state.finaleDone = true
            state.creditsAnnounced = true
            data.setDirty()
            AbuseStateSync.sync(player)  // their client opens the credits
        }
    }

    /**
     * Gives [uuid] back their control and takes The "Useful" Owner away. Safe to call any number of
     * times, from anywhere; every step on its own, so one failing does not stop the others.
     */
    fun stop(server: MinecraftServer, uuid: UUID) {
        val run = runs.remove(uuid) ?: return
        try {
            run.owner?.discard()
        } catch (e: Exception) {
            LOGGER.error("Abuse mode: could not remove the figure of the ending.", e)
        }
        try {
            val player = server.playerList.getPlayer(uuid)
            if (player != null && ServerPlayNetworking.canSend(player, FreezePacket.TYPE)) {
                ServerPlayNetworking.send(player, FreezePacket(0))
            }
        } catch (e: Exception) {
            LOGGER.error("Abuse mode: could not unfreeze a player (their client unfreezes on its own).", e)
        }
    }
}
