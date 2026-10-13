package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.abuse.AbuseData
import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.entity.TheMinerEntity
import daio7771.dioriteisntuseless.entity.UsefulOwnerEntity
import daio7771.dioriteisntuseless.registry.ModItems
import daio7771.dioriteisntuseless.registry.ModSounds
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stat
import net.minecraft.stats.Stats
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * The first appearance of The "Useful" Owner, once per cycle (until "Start over"): when the player
 * has worn out [TOOLS_NEEDED] Dioritine Axes or [TOOLS_NEEDED] Dioritine Pickaxes, whichever comes
 * first (OwnerState). Axes and pickaxes do not add up: it takes 3 of the same kind.
 *
 * - Right when that tool breaks, it appears silently 4-6 blocks behind them, out of sight, on
 *   solid ground and never inside a building (Visitors.Kind.OWNER). It stands still. No waiting
 *   and no conditions: only if there is no such spot right then, it tries again every second.
 * - If The Miner is visiting and close to that spot, he knows: [OMEN_TICKS] before it appears, he
 *   turns his head towards the spot (TheMinerEntity.foresee).
 * - Radio static plays from where it stands ([playStatic]): [STATIC_DELAY_TICKS] after it appears,
 *   then again every [STATIC_GAP_TICKS] while they have not seen it, [STATIC_PLAYS] times at most.
 *   It is a warning, not a scare: it plays with abuseMode.jumpscares off too.
 * - If they see it (Visibility.sees) while the static plays, the static is cut dead ([cutStatic]).
 * - [SEEN_TICKS] (3 s) after they first SEE it, a loud, dry crack of stone and it is gone, leaving
 *   diorite dust. No flashes. With abuseMode.jumpscares off, the crack is soft.
 * - Only then does it count as shown. If they never see it, it leaves unseen after the last static
 *   ([gaveUp]) and tries again later; the same if it goes away before (they disconnect, go too far,
 *   change world...). It is never saved.
 *
 * Sound and dust only reach that player. Only used from the server thread.
 */
object OwnerStatue {

    const val TOOLS_NEEDED = 3

    /** 3 seconds from the moment they see it. */
    const val SEEN_TICKS = 3 * 20

    /** The static: first this long after it appears, then every [STATIC_GAP_TICKS] (3 s of static, 3 of silence). */
    const val STATIC_DELAY_TICKS = 20
    const val STATIC_GAP_TICKS = 6 * 20
    const val STATIC_PLAYS = 3

    /** How long one play of owner/static_sequence.ogg lasts. */
    const val STATIC_LENGTH_TICKS = 3 * 20

    /** Unseen this long after appearing (the last static and its silence), it leaves. */
    const val GIVE_UP_TICKS = STATIC_DELAY_TICKS + STATIC_PLAYS * STATIC_GAP_TICKS

    private const val STATIC_VOLUME = 1.0f

    /** The static comes from about its head. */
    private const val STATIC_HEIGHT = 1.6

    /** After leaving unseen (its static unanswered), it comes back 30 s to 2 min later. */
    private const val MIN_DELAY_TICKS = 30 * 20L
    private const val MAX_DELAY_TICKS = 2 * 60 * 20L

    /** The Miner turns his head to the spot this long before it appears there. */
    private const val OMEN_TICKS = 4 * 20

    /** ...and keeps looking there this long after it appears. */
    private const val OMEN_AFTER_TICKS = 4 * 20

    /** The Miner only notices if he is this close to the spot. */
    private const val OMEN_DISTANCE = 16.0

    /** Where to look at it: about its eyes (it is 1.1 times as tall as a player). */
    private const val EYE_HEIGHT = 1.75

    /** After leaving unseen: PlayerAbuse.playTicks before which it does not come back. Not saved: after a reload, it comes at once. */
    private val nextTryAt = HashMap<UUID, Long>()

    /** It is about to appear at [spot] (The Miner is already looking there) at PlayerAbuse.playTicks [dueAt]. */
    private class Omen(val spot: Vec3, val dueAt: Long)

    private val omens = HashMap<UUID, Omen>()

    fun init() {
        ServerLifecycleEvents.SERVER_STOPPED.register {
            nextTryAt.clear()
            omens.clear()
        }
    }

    /** It is not coming any more for [uuid] (they disconnected, "Start over"). */
    fun forget(uuid: UUID) {
        omens.remove(uuid)
    }

    /** From ServerPlayerMixin with every statistic: worn-out dioritine tools are counted. */
    @JvmStatic
    fun onStatAwarded(player: ServerPlayer, stat: Stat<*>) {
        if (stat.type != Stats.ITEM_BROKEN || !AbuseMode.active) return
        val axe = stat.value == ModItems.DIORITINE_AXE
        if (!axe && stat.value != ModItems.DIORITINE_PICKAXE) return
        AbuseMode.guard("worn-out tool counting") {
            val data = AbuseData.get(player.server)
            val owner = data.get(player.uuid).owner
            if (axe) owner.axesWornOut++ else owner.pickaxesWornOut++
            data.setDirty()
            // The third one: it comes now.
            tryToCome(player, data.get(player.uuid))
        }
    }

    /** From the AbuseTracker player tick (every few ticks). */
    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        val omen = omens[player.uuid]
        if (omen != null) {
            if (state.playTicks < omen.dueAt) return
            omens.remove(player.uuid)
            // If they turned towards the spot meanwhile, it does not come there: he looked at nothing,
            // and it looks for another spot on the next check.
            if (player.isAlive && VisitorSpots.stillHidden(player, Visitors.Kind.OWNER.type(), omen.spot)) {
                bring(player, omen.spot)
            }
            return
        }
        tryToCome(player, state)
    }

    /**
     * When the tools are worn out and it has not been shown yet: it comes now. If there is no spot
     * for it right now, the next check (a second later) tries again.
     */
    private fun tryToCome(player: ServerPlayer, state: PlayerAbuse) {
        val owner = state.owner
        if (owner.statueShown || !toolsWornOut(owner)) return
        if (state.level >= AbuseTracker.LEVEL_FINAL) return  // the ending has its own appearance
        nextTryAt[player.uuid]?.let { if (state.playTicks < it) return }
        if (!player.isAlive || player.isSpectator || player.uuid in omens) return
        if (Visitors.current(player.uuid, Visitors.Kind.OWNER) != null) return
        approach(player, state)
    }

    /**
     * Picks its spot and brings it there: right away or, if The Miner is close to the spot, after
     * he has turned his head there. Returns false if there is no spot right now.
     */
    private fun approach(player: ServerPlayer, state: PlayerAbuse): Boolean {
        val spot = Visitors.findSpot(player, Visitors.Kind.OWNER) ?: return false
        val miner = Visitors.current(player.uuid, Visitors.Kind.MINER) as? TheMinerEntity
        if (miner == null || miner.isTalking || miner.distanceToSqr(spot) > OMEN_DISTANCE * OMEN_DISTANCE) {
            return bring(player, spot)
        }
        miner.foresee(spot.add(0.0, EYE_HEIGHT, 0.0), OMEN_TICKS + OMEN_AFTER_TICKS)
        omens[player.uuid] = Omen(spot, state.playTicks + OMEN_TICKS)
        return true
    }

    /** [TOOLS_NEEDED] axes or [TOOLS_NEEDED] pickaxes worn out: each kind on its own, never added together. */
    fun toolsWornOut(owner: OwnerState): Boolean =
        owner.axesWornOut >= TOOLS_NEEDED || owner.pickaxesWornOut >= TOOLS_NEEDED

    private fun scheduleTry(player: ServerPlayer, state: PlayerAbuse) {
        nextTryAt[player.uuid] = state.playTicks + MIN_DELAY_TICKS + (player.random.nextDouble() * (MAX_DELAY_TICKS - MIN_DELAY_TICKS)).toLong()
    }

    private fun bring(player: ServerPlayer, spot: Vec3): Boolean {
        val statue = Visitors.bring(player, Visitors.Kind.OWNER, -1, spot) as? UsefulOwnerEntity ?: return false
        statue.becomeStatue()
        return true
    }

    /** From UsefulOwnerEntity: one play of the radio static, from where [statue] stands, only for [player]. */
    fun playStatic(player: ServerPlayer, statue: UsefulOwnerEntity) {
        player.connection.send(
            ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.OWNER_STATIC), SoundSource.HOSTILE,
                statue.x, statue.y + STATIC_HEIGHT, statue.z, STATIC_VOLUME, 1f, player.random.nextLong(),
            )
        )
    }

    /** Cuts the static dead for [player] (they have seen it, or it is gone). */
    fun cutStatic(player: ServerPlayer) {
        player.connection.send(ClientboundStopSoundPacket(ModSounds.OWNER_STATIC.location, SoundSource.HOSTILE))
    }

    /** From UsefulOwnerEntity: unseen after all its static, it is leaving. It comes back 30 s to 2 min later. */
    fun gaveUp(player: ServerPlayer) {
        scheduleTry(player, AbuseData.get(player.server).get(player.uuid))
    }

    /** From UsefulOwnerEntity, [SEEN_TICKS] after they saw it: the crack, the dust, and it is gone for this cycle. */
    fun crumble(player: ServerPlayer, statue: UsefulOwnerEntity) {
        val loud = ModConfig.current.abuseMode.jumpscares
        if (loud) {
            playTo(player, statue, SoundEvents.DEEPSLATE_BRICKS_BREAK, LOUD_VOLUME, 0.5f)
            playTo(player, statue, SoundEvents.STONE_BREAK, LOUD_VOLUME, 0.6f)
            playTo(player, statue, SoundEvents.BASALT_BREAK, LOUD_VOLUME, 0.7f)
        } else {
            playTo(player, statue, SoundEvents.STONE_HIT, SOFT_VOLUME, 0.6f)
        }
        val level = player.serverLevel()
        val box = statue.boundingBox
        val center = box.center
        val diorite = Blocks.DIORITE.defaultBlockState()
        level.sendParticles(
            player, BlockParticleOption(ParticleTypes.BLOCK, diorite), true,
            center.x, center.y, center.z, 80, box.xsize / 3, box.ysize / 3, box.zsize / 3, 0.0,
        )
        level.sendParticles(
            player, BlockParticleOption(ParticleTypes.FALLING_DUST, diorite), true,
            center.x, center.y, center.z, 40, box.xsize / 2, box.ysize / 2, box.zsize / 2, 0.0,
        )
        statue.discard()
        val data = AbuseData.get(player.server)
        data.get(player.uuid).owner.statueShown = true
        data.setDirty()
    }

    /** A sound at the statue that only [player] hears. */
    private fun playTo(player: ServerPlayer, statue: UsefulOwnerEntity, sound: SoundEvent, volume: Float, pitch: Float) {
        player.connection.send(
            ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.HOSTILE,
                statue.x, statue.y + 1.0, statue.z, volume, pitch, player.random.nextLong(),
            )
        )
    }

    /** Above 1 it only carries farther, so 4-6 blocks away it is still almost at full volume. */
    private const val LOUD_VOLUME = 2.0f
    private const val SOFT_VOLUME = 0.3f

    /**
     * For the testing command: the real statue, now (without the tools or the wait; with The Miner
     * near, after he looks). False if there is no spot.
     */
    fun testNow(player: ServerPlayer): Boolean {
        if (!AbuseMode.active) return false
        var brought = false
        AbuseMode.guard("test statue") {
            omens.remove(player.uuid)
            brought = approach(player, AbuseData.get(player.server).get(player.uuid))
        }
        return brought
    }
}
