package daio7771.dioriteisntuseless.entity

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
 * As a statue (OwnerStatue): once the player sees it, it crumbles OwnerStatue.SEEN_TICKS later.
 */
class UsefulOwnerEntity(type: EntityType<out UsefulOwnerEntity>, level: Level) : VisitorEntity(type, level) {

    private var statue = false

    /** Ticks since the player first saw it, or -1 if they have not seen it yet. */
    private var seenTicks = -1

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
        if (seenTicks < 0 && Visibility.sees(player, boundingBox)) seenTicks = 0
        // Once seen, the 3 seconds run even if they look away.
        if (seenTicks >= 0 && ++seenTicks >= OwnerStatue.SEEN_TICKS) OwnerStatue.crumble(player, this)
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
