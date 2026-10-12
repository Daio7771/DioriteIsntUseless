package daio7771.dioriteisntuseless.entity

import daio7771.dioriteisntuseless.abuse.visitor.OwnerStatue
import daio7771.dioriteisntuseless.abuse.world.Visibility
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level

/**
 * The "Useful" Owner. Its name never shows up in the game: no name tag, and its registry id and
 * display name say nothing about it (see ModEntities).
 *
 * It never moves towards the player, never attacks, never does anything: it stands where it
 * appeared, facing the way it was set to face. It cannot even be hit (not pickable).
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

    override fun visitTick(player: ServerPlayer) {
        if (!statue) return
        if (seenTicks < 0 && Visibility.sees(player, boundingBox)) seenTicks = 0
        // Once seen, the 3 seconds run even if they look away.
        if (seenTicks >= 0 && ++seenTicks >= OwnerStatue.SEEN_TICKS) OwnerStatue.crumble(player, this)
    }

    companion object {
        /** It is drawn this much wider and taller than a player (the hitbox matches): something is off. */
        const val WIDTH_SCALE = 0.9f
        const val HEIGHT_SCALE = 1.1f
    }
}
