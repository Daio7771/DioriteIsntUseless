package daio7771.dioriteisntuseless.abuse.visitor

import daio7771.dioriteisntuseless.abuse.AbuseData
import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.PlayerAbuse
import daio7771.dioriteisntuseless.config.ModConfig
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
import java.util.UUID

/**
 * The first appearance of The "Useful" Owner, once per cycle (until "Start over"): when the player
 * has worn out [TOOLS_NEEDED] Dioritine Axes or [TOOLS_NEEDED] Dioritine Pickaxes, whichever comes
 * first (OwnerState). Axes and pickaxes do not add up: it takes 3 of the same kind.
 *
 * - A while later, at a calm moment, it appears silently 4-6 blocks behind them, out of sight,
 *   on solid ground and never inside a building (Visitors.Kind.OWNER). It stands still.
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

    /** PlayerAbuse.playTicks before which it does not try again. Not saved: a reload starts a new wait. */
    private val nextTryAt = HashMap<UUID, Long>()

    fun init() {
        ServerLifecycleEvents.SERVER_STOPPED.register { nextTryAt.clear() }
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
        val owner = state.owner
        if (owner.statueShown || !toolsWornOut(owner)) return
        if (state.level >= AbuseTracker.LEVEL_FINAL) return  // the ending has its own appearance
        val waitUntil = nextTryAt[player.uuid]
        if (waitUntil == null) {
            scheduleTry(player, state)
            return
        }
        if (state.playTicks < waitUntil || Visitors.current(player.uuid, Visitors.Kind.OWNER) != null) return
        // Never together with The Miner.
        if (Visitors.current(player.uuid, Visitors.Kind.MINER) != null || !Visitors.isCalm(player)) return
        scheduleTry(player, state)
        bring(player)
    }

    /** [TOOLS_NEEDED] axes or [TOOLS_NEEDED] pickaxes worn out: each kind on its own, never added together. */
    fun toolsWornOut(owner: OwnerState): Boolean =
        owner.axesWornOut >= TOOLS_NEEDED || owner.pickaxesWornOut >= TOOLS_NEEDED

    private fun scheduleTry(player: ServerPlayer, state: PlayerAbuse) {
        nextTryAt[player.uuid] = state.playTicks + MIN_DELAY_TICKS + (player.random.nextDouble() * (MAX_DELAY_TICKS - MIN_DELAY_TICKS)).toLong()
    }

    private fun bring(player: ServerPlayer): Boolean {
        val statue = Visitors.bring(player, Visitors.Kind.OWNER, -1) as? UsefulOwnerEntity ?: return false
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

    /** For the testing command: the real statue, now (without the tools or the wait). False if there is no spot. */
    fun testNow(player: ServerPlayer): Boolean {
        if (!AbuseMode.active) return false
        var brought = false
        AbuseMode.guard("test statue") { brought = bring(player) }
        return brought
    }
}
