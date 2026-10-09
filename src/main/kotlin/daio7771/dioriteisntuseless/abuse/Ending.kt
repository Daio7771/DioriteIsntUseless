package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.abuse.morse.MorseCode
import daio7771.dioriteisntuseless.abuse.morse.MorsePhrases
import daio7771.dioriteisntuseless.abuse.signal.AbuseSignals
import daio7771.dioriteisntuseless.abuse.signal.MorseSignal
import daio7771.dioriteisntuseless.abuse.signal.NonsenseNameSignal
import daio7771.dioriteisntuseless.registry.ModItems
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack

/**
 * The ending (HORROR_DESIGN.md, section 5). It is due when, at level 4 and with THE sign already
 * placed, the player has felled abuseMode.treesUntilEnding trees or made the last number of
 * abuseMode.pickaxeSteps in strikes (or a mix, see AbuseTracker.stepReached):
 *
 * - Phase A, "Diorite Is Useless": the axes, pickaxes, ingots and crystals are taken away
 *   (inventory, armor, offhand, Ender chest, cursor and crafting grid) and kept for
 *   "Start over". From then on, in their hands the axe does not fell and the pickaxe does not mine
 *   3x3, and they cannot take crystals out of the furnace (DioriteUselessness). Final Morse:
 *   DELETE THIS MOD.
 * - Phase B: 2 min 15 s with every item carrying a nonsense name and chat messages that get more
 *   and more broken, ending with USELESS in red every second (EndingMessages). Then one last cave
 *   sound plays (CaveSounds) and the credits are due: their client opens them right then if it is
 *   a calm moment (if not, as soon as it is), and their "Start over" button starts everything over.
 *
 * So nothing is seen vanishing from the hotbar, phase A waits for a moment when they are not
 * looking: on joining the world or on waking up after sleeping. If neither has happened within
 * half a game day, it happens anyway.
 */
object Ending {

    /** Length of phase B: as long as the ending messages take. */
    const val PHASE_B_TICKS = EndingMessages.DURATION_TICKS

    /** If they neither join nor sleep within this time after it is due, phase A happens anyway. */
    private const val FALLBACK_TICKS = AbuseTracker.TICKS_PER_DAY / 2

    /**
     * The last cave sound plays if phase B has just finished, not when joining again with the
     * credits pending (they are announced again, but silently).
     */
    private const val FINAL_SOUND_TOLERANCE_TICKS = 40L

    /** From here on the screen is completely dark while sleeping. */
    private const val DARK_SLEEP_TICKS = 100

    private const val FINAL_MORSE = "DELETE THIS MOD"

    fun init() {
        EntitySleepEvents.STOP_SLEEPING.register { entity, _ ->
            if (entity is ServerPlayer && entity.sleepTimer >= DARK_SLEEP_TICKS && AbuseMode.active) {
                AbuseMode.guard("ending on wake") { startIfDue(entity, AbuseData.get(entity.server)) }
            }
        }
    }

    /**
     * From the player tick (every few ticks): notes when the ending becomes due, the half-day
     * safety net, the phase B messages and, when that phase ends, telling their client the credits
     * are due.
     */
    fun tick(player: ServerPlayer, state: PlayerAbuse) {
        if (state.level == AbuseTracker.MAX_LEVEL && state.endingDueAt < 0 && state.finalSignPlacedAt >= 0 &&
            AbuseTracker.stepReached(state)
        ) {
            state.endingDueAt = state.playTicks
        }
        val due = dueAt(state)
        if (due >= 0 && state.playTicks >= due + FALLBACK_TICKS) start(player, state)
        EndingMessages.tick(player, state)
        if (!state.creditsAnnounced && creditsDue(state)) {
            state.creditsAnnounced = true
            if (state.playTicks - state.endingStartedAt < PHASE_B_TICKS + FINAL_SOUND_TOLERANCE_TICKS) {
                CaveSounds.playFinal(player)
            }
            AbuseStateSync.sync(player)
        }
    }

    /** Phase B is over: the credits open at the next calm moment. */
    fun creditsDue(state: PlayerAbuse): Boolean =
        state.level == AbuseTracker.LEVEL_FINAL && state.endingStartedAt >= 0 &&
            state.playTicks - state.endingStartedAt >= PHASE_B_TICKS

    /** For the testing command: marks phase B as finished (running phase A first if needed). */
    fun skipToCredits(player: ServerPlayer, state: PlayerAbuse) {
        if (state.level != AbuseTracker.LEVEL_FINAL) start(player, state)
        state.endingStartedAt = (state.playTicks - PHASE_B_TICKS).coerceAtLeast(0)
        state.playTicks = state.endingStartedAt + PHASE_B_TICKS
        state.endingMessagesSent = EndingMessages.COUNT
        state.creditsAnnounced = true
        AbuseStateSync.sync(player)
    }

    /** On joining the world: if it is due, it starts; if already at the ending, re-sends the names. */
    fun onJoin(player: ServerPlayer, data: AbuseData) {
        val state = data.getIfPresent(player.uuid) ?: return
        if (isDue(state)) {
            start(player, state)
            data.setDirty()
        } else if (state.level == AbuseTracker.LEVEL_FINAL) {
            NonsenseNameSignal.everything(player)  // their client forgot them on disconnecting
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

    /** playTicks at which the ending became due, or -1 if it is not due yet. */
    private fun dueAt(state: PlayerAbuse): Long =
        if (state.level == AbuseTracker.MAX_LEVEL) state.endingDueAt else -1

    /** Phase A and the start of phase B. Also used by the testing command. */
    fun start(player: ServerPlayer, state: PlayerAbuse) {
        state.level = AbuseTracker.LEVEL_FINAL
        state.treesAtLevel = 0
        state.strikesAtLevel = 0
        state.endingStartedAt = state.playTicks
        state.endingMessagesSent = 0
        state.dioriteUseless = true
        val taken = takeItems(player, state)
        LOGGER.debug("Abuse mode: ending started for {}; {} stack(s) held back.", player.gameProfile.name, taken)
        AbuseStateSync.sync(player)
        NonsenseNameSignal.everything(player)
        MorseSignal.send(player, state, MorsePhrases.Phrase(FINAL_MORSE, MorseCode.encode(FINAL_MORSE).code))
        AbuseSignals.schedule(player, state)
    }

    /** Takes away what the mod gave them and keeps it in [state] for "Start over". Returns how many stacks. */
    private fun takeItems(player: ServerPlayer, state: PlayerAbuse): Int {
        // Whatever was in an open crafting table goes back to the inventory first.
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
        taken += takeFrom(player.inventoryMenu.craftSlots, state)  // the 2x2 inventory crafting grid
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

    /** If [stack] is from the mod, keeps a copy (with its NBT: enchantments, wear...). */
    private fun take(stack: ItemStack, state: PlayerAbuse): Boolean {
        if (stack.isEmpty || !isTakenAtTheEnd(stack)) return false
        state.takenItems += stack.copy()
        return true
    }

    private fun isTakenAtTheEnd(stack: ItemStack): Boolean =
        stack.`is`(ModItems.DIORITINE_AXE) || stack.`is`(ModItems.DIORITINE_PICKAXE) ||
            stack.`is`(ModItems.DIORITINE_INGOT) || stack.`is`(ModItems.DIORITE_CRYSTAL)
}
