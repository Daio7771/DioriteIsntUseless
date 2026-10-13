package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.entity.VisitorEntity
import daio7771.dioriteisntuseless.registry.ModEntities
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Brings the visitors (The Miner, The "Useful" Owner) to a player and sends them away. At most one
 * of each kind per player. They are never saved: on disconnecting, on "Start over" and when the
 * server stops, they are gone, and whatever has to be remembered is in the player's state
 * (MinerState, OwnerState).
 *
 * Only used from the server thread, always inside AbuseMode.guard.
 */
object Visitors {

    /** How each kind appears: distance from the player and how far from straight behind them. */
    enum class Kind(
        val type: () -> EntityType<out VisitorEntity>, val minDistance: Double, val maxDistance: Double, val spread: Double,
        val inFront: Boolean = false,
    ) {
        /** 2-3 blocks away, anywhere out of sight. */
        MINER({ ModEntities.THE_MINER }, 2.0, 3.0, 100.0),

        /** 4-6 blocks behind. */
        OWNER({ ModEntities.USEFUL_OWNER }, 4.0, 6.0, 60.0),

        /** The ending: 3-4 blocks in front of them, in plain sight. */
        OWNER_IN_FRONT({ ModEntities.USEFUL_OWNER }, 3.0, 4.0, 20.0, inFront = true),
    }

    private val active = HashMap<UUID, MutableList<VisitorEntity>>()

    /** No hostile mob this close: nobody shows up in the middle of a fight. */
    private const val HOSTILE_RADIUS = 12.0

    fun init() {
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            AbuseMode.guard("visitors on disconnect") { dismissAll(handler.player.uuid) }
        }
        // In single player the game stays open between worlds: nothing carries over to the next one.
        ServerLifecycleEvents.SERVER_STOPPED.register { active.clear() }
    }

    /**
     * Brings [kind] next to [player], silently and out of sight (or at [spot]), for [ticks] ticks
     * (-1: until sent away). The previous one of that kind goes away. Returns null if there is no
     * spot right now.
     */
    fun bring(player: ServerPlayer, kind: Kind, ticks: Int, spot: Vec3? = findSpot(player, kind)): VisitorEntity? {
        dismiss(player.uuid, kind)
        if (spot == null) return null
        val type = kind.type()
        val level = player.serverLevel()
        val visitor = type.create(level) ?: return null
        visitor.moveTo(spot.x, spot.y, spot.z, 0f, 0f)
        visitor.face(player)
        visitor.visit(player, ticks)
        if (!level.addFreshEntity(visitor)) return null
        active.getOrPut(player.uuid) { ArrayList() } += visitor
        return visitor
    }

    /** Where [kind] could appear next to [player] right now, or null. */
    fun findSpot(player: ServerPlayer, kind: Kind): Vec3? =
        VisitorSpots.find(player, kind.type(), kind.minDistance, kind.maxDistance, kind.spread, kind.inFront)

    /** A moment for a visitor to arrive: on the ground, not in danger and not busy (in bed, riding, flying, swimming or fighting). */
    fun isCalm(player: ServerPlayer): Boolean =
        player.isAlive && !player.isSpectator && !player.isSleeping && !player.isPassenger && player.onGround() &&
            !player.isFallFlying && !player.isInWater && !player.isInLava && !player.isOnFire && player.hurtTime == 0 &&
            player.serverLevel().getEntitiesOfClass(Mob::class.java, player.boundingBox.inflate(HOSTILE_RADIUS)) {
                it is Enemy && it.isAlive
            }.isEmpty()

    /** The [kind] visiting [uuid] right now, if any. */
    fun current(uuid: UUID, kind: Kind): VisitorEntity? = visitors(uuid).firstOrNull { it.type === kind.type() }

    fun dismiss(uuid: UUID, kind: Kind) {
        current(uuid, kind)?.discard()
    }

    /** Sends every visitor of [uuid] away at once (disconnecting, "Start over"), and the statue on its way too. */
    fun dismissAll(uuid: UUID) {
        OwnerStatue.forget(uuid)
        active.remove(uuid)?.forEach { it.discard() }
    }

    /** Those still in the world: the ones that went away by themselves are forgotten here. */
    private fun visitors(uuid: UUID): List<VisitorEntity> {
        val list = active[uuid] ?: return emptyList()
        list.removeIf { it.isRemoved }
        if (list.isEmpty()) active.remove(uuid)
        return list
    }

    /** For the testing command. Returns false if there was no spot (or the Abuse Mode is not active). */
    fun testBring(player: ServerPlayer, kind: Kind): Boolean {
        if (!AbuseMode.active) return false
        var brought = false
        AbuseMode.guard("test visitor") { brought = bring(player, kind, TEST_TICKS) != null }
        return brought
    }

    /** For the testing command: visitors come and go by themselves after 30 s. */
    private const val TEST_TICKS = 30 * 20
}
