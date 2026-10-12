package daio7771.dioriteisntuseless.command

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import daio7771.dioriteisntuseless.abuse.AbuseMode
import daio7771.dioriteisntuseless.abuse.AbuseTracker
import daio7771.dioriteisntuseless.abuse.signal.AbuseSignals
import daio7771.dioriteisntuseless.abuse.visitor.MinerQuests
import daio7771.dioriteisntuseless.abuse.visitor.MinerVisits
import daio7771.dioriteisntuseless.abuse.visitor.OwnerStatue
import daio7771.dioriteisntuseless.abuse.visitor.Visitors
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
 * /diu reload: reads the config again without restarting.
 * /diu abuse <player> [level | signal [type] | credits | reset | change <pos> <block> |
 * visitor [miner | figure | statue | dismiss | next | quest]]: DEVELOPMENT ONLY, for testing the Abuse Mode; shows or
 * changes the level, fires a signal now (a specific one or a random one), skips to the credits,
 * runs "Start over", makes a recorded world change (to test that "Start over" undoes it), or
 * shows the visitors' state, brings one for 30 s, sends them away or makes The Miner's next visit
 * due now (the real one: 5 minutes, then the next one is scheduled), gives them a new job of his,
 * or brings the real statue now (it crumbles 3 s after they see it).
 * Operators only (level 2).
 */
object DiuCommand {

    private const val PERMISSION_LEVEL = 2
    private const val LANG = "commands.dioriteisntuseless.reload"
    private const val ABUSE_LANG = "commands.dioriteisntuseless.abuse"

    fun init() {
        // /diu abuse only exists in development (runClient/runServer): in the published mod it is
        // not registered, so it does not even show up in autocompletion.
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
                    Commands.literal("visitor")
                        .executes { showVisitors(it.source, EntityArgument.getPlayer(it, "player")) }
                        .then(
                            Commands.literal("miner")
                                .executes { bringVisitor(it.source, EntityArgument.getPlayer(it, "player"), Visitors.Kind.MINER) }
                        )
                        .then(
                            Commands.literal("figure")
                                .executes { bringVisitor(it.source, EntityArgument.getPlayer(it, "player"), Visitors.Kind.OWNER) }
                        )
                        .then(
                            Commands.literal("next")
                                .executes { minerVisitNow(it.source, EntityArgument.getPlayer(it, "player")) }
                        )
                        .then(
                            Commands.literal("quest")
                                .executes { newQuest(it.source, EntityArgument.getPlayer(it, "player")) }
                        )
                        .then(
                            Commands.literal("statue")
                                .executes { bringStatue(it.source, EntityArgument.getPlayer(it, "player")) }
                        )
                        .then(
                            Commands.literal("dismiss")
                                .executes { dismissVisitors(it.source, EntityArgument.getPlayer(it, "player")) }
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
                // Like the vanilla /reload: the other operators are told too.
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
        // Like /gamemode: the other operators are told too (and it goes to the log).
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

    private fun showVisitors(source: CommandSourceStack, player: ServerPlayer): Int {
        val state = AbuseTracker.state(source.server, player)
        val quest = MinerQuests.Type.byId(state.miner.questType)?.let { type ->
            Component.translatable(
                "$ABUSE_LANG.visitor.quest", MinerQuests.description(state.miner),
                MinerQuests.progress(player, state, type), state.miner.questAmount,
            )
        } ?: Component.literal("-")
        val here = Visitors.Kind.entries.filter { Visitors.current(player.uuid, it) != null }.joinToString { it.name.lowercase() }
        source.sendSuccess({
            Component.translatable(
                "$ABUSE_LANG.visitor.status", player.displayName, state.miner.met, state.miner.nextVisitAt,
                state.playTicks, state.miner.gone, quest, state.owner.axesWornOut, state.owner.pickaxesWornOut,
                OwnerStatue.TOOLS_NEEDED, state.owner.statueShown, here.ifEmpty { "-" },
            )
        }, false)
        warnIfInactive(source)
        return 1
    }

    private fun bringVisitor(source: CommandSourceStack, player: ServerPlayer, kind: Visitors.Kind): Int {
        if (!Visitors.testBring(player, kind)) {
            source.sendFailure(Component.translatable("$ABUSE_LANG.visitor.no_spot", player.displayName))
            warnIfInactive(source)
            return 0
        }
        source.sendSuccess({ Component.translatable("$ABUSE_LANG.visitor.brought", player.displayName) }, false)
        return 1
    }

    private fun minerVisitNow(source: CommandSourceStack, player: ServerPlayer): Int {
        if (!MinerVisits.testVisitNow(player)) {
            source.sendFailure(Component.translatable("$ABUSE_LANG.inactive"))
            return 0
        }
        source.sendSuccess({ Component.translatable("$ABUSE_LANG.visitor.next", player.displayName) }, false)
        return 1
    }

    private fun bringStatue(source: CommandSourceStack, player: ServerPlayer): Int {
        if (!OwnerStatue.testNow(player)) {
            source.sendFailure(Component.translatable("$ABUSE_LANG.visitor.no_spot", player.displayName))
            warnIfInactive(source)
            return 0
        }
        source.sendSuccess({ Component.translatable("$ABUSE_LANG.visitor.statue", player.displayName) }, false)
        return 1
    }

    private fun newQuest(source: CommandSourceStack, player: ServerPlayer): Int {
        if (!AbuseMode.active) {
            source.sendFailure(Component.translatable("$ABUSE_LANG.inactive"))
            return 0
        }
        val state = AbuseTracker.state(source.server, player)
        AbuseMode.guard("test miner quest") { MinerQuests.assign(player, state) }
        source.sendSuccess({
            Component.translatable("$ABUSE_LANG.visitor.new_quest", player.displayName, MinerQuests.description(state.miner))
        }, false)
        return 1
    }

    private fun dismissVisitors(source: CommandSourceStack, player: ServerPlayer): Int {
        Visitors.dismissAll(player.uuid)
        source.sendSuccess({ Component.translatable("$ABUSE_LANG.visitor.dismissed", player.displayName) }, false)
        return 1
    }

    private fun warnIfInactive(source: CommandSourceStack) {
        if (!AbuseMode.active) source.sendSuccess({ Component.translatable("$ABUSE_LANG.inactive") }, false)
    }
}
