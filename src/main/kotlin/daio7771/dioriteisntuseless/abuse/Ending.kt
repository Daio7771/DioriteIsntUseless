package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.abuse.morse.MorseCode
import daio7771.dioriteisntuseless.abuse.morse.MorsePhrases
import daio7771.dioriteisntuseless.abuse.signal.AbuseSignals
import daio7771.dioriteisntuseless.abuse.signal.MorseSignal
import daio7771.dioriteisntuseless.abuse.signal.NonsenseNameSignal
import daio7771.dioriteisntuseless.config.ModConfig
import daio7771.dioriteisntuseless.registry.ModItems
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack

/**
 * El final (HORROR_DESIGN.md, apartado 5). Empieza abuseMode.daysUntilEnding días de juego después
 * de EL cartel del nivel 4:
 *
 * - Fase A, "Diorite Is Useless": se le retiran las hachas, lingotes y cristales (inventario,
 *   armadura, mano secundaria, cofre de Ender, cursor y cuadrícula de fabricar) y se guardan para
 *   "Start over". Desde entonces el hacha no tala en sus manos y no puede sacar cristales del horno
 *   (DioriteUselessness). Morse final: DELETE THIS MOD.
 * - Fase B: un día de juego con las señales del nivel final (todo a más frecuencia y todos los
 *   ítems con nombres sin sentido). Después, los créditos (paso 8).
 *
 * Para que no se le vea desaparecer nada de la barra rápida, la fase A espera a un momento en que
 * no mira: al entrar al mundo o al despertarse tras dormir. Si en medio día de juego no ha pasado
 * ninguna de las dos cosas, se hace igualmente.
 */
object Ending {

    /** Duración de la fase B. */
    const val PHASE_B_TICKS = AbuseTracker.TICKS_PER_DAY

    /** Si no entra ni duerme en este tiempo desde que toca, la fase A ocurre igualmente. */
    private const val FALLBACK_TICKS = AbuseTracker.TICKS_PER_DAY / 2

    /** A partir de aquí la pantalla está completamente a oscuras al dormir. */
    private const val DARK_SLEEP_TICKS = 100

    private const val FINAL_MORSE = "DELETE THIS MOD"

    fun init() {
        EntitySleepEvents.STOP_SLEEPING.register { entity, _ ->
            if (entity is ServerPlayer && entity.sleepTimer >= DARK_SLEEP_TICKS && AbuseMode.active) {
                AbuseMode.guard("ending on wake") { startIfDue(entity, AbuseData.get(entity.server)) }
            }
        }
    }

    /** Desde el tick del jugador (cada pocos ticks): la red de seguridad del medio día. */
    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        val due = dueAt(state)
        if (due >= 0 && state.playTicks >= due + FALLBACK_TICKS) start(player, state)
    }

    /** Al entrar al mundo: si ya toca, empieza; si ya está en la fase final, le recuerda los nombres. */
    fun onJoin(player: ServerPlayer, data: AbuseData) {
        val state = data.getIfPresent(player.uuid) ?: return
        if (isDue(state)) {
            start(player, state)
            data.setDirty()
        } else if (state.level == AbuseTracker.LEVEL_FINAL) {
            NonsenseNameSignal.everything(player)  // su cliente lo olvidó al desconectarse
        }
    }

    private fun startIfDue(player: ServerPlayer, data: AbuseData) {
        val state = data.getIfPresent(player.uuid) ?: return
        if (!isDue(state)) return
        start(player, state)
        data.setDirty()
    }

    private fun isDue(state: PlayerAbuse): Boolean {
        val due = dueAt(state)
        return due >= 0 && state.playTicks >= due
    }

    /** playTicks en que toca el final, o -1 si aún no está en el nivel 4 con el cartel puesto. */
    private fun dueAt(state: PlayerAbuse): Long {
        if (state.level != AbuseTracker.MAX_LEVEL || state.finalSignPlacedAt < 0) return -1
        return state.finalSignPlacedAt + ModConfig.current.abuseMode.daysUntilEnding * AbuseTracker.TICKS_PER_DAY
    }

    /** Fase A y comienzo de la fase B. También lo usa el comando de pruebas. */
    fun start(player: ServerPlayer, state: PlayerAbuse) {
        state.level = AbuseTracker.LEVEL_FINAL
        state.levelReachedAt = state.playTicks
        state.endingStartedAt = state.playTicks
        state.dioriteUseless = true
        val taken = takeItems(player, state)
        LOGGER.debug("Abuse mode: ending started for {}; {} stack(s) held back.", player.gameProfile.name, taken)
        DioriteUselessness.sync(player)
        NonsenseNameSignal.everything(player)
        MorseSignal.send(player, state, MorsePhrases.Phrase(FINAL_MORSE, MorseCode.encode(FINAL_MORSE).code))
        AbuseSignals.schedule(player, state)
    }

    /** Retira lo que le dio el mod y lo guarda en [state] para "Start over". Devuelve cuántas pilas. */
    private fun takeItems(player: ServerPlayer, state: PlayerAbuse): Int {
        // Lo que hubiera en una mesa de trabajo abierta vuelve primero al inventario.
        if (player.containerMenu !== player.inventoryMenu) player.closeContainer()
        var taken = 0
        val inventory = player.inventory
        for (list in listOf(inventory.items, inventory.armor, inventory.offhand)) {
            for (i in list.indices) {
                if (take(list[i], state)) {
                    list[i] = ItemStack.EMPTY
                    taken++
                }
            }
        }
        taken += takeFrom(player.enderChestInventory, state)
        taken += takeFrom(player.inventoryMenu.craftSlots, state)  // la cuadrícula 2x2 del inventario
        if (take(player.containerMenu.carried, state)) {
            player.containerMenu.carried = ItemStack.EMPTY
            taken++
        }
        inventory.setChanged()
        return taken
    }

    private fun takeFrom(container: Container, state: PlayerAbuse): Int {
        var taken = 0
        for (i in 0 until container.containerSize) {
            if (take(container.getItem(i), state)) {
                container.setItem(i, ItemStack.EMPTY)
                taken++
            }
        }
        return taken
    }

    /** Si [stack] es del mod, guarda una copia (con su NBT: encantamientos, desgaste...). */
    private fun take(stack: ItemStack, state: PlayerAbuse): Boolean {
        if (stack.isEmpty || !isTakenAtTheEnd(stack)) return false
        state.takenItems += stack.copy()
        return true
    }

    private fun isTakenAtTheEnd(stack: ItemStack): Boolean =
        stack.`is`(ModItems.DIORITINE_AXE) || stack.`is`(ModItems.DIORITINE_INGOT) || stack.`is`(ModItems.DIORITE_CRYSTAL)
}
