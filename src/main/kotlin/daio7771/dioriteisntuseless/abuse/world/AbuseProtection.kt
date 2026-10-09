package daio7771.dioriteisntuseless.abuse.world

import eu.pb4.common.protection.api.CommonProtection
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

/**
 * May the Abuse Mode change this block? The same protections are respected as if the affected
 * player were about to break it and place another one:
 * - Vanilla: spawn protection, world border and adventure/spectator mode.
 * - Common Protection API, if installed (claim mods bring it: FTB Chunks, Open Parties and
 *   Claims, GOML, Flan...). It is optional: without it, only the vanilla checks.
 */
object AbuseProtection {

    private val commonProtectionLoaded = FabricLoader.getInstance().isModLoaded("common-protection-api")

    fun mayChange(level: ServerLevel, pos: BlockPos, player: ServerPlayer): Boolean {
        if (!level.mayInteract(player, pos)) return false  // spawn protection and world border
        if (player.blockActionRestricted(level, pos, player.gameMode.gameModeForPlayer)) return false
        return !commonProtectionLoaded || CommonProtectionBridge.mayChange(level, pos, player)
    }

    /** Kept apart so the API class is only loaded if it is installed. */
    private object CommonProtectionBridge {
        fun mayChange(level: ServerLevel, pos: BlockPos, player: ServerPlayer): Boolean =
            CommonProtection.canBreakBlock(level, pos, player.gameProfile, player) &&
                CommonProtection.canPlaceBlock(level, pos, player.gameProfile, player)
    }
}
