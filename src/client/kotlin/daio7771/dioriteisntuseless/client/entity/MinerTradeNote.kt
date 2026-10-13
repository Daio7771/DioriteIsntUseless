package daio7771.dioriteisntuseless.client.entity

import daio7771.dioriteisntuseless.network.MinerNotePacket
import daio7771.dioriteisntuseless.registry.ModItems
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.MerchantScreen
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

/**
 * The note at the top of The Miner's trading screen, with what he wants right now: the job they
 * have from him (and how far along it is), or his phrase of the level (MinerNotePacket).
 *
 * The server sends it right before the screen opens, so it goes on the next trading screen only
 * if that one opens within [PENDING_TICKS] (a villager's later on never gets it). It is a small
 * piece of paper above the trading window, still: nothing moves or blinks. For a job of bringing
 * crystals, the count is the one they carry right now, even while they sell him some.
 */
object MinerTradeNote {

    private const val PENDING_TICKS = 40

    /** The trading window (MerchantScreen), to sit just above it. */
    private const val WINDOW_HEIGHT = 166

    private const val TEXT_WIDTH = 220
    private const val PADDING = 5
    private const val LINE_HEIGHT = 10
    private const val GAP = 3

    private const val PAPER = 0xFFE8DCB4.toInt()
    private const val EDGE = 0xFF8C7A52.toInt()
    private const val INK = 0xFF3A2E1C.toInt()

    private var pending: MinerNotePacket? = null
    private var pendingTicks = 0

    /** The note and the screen it belongs to. */
    private var note: MinerNotePacket? = null
    private var screen: Screen? = null

    fun init() {
        ClientPlayNetworking.registerGlobalReceiver(MinerNotePacket.TYPE) { packet, _, _ ->
            pending = packet
            pendingTicks = PENDING_TICKS
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (pending != null && --pendingTicks <= 0) pending = null
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            pending = null
            note = null
            screen = null
        }
        // Also on every resize: the screen's own events are new each time, so they are added again.
        ScreenEvents.AFTER_INIT.register { _, newScreen, _, _ ->
            if (newScreen !is MerchantScreen) return@register
            pending?.let {
                note = it
                screen = newScreen
                pending = null
            }
            if (newScreen !== screen) return@register
            ScreenEvents.afterRender(newScreen).register { _, graphics, _, _, _ -> draw(newScreen, graphics) }
            ScreenEvents.remove(newScreen).register {
                if (screen === newScreen) {
                    note = null
                    screen = null
                }
            }
        }
    }

    private fun draw(screen: MerchantScreen, graphics: GuiGraphics) {
        val note = note ?: return
        val font = Minecraft.getInstance().font
        val lines = ArrayList<FormattedCharSequence>()
        if (note.header.string.isNotEmpty()) lines += font.split(note.header, TEXT_WIDTH)
        lines += font.split(note.body, TEXT_WIDTH)
        if (note.amount > 0) {
            val progress = if (note.liveCrystals) crystalsCarried(screen) else note.progress
            lines += font.split(
                Component.translatable("note.dioriteisntuseless.miner.progress", progress.coerceAtMost(note.amount), note.amount, note.reward),
                TEXT_WIDTH,
            )
        }
        val width = lines.maxOf { font.width(it) } + PADDING * 2
        val height = lines.size * LINE_HEIGHT + PADDING * 2 - 1
        val left = (screen.width - width) / 2
        // Just above the trading window; on a very small screen, as high as it goes.
        val top = ((screen.height - WINDOW_HEIGHT) / 2 - GAP - height).coerceAtLeast(1)
        graphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, EDGE)
        graphics.fill(left, top, left + width, top + height, PAPER)
        for ((index, line) in lines.withIndex()) {
            graphics.drawString(font, line, left + PADDING, top + PADDING + index * LINE_HEIGHT, INK, false)
        }
    }

    /** Crystals in their inventory, the payment slots and on the cursor (not the result slot: that is his). */
    private fun crystalsCarried(screen: MerchantScreen): Int {
        val menu = screen.menu
        val inSlots = menu.slots.filterIndexed { index, _ -> index != RESULT_SLOT }
            .sumOf { slot -> slot.item.takeIf { it.`is`(ModItems.DIORITE_CRYSTAL) }?.count ?: 0 }
        return inSlots + (menu.carried.takeIf { it.`is`(ModItems.DIORITE_CRYSTAL) }?.count ?: 0)
    }

    /** MerchantMenu: 0 and 1 are the payment, 2 the result, then the player's inventory. */
    private const val RESULT_SLOT = 2
}
