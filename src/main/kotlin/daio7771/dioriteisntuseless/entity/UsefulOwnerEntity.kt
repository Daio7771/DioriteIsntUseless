package daio7771.dioriteisntuseless.entity

import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.visitor.OwnerStatue
import daio7771.dioriteisntuseless.abuse.world.Visibility
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.control.BodyRotationControl
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.level.Level
import java.util.EnumSet

/**
 * The "Useful" Owner. Its name never shows up in the game: no name tag, and its registry id and
 * display name say nothing about it (see ModEntities).
 *
 * It never moves: it stands where it appeared, its body facing the way it was set to face, and
 * its arms and legs never stir (VisitorRenderer draws it that way). The only thing it does is
 * follow the visited player with its head ([WatchPlayer]), as statue and at the ending alike. Its
 * body never turns after the head, so the head turns as far as it takes: all the way round if the
 * player walks behind it. It never attacks, never does anything else, and cannot even be hit (not
 * pickable).
 *
 * As a statue (OwnerStatue): radio static plays from it while the player has not seen it, and is
 * cut dead as soon as they do; it crumbles OwnerStatue.SEEN_TICKS after they see it. If they
 * never do, it leaves unseen after the last static.
 */
class UsefulOwnerEntity(type: EntityType<out UsefulOwnerEntity>, level: Level) : VisitorEntity(type, level) {

    private var statue = false

    /** Ticks since the player first saw it, or -1 if they have not seen it yet. */
    private var seenTicks = -1

    /** As a statue: ticks unseen since it appeared, how many times the static has played and when the last one ends. */
    private var unseenTicks = 0
    private var staticPlays = 0
    private var staticEndsAt = -1

    /** Unseen after all its static: it is leaving. */
    private var gaveUp = false

    fun becomeStatue() {
        statue = true
    }

    override fun isPickable(): Boolean = false

    override fun registerGoals() {
        goalSelector.addGoal(0, WatchPlayer(this))
    }

    /** Its body never turns: not after the head, not ever. Runs on both sides (the client works out the body too). */
    override fun createBodyControl(): BodyRotationControl = object : BodyRotationControl(this) {
        override fun clientTick() {}
    }

    override fun visitTick(player: ServerPlayer) {
        if (!statue) return
        if (seenTicks < 0) {
            if (Visibility.sees(player, boundingBox)) {
                seenTicks = 0
                cutStatic(player)
            } else {
                tickStatic(player)
            }
        }
        // Once seen, the 3 seconds run even if they look away.
        if (seenTicks >= 0 && ++seenTicks >= OwnerStatue.SEEN_TICKS) OwnerStatue.crumble(player, this)
    }

    /** While unseen: the static on schedule and, after the last one, it leaves (OwnerStatue). */
    private fun tickStatic(player: ServerPlayer) {
        unseenTicks++
        if (gaveUp) return
        if (staticPlays < OwnerStatue.STATIC_PLAYS) {
            if (unseenTicks >= OwnerStatue.STATIC_DELAY_TICKS + staticPlays * OwnerStatue.STATIC_GAP_TICKS) {
                OwnerStatue.playStatic(player, this)
                staticPlays++
                staticEndsAt = unseenTicks + OwnerStatue.STATIC_LENGTH_TICKS
            }
        } else if (unseenTicks >= OwnerStatue.GIVE_UP_TICKS) {
            // If they turn to it while it is leaving, it is seen after all: it crumbles as usual.
            gaveUp = true
            OwnerStatue.gaveUp(player)
            leave()
        }
    }

    /** The static stops dead, if it is still playing. */
    private fun cutStatic(player: ServerPlayer) {
        if (unseenTicks < staticEndsAt) OwnerStatue.cutStatic(player)
        staticEndsAt = -1
    }

    /** Whatever takes it away ("Start over", the Abuse Mode stopping...), its static stops with it. */
    override fun remove(reason: RemovalReason) {
        if (!level().isClientSide && unseenTicks < staticEndsAt) {
            AbuseMode.guard("statue static") {
                visitedPlayer?.let { level().server?.playerList?.getPlayer(it) }?.let(::cutStatic)
            }
        }
        super.remove(reason)
    }

    /** Its head follows the visited player's eyes, always. Nothing else. */
    private class WatchPlayer(private val owner: UsefulOwnerEntity) : Goal() {
        init {
            setFlags(EnumSet.of(Flag.LOOK))
        }

        override fun canUse(): Boolean = owner.visiting != null

        override fun requiresUpdateEveryTick(): Boolean = true

        override fun tick() {
            val player = owner.visiting ?: return
            owner.lookControl.setLookAt(player.x, player.eyeY, player.z, HEAD_TURN_SPEED, HEAD_TURN_SPEED)
        }
    }

    companion object {
        /** It is drawn this much wider and taller than a player (the hitbox matches): something is off. */
        const val WIDTH_SCALE = 0.9f
        const val HEIGHT_SCALE = 1.1f

        /** Degrees per tick: steady, never a snap. */
        private const val HEAD_TURN_SPEED = 8f
    }
}
