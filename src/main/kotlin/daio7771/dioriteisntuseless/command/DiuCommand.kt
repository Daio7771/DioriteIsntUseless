package daio7771.dioriteisntuseless.command

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.signal.AbuseSignals
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.network.ConfigSync
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.blocks.BlockStateArgument
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState

/**
 * /diu reload: vuelve a leer la configuración sin reiniciar.
 * /diu abuse <jugador> [nivel | signal [tipo] | credits | reset | change <pos> <bloque>]: SOLO EN
 * DESARROLLO, para pruebas del Abuse Mode; consulta o cambia el nivel, lanza una señal ya (una
 * concreta o al azar), lleva a los créditos, hace "Start over" o hace un cambio en el mundo
 * registrado (para probar que "Start over" lo deshace).
 * Solo operadores (nivel 2).
 */
object DiuCommand {

    private const val PERMISSION_LEVEL = 2
    private const val LANG = "commands.dioriteisntuseless.reload"
    private const val ABUSE_LANG = "commands.dioriteisntuseless.abuse"

    fun init() {
        // /diu abuse solo existe en desarrollo (runClient/runServer): en el mod publicado no se
        // registra, así que ni aparece en el autocompletado.
        val withTestCommands = FabricLoader.getInstance().isDevelopmentEnvironment
        CommandRegistrationCallback.EVENT.register { dispatcher, buildContext, _ ->
            val root = Commands.literal("diu")
                .requires { it.hasPermission(PERMISSION_LEVEL) }
                .then(Commands.literal("reload").executes { reload(it.source) })
            if (withTestCommands) root.then(abuseCommand(buildContext))
            dispatcher.register(root)
        }
    }

    private fun abuseCommand(buildContext: CommandBuildContext): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("abuse").then(
            Commands.argument("player", EntityArgument.player())
                .executes { showAbuse(it.source, EntityArgument.getPlayer(it, "player")) }
                .then(
                    Commands.literal("signal")
                        .executes { forceSignal(it.source, EntityArgument.getPlayer(it, "player"), null) }
                        .then(
                            Commands.argument("type", StringArgumentType.word())
                                .suggests { _, builder -> SharedSuggestionProvider.suggest(AbuseSignals.ids, builder) }
                                .executes {
                                    forceSignal(
                                        it.source,
                                        EntityArgument.getPlayer(it, "player"),
                                        StringArgumentType.getString(it, "type"),
                                    )
                                }
                        )
                )
                .then(
                    Commands.literal("credits")
                        .executes { skipToCredits(it.source, EntityArgument.getPlayer(it, "player")) }
                )
                .then(
                    Commands.literal("reset")
                        .executes { startOver(it.source, EntityArgument.getPlayer(it, "player")) }
                )
                .then(
                    Commands.literal("change").then(
                        Commands.argument("pos", BlockPosArgument.blockPos()).then(
                            Commands.argument("block", BlockStateArgument.block(buildContext))
                                .executes {
                                    testWorldChange(
                                        it.source,
                                        EntityArgument.getPlayer(it, "player"),
                                        BlockPosArgument.getLoadedBlockPos(it, "pos"),
                                        BlockStateArgument.getBlock(it, "block").state,
                                    )
                                }
                        )
                    )
                )
                .then(
                    Commands.argument("level", IntegerArgumentType.integer(0, AbuseTracker.LEVEL_FINAL))
                        .executes {
                            setAbuse(
                                it.source,
                                EntityArgument.getPlayer(it, "player"),
                                IntegerArgumentType.getInteger(it, "level"),
                            )
                        }
                )
        )

    private fun reload(source: CommandSourceStack): Int {
        return when (val result = ModConfig.reload()) {
            is ModConfig.ReloadResult.Success -> {
                ConfigSync.broadcast(source.server)
                // Como el /reload vanilla: también se avisa a los demás operadores.
                source.sendSuccess({ Component.translatable("$LANG.success") }, true)
                if (result.corrections > 0) {
                    source.sendSuccess({ Component.translatable("$LANG.corrected", result.corrections) }, false)
                }
                result.restartPendingDurability?.let { pending ->
                    source.sendSuccess({
                        Component.translatable("$LANG.restart_required", pending, ModConfig.axeDurabilityAtStartup)
                    }, false)
                }
                1
            }
            ModConfig.ReloadResult.InvalidJson -> {
                source.sendFailure(Component.translatable("$LANG.invalid_json"))
                0
            }
            ModConfig.ReloadResult.ReadError -> {
                source.sendFailure(Component.translatable("$LANG.read_error"))
                0
            }
        }
    }

    private fun showAbuse(source: CommandSourceStack, player: ServerPlayer): Int {
        val state = AbuseTracker.state(source.server, player)
        val step = AbuseTracker.nextStep(state)
        val progress = AbuseTracker.progress(state)?.let { "${(it * 100).toInt()}%" } ?: "-"
        source.sendSuccess({
            Component.translatable(
                "$ABUSE_LANG.status", player.displayName, state.level, state.treesFelled, state.strikes,
                state.treesAtLevel, step?.trees?.toString() ?: "-", state.strikesAtLevel, step?.strikes?.toString() ?: "-",
                progress, AbuseTracker.worldChangeCount(source.server, player), state.takenItems.size,
            )
        }, false)
        warnIfInactive(source)
        return state.level
    }

    private fun setAbuse(source: CommandSourceStack, player: ServerPlayer, level: Int): Int {
        AbuseTracker.setLevel(source.server, player, level)
        // Como /gamemode: también se avisa a los demás operadores (y queda en el log).
        source.sendSuccess({ Component.translatable("$ABUSE_LANG.set", player.displayName, level) }, true)
        warnIfInactive(source)
        return level
    }

    private fun forceSignal(source: CommandSourceStack, player: ServerPlayer, type: String?): Int {
        if (!AbuseTracker.forceSignal(source.server, player, type)) {
            source.sendFailure(Component.translatable("$ABUSE_LANG.no_signal", player.displayName))
            return 0
        }
        source.sendSuccess({ Component.translatable("$ABUSE_LANG.signal", player.displayName) }, false)
        return 1
    }

    private fun skipToCredits(source: CommandSourceStack, player: ServerPlayer): Int {
        if (!AbuseTracker.skipToCredits(source.server, player)) {
            source.sendFailure(Component.translatable("$ABUSE_LANG.inactive"))
            return 0
        }
        source.sendSuccess({ Component.translatable("$ABUSE_LANG.credits", player.displayName) }, false)
        return 1
    }

    private fun startOver(source: CommandSourceStack, player: ServerPlayer): Int {
        if (!AbuseTracker.startOver(source.server, player)) {
            source.sendFailure(Component.translatable("$ABUSE_LANG.reset_failed"))
            return 0
        }
        source.sendSuccess({ Component.translatable("$ABUSE_LANG.reset", player.displayName) }, true)
        return 1
    }

    private fun testWorldChange(source: CommandSourceStack, player: ServerPlayer, pos: BlockPos, state: BlockState): Int {
        if (!AbuseTracker.testWorldChange(player, source.level, pos, state)) {
            source.sendFailure(Component.translatable("$ABUSE_LANG.change_refused"))
            return 0
        }
        source.sendSuccess({
            Component.translatable("$ABUSE_LANG.change", pos.toShortString(), player.displayName)
        }, false)
        return 1
    }

    private fun warnIfInactive(source: CommandSourceStack) {
        if (!AbuseMode.active) source.sendSuccess({ Component.translatable("$ABUSE_LANG.inactive") }, false)
    }
}
