package daio7771.dioriteisntuseless.command

import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.network.ConfigSync
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/** /diu reload: vuelve a leer la configuración sin reiniciar. Solo operadores (nivel 2). */
object DiuCommand {

    private const val PERMISSION_LEVEL = 2
    private const val LANG = "commands.dioriteisntuseless.reload"

    fun init() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("diu")
                    .requires { it.hasPermission(PERMISSION_LEVEL) }
                    .then(Commands.literal("reload").executes { reload(it.source) })
            )
        }
    }

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
}
