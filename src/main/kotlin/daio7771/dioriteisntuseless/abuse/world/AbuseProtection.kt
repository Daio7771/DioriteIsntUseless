package daio7771.dioriteisntuseless.abuse.world

import eu.pb4.common.protection.api.CommonProtection
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

/**
 * ¿Puede el Abuse Mode cambiar este bloque? Se respetan las mismas protecciones que si el jugador
 * afectado fuera a romperlo y poner otro:
 * - Vanilla: protección del spawn, borde del mundo y modo aventura/espectador.
 * - Common Protection API, si está instalada (la traen los mods de claims: FTB Chunks, Open
 *   Parties and Claims, GOML, Flan...). Es opcional: sin ella, solo lo vanilla.
 */
object AbuseProtection {

    private val commonProtectionLoaded = FabricLoader.getInstance().isModLoaded("common-protection-api")

    fun mayChange(level: ServerLevel, pos: BlockPos, player: ServerPlayer): Boolean {
        if (!level.mayInteract(player, pos)) return false  // protección del spawn y borde del mundo
        if (player.blockActionRestricted(level, pos, player.gameMode.gameModeForPlayer)) return false
        return !commonProtectionLoaded || CommonProtectionBridge.mayChange(level, pos, player)
    }

    /** Aparte para que la clase de la API solo se cargue si está instalada. */
    private object CommonProtectionBridge {
        fun mayChange(level: ServerLevel, pos: BlockPos, player: ServerPlayer): Boolean =
            CommonProtection.canBreakBlock(level, pos, player.gameProfile, player) &&
                CommonProtection.canPlaceBlock(level, pos, player.gameProfile, player)
    }
}
