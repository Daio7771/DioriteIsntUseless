package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.abuse.AbuseData
import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.entity.TheMinerEntity
import daio7771.dioriteisntuseless.entity.UsefulOwnerEntity
import daio7771.dioriteisntuseless.registry.ModItems
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundSoundPacket
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
 * - A while later, at a calm moment, it appears silently 4-6 blocks behind them, out of sight,
 *   on solid ground and never inside a building (Visitors.Kind.OWNER). It stands still.
 * - If The Miner is visiting and close to that spot, he knows: [OMEN_TICKS] before it appears, he
 *   turns his head towards the spot (TheMinerEntity.foresee). It never comes while he is talking.
 * - [SEEN_TICKS] (3 s) after they first SEE it (Visibility.sees), a loud, dry crack of stone and
 *   it is gone, leaving diorite dust. No flashes. With abuseMode.jumpscares off, the crack is soft.
 * - Only then does it count as shown. If it goes away before (they disconnect, go too far, change
 *   world...), it is never saved: it is tried again later.
 *
 * Sound and dust only reach that player. Only used from the server thread.
 */
object OwnerStatue {

    const val TOOLS_NEEDED = 3

    /** 3 seconds from the moment they see it. */
    const val SEEN_TICKS = 3 * 20

    /** It does not come right as the last tool breaks, nor right after a failed try: 30 s to 2 min later. */
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

    /** PlayerAbuse.playTicks before which it does not try again. Not saved: a reload starts a new wait. */
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
        }
    }

    /** From the AbuseTracker player tick (every few ticks). */
    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        val omen = omens[player.uuid]
        if (omen != null) {
            if (state.playTicks < omen.dueAt) return
            omens.remove(player.uuid)
            // If they turned towards the spot meanwhile (or trouble came), it does not come: he looked at nothing.
            if (Visitors.isCalm(player) && VisitorSpots.stillHidden(player, Visitors.Kind.OWNER.type(), omen.spot)) {
                bring(player, omen.spot)
            }
            return
        }
        val owner = state.owner
        if (owner.statueShown || !toolsWornOut(owner)) return
        if (state.level >= AbuseTracker.LEVEL_FINAL) return  // the ending has its own appearance
        val waitUntil = nextTryAt[player.uuid]
        if (waitUntil == null) {
            scheduleTry(player, state)
            return
        }
        if (state.playTicks < waitUntil || Visitors.current(player.uuid, Visitors.Kind.OWNER) != null) return
        if (minerTalking(player) || !Visitors.isCalm(player)) return
        scheduleTry(player, state)
        approach(player, state)
    }

    /** The Miner is trading or talking with them: it does not interrupt. */
    private fun minerTalking(player: ServerPlayer): Boolean =
        (Visitors.current(player.uuid, Visitors.Kind.MINER) as? TheMinerEntity)?.isTalking == true

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
