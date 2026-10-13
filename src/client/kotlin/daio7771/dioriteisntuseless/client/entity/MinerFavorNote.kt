package daio7771.dioriteisntuseless.client.entity

import daio7771.dioriteisntuseless.network.MinerNotePacket
import daio7771.dioriteisntuseless.registry.ModItems
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Mth

/**
 * A small piece of paper in the top left corner of the screen while they are near The Miner, with
 * what he wants right now: the favor they have from him and how far along it is, or his phrase of
 * the level (MinerNotePacket, sent by the server whenever it changes).
 *
 * It fades in as they come within [NEAR_DISTANCE] of him and fades out as they walk away or he
 * leaves: never a sudden pop, never a flash. For a favor of bringing crystals, the count is the one
 * they carry right now. Hidden with the rest of the HUD (F1).
 */
object MinerFavorNote {

    private const val NEAR_DISTANCE = 8.0

    /** Alpha change per tick: about a third of a second to appear or disappear. */
    private const val FADE_STEP = 0.15f

    private const val MARGIN = 4
    private const val TEXT_WIDTH = 160
    private const val PADDING = 5
    private const val LINE_HEIGHT = 10

    private const val PAPER = 0xE8DCB4
    private const val EDGE = 0x8C7A52
    private const val INK = 0x3A2E1C

    /** What the note says, and whether it is going away (level 4: an empty one came). */
    private var note: MinerNotePacket? = null
    private var hidden = false
    private var alpha = 0f
    private var alphaO = 0f

    fun init() {
        ClientPlayNetworking.registerGlobalReceiver(MinerNotePacket.TYPE) { packet, _, _ ->
            // An empty one (level 4): no note; the current one fades out.
            if (packet.body.string.isEmpty()) {
                if (note?.minerId == packet.minerId) hidden = true
            } else {
                note = packet
                hidden = false
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            note = null
            hidden = false
            alpha = 0f
            alphaO = 0f
        }
        HudRenderCallback.EVENT.register(::draw)
    }

    private fun tick(minecraft: Minecraft) {
        alphaO = alpha
        val target = if (note != null && !hidden && minerNear(minecraft)) 1f else 0f
        alpha = if (alpha < target) (alpha + FADE_STEP).coerceAtMost(1f) else (alpha - FADE_STEP).coerceAtLeast(0f)
        // Faded out, with him gone or nothing more to say: forgotten (each visit sends a new one).
        if (alpha == 0f && note != null && (hidden || miner(minecraft) == null)) {
            note = null
            hidden = false
        }
    }

    /** The Miner the note is about, if he is still in the world. */
    private fun miner(minecraft: Minecraft) =
        note?.let { minecraft.level?.getEntity(it.minerId) }?.takeUnless { it.isRemoved }

    private fun minerNear(minecraft: Minecraft): Boolean {
        val player = minecraft.player ?: return false
        val miner = miner(minecraft) ?: return false
        return miner.distanceToSqr(player) <= NEAR_DISTANCE * NEAR_DISTANCE
    }

    private fun draw(graphics: GuiGraphics, partialTick: Float) {
        val note = note ?: return
        val alpha = Mth.lerp(partialTick, alphaO, this.alpha)
        // Font treats an alpha under 4 as opaque: below that, nothing is drawn.
        val alphaByte = (alpha * 255).toInt()
        if (alphaByte < 5) return
        val minecraft = Minecraft.getInstance()
        val font = minecraft.font
        val lines = ArrayList<FormattedCharSequence>()
        if (note.header.string.isNotEmpty()) lines += font.split(note.header, TEXT_WIDTH)
        lines += font.split(note.body, TEXT_WIDTH)
        if (note.amount > 0) {
            val progress = if (note.liveCrystals) minecraft.player?.inventory?.countItem(ModItems.DIORITE_CRYSTAL) ?: 0 else note.progress
            lines += font.split(
                Component.translatable("note.dioriteisntuseless.miner.progress", progress.coerceAtMost(note.amount), note.amount, note.reward),
                TEXT_WIDTH,
            )
        }
        if (lines.isEmpty()) return
        val width = lines.maxOf { font.width(it) } + PADDING * 2
        val height = lines.size * LINE_HEIGHT + PADDING * 2 - 1
        val left = MARGIN + 1
        val top = MARGIN + 1
        val shade = alphaByte shl 24
        graphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, shade or EDGE)
        graphics.fill(left, top, left + width, top + height, shade or PAPER)
        for ((index, line) in lines.withIndex()) {
            graphics.drawString(font, line, left + PADDING, top + PADDING + index * LINE_HEIGHT, shade or INK, false)
        }
    }
}
