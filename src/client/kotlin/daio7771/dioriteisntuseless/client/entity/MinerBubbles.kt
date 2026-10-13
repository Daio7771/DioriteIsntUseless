package daio7771.dioriteisntuseless.client.entity

import com.mojang.blaze3d.vertex.PoseStack
import daio7771.dioriteisntuseless.abuse.visitor.MinerSpeech
import daio7771.dioriteisntuseless.abuse.visitor.MinerSpeech.Mood
import daio7771.dioriteisntuseless.network.MinerLinePacket
import daio7771.dioriteisntuseless.registry.ModSounds
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderDispatcher
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity

/**
 * The Miner's speech bubble on this client (the server sends each line with MinerLinePacket, only
 * to the player he visits):
 *
 * - Above his head, always facing the camera, like a name tag; wrapped to [MAX_WIDTH] up front, so
 *   words never jump from one line to the next while it is typed.
 * - Typed letter by letter: 30 a second at levels 0-1; at levels 2-3 slower and irregular, with
 *   hesitations; at level 4 slow and steady.
 * - His voice: a short, low note every 2 letters, with a slight random pitch; at levels 2-3 it
 *   follows the irregular typing and wavers more. At level 4, no voice at all (silence).
 * - Once typed, the line is copied to the chat ("The Miner: ...") so it can be read again.
 * - It stays a few seconds and fades out smoothly; the box fades in when it appears. No flashes.
 * - A line ending in "—" is cut off: when the typing gets there, the voice stops and the bubble is
 *   gone at once, with no fading.
 * - Lines never interrupt each other: one that arrives while another is being typed waits for it.
 *
 * If he disappears with lines still pending, they go straight to the chat. Client thread only.
 */
object MinerBubbles {

    /** Wrap width, in font pixels (1/40 of a block). */
    private const val MAX_WIDTH = 150

    private const val LINE_HEIGHT = 10

    /** Not drawn farther than this (like name tags). */
    private const val MAX_DISTANCE_SQR = 64.0 * 64.0

    /** The box fades in this fast. */
    private const val FADE_IN_TICKS = 4

    /** After it is typed: [HOLD_TICKS] plus [HOLD_TICKS_PER_CHAR] per letter, then [FADE_OUT_TICKS] fading out. */
    private const val HOLD_TICKS = 50
    private const val HOLD_TICKS_PER_CHAR = 0.5f
    private const val FADE_OUT_TICKS = 20

    /** With a line waiting: it gives way after [MIN_READ_TICKS], fading out in [QUICK_FADE_TICKS]. */
    private const val MIN_READ_TICKS = 20
    private const val QUICK_FADE_TICKS = 6

    /** A cut-off line freezes on its "—" for this long, then is gone at once. */
    private const val CUT_HOLD_TICKS = 6

    /** Levels 2-3: per tick, a random speed between these, and sometimes a hesitation. */
    private const val EAGER_MIN_SPEED = 0.35f
    private const val EAGER_MAX_SPEED = 1.3f
    private const val EAGER_HESITATION_CHANCE = 0.05f
    private const val EAGER_HESITATION_MIN_TICKS = 4
    private const val EAGER_HESITATION_EXTRA_TICKS = 10

    /** His voice: one note every [LETTERS_PER_BLIP] letters; pitch around 1 (sounds.json lowers it), more uneven at levels 2-3. */
    private const val LETTERS_PER_BLIP = 2
    private const val CALM_PITCH_SPREAD = 0.06f
    private const val EAGER_PITCH_SPREAD = 0.14f
    private const val EAGER_PITCH = 0.92f

    private const val CUT_MARK = "—"

    private class Bubble(val minerId: Int, val line: Component, val mood: Mood) {
        val text: String = line.string.trimEnd()
        val cut = text.endsWith(CUT_MARK)

        /** Wrapped once, when it starts (it needs the font). */
        var lines: List<String> = emptyList()
        var width = 0
        val length: Int get() = lines.sumOf { it.length }

        var age = 0
        var shown = 0f
        var hesitation = 0
        var letters = 0
        var typed = false
        var typedAt = 0
        var inChat = false

        /** Fading out since [fadeFrom] for [fadeTicks], or not yet ([fadeFrom] -1). */
        var fadeFrom = -1
        var fadeTicks = FADE_OUT_TICKS

        val holdTicks: Int get() = HOLD_TICKS + (text.length * HOLD_TICKS_PER_CHAR).toInt()

        fun alpha(partialTick: Float): Float {
            val time = age + partialTick
            val fadeIn = (time / FADE_IN_TICKS).coerceIn(0f, 1f)
            val fadeOut = if (fadeFrom < 0) 1f else 1f - ((time - fadeFrom) / fadeTicks).coerceIn(0f, 1f)
            return fadeIn * fadeOut
        }
    }

    private var current: Bubble? = null
    private val waiting = ArrayDeque<Bubble>()
    private val random = RandomSource.create()

    fun init() {
        ClientPlayNetworking.registerGlobalReceiver(MinerLinePacket.TYPE) { packet, _, _ ->
            waiting += Bubble(packet.minerId, packet.line, packet.mood)
        }
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            current = null
            waiting.clear()
        }
    }

    private fun tick(minecraft: Minecraft) {
        val level = minecraft.level
        if (level == null) {
            current = null
            waiting.clear()
            return
        }
        if (minecraft.isPaused) return
        val bubble = current ?: waiting.removeFirstOrNull()?.also { start(minecraft, it) } ?: return
        val miner = level.getEntity(bubble.minerId)
        if (miner == null || miner.isRemoved) {
            // He is gone (or out of reach): whatever he had to say goes straight to the chat.
            toChat(minecraft, bubble)
            waiting.forEach { toChat(minecraft, it) }
            current = null
            waiting.clear()
            return
        }
        bubble.age++
        if (!bubble.typed) {
            type(bubble, miner)
            if (bubble.typed) {
                bubble.typedAt = bubble.age
                toChat(minecraft, bubble)
            }
            return
        }
        val since = bubble.age - bubble.typedAt
        when {
            bubble.cut -> if (since >= CUT_HOLD_TICKS) current = null
            bubble.fadeFrom >= 0 -> if (bubble.age >= bubble.fadeFrom + bubble.fadeTicks) current = null
            waiting.isNotEmpty() && since >= MIN_READ_TICKS -> {
                bubble.fadeFrom = bubble.age
                bubble.fadeTicks = QUICK_FADE_TICKS
            }
            since >= bubble.holdTicks -> {
                bubble.fadeFrom = bubble.age
                bubble.fadeTicks = FADE_OUT_TICKS
            }
        }
    }

    private fun start(minecraft: Minecraft, bubble: Bubble) {
        val font = minecraft.font
        bubble.lines = font.splitter.splitLines(bubble.text, MAX_WIDTH, Style.EMPTY).map { it.string }
        bubble.width = bubble.lines.maxOfOrNull(font::width) ?: 0
        current = bubble
    }

    /** One tick of typing: the letters that appear, and his voice on every [LETTERS_PER_BLIP]. */
    private fun type(bubble: Bubble, miner: Entity) {
        val before = bubble.shown.toInt()
        bubble.shown = (bubble.shown + speed(bubble)).coerceAtMost(bubble.length.toFloat())
        val now = bubble.shown.toInt()
        var blip = false
        for (index in before until now) {
            val char = charAt(bubble, index)
            // The "—" of a cut-off line makes no sound: the voice stops dead with the text.
            if (char.isWhitespace() || (bubble.cut && index == bubble.length - 1)) continue
            if (bubble.letters++ % LETTERS_PER_BLIP == 0) blip = true
        }
        // At most one note per tick, even when two letters fall in it.
        if (blip) voice(bubble, miner)
        if (now >= bubble.length) bubble.typed = true
    }

    private fun speed(bubble: Bubble): Float = when (bubble.mood) {
        Mood.CALM -> MinerSpeech.CALM_CHARS_PER_TICK
        Mood.FAREWELL -> MinerSpeech.FAREWELL_CHARS_PER_TICK
        Mood.EAGER -> when {
            bubble.hesitation > 0 -> {
                bubble.hesitation--
                0f
            }
            random.nextFloat() < EAGER_HESITATION_CHANCE -> {
                bubble.hesitation = EAGER_HESITATION_MIN_TICKS + random.nextInt(EAGER_HESITATION_EXTRA_TICKS + 1)
                0f
            }
            else -> Mth.lerp(random.nextFloat(), EAGER_MIN_SPEED, EAGER_MAX_SPEED)
        }
    }

    private fun charAt(bubble: Bubble, index: Int): Char {
        var rest = index
        for (line in bubble.lines) {
            if (rest < line.length) return line[rest]
            rest -= line.length
        }
        return ' '
    }

    /** One note of his voice, from his head. Level 4: silence. */
    private fun voice(bubble: Bubble, miner: Entity) {
        val pitch = when (bubble.mood) {
            Mood.CALM -> 1f + (random.nextFloat() * 2 - 1) * CALM_PITCH_SPREAD
            Mood.EAGER -> EAGER_PITCH + (random.nextFloat() * 2 - 1) * EAGER_PITCH_SPREAD
            Mood.FAREWELL -> return
        }
        miner.level().playLocalSound(miner.x, miner.eyeY, miner.z, ModSounds.MINER_VOICE, SoundSource.NEUTRAL, 1f, pitch, false)
    }

    private fun toChat(minecraft: Minecraft, bubble: Bubble) {
        if (bubble.inChat) return
        bubble.inChat = true
        minecraft.gui.chat.addMessage(MinerSpeech.chatLine(bubble.line))
    }

    /**
     * From VisitorRenderer, with [poseStack] at The Miner's feet: his bubble, if he has one. Drawn
     * like a name tag (a faint pass that shows through blocks, with the box, and a normal one),
     * at full brightness so it can be read in a dark mine.
     */
    fun render(
        miner: Entity, poseStack: PoseStack, buffers: MultiBufferSource, partialTick: Float,
        dispatcher: EntityRenderDispatcher, font: Font,
    ) {
        val bubble = current ?: return
        if (bubble.minerId != miner.id || bubble.lines.isEmpty() || dispatcher.distanceToSqr(miner) > MAX_DISTANCE_SQR) return
        val alpha = bubble.alpha(partialTick)
        // Font treats an alpha under 4 as opaque: below that, nothing is drawn.
        val textAlpha = (alpha * 255).toInt()
        if (textAlpha < 5) return
        val throughAlpha = (alpha * SEE_THROUGH_ALPHA).toInt()
        val background = (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * alpha * 255).toInt()

        poseStack.pushPose()
        poseStack.translate(0.0, miner.bbHeight + 0.5, 0.0)
        poseStack.mulPose(dispatcher.cameraOrientation())
        poseStack.scale(-0.025f, -0.025f, 0.025f)
        val matrix = poseStack.last().pose()
        val height = bubble.lines.size * LINE_HEIGHT
        val left = -bubble.width / 2f
        val top = -height.toFloat()
        if (background > 0) {
            val color = background shl 24
            val box = buffers.getBuffer(RenderType.textBackgroundSeeThrough())
            // Just behind the text, like the box vanilla draws behind a name tag.
            box.vertex(matrix, left - 1, top - 1, BOX_DEPTH).color(color).uv2(LIGHT).endVertex()
            box.vertex(matrix, left - 1, 0f, BOX_DEPTH).color(color).uv2(LIGHT).endVertex()
            box.vertex(matrix, left + bubble.width + 1, 0f, BOX_DEPTH).color(color).uv2(LIGHT).endVertex()
            box.vertex(matrix, left + bubble.width + 1, top - 1, BOX_DEPTH).color(color).uv2(LIGHT).endVertex()
        }
        var remaining = bubble.shown.toInt()
        for ((index, line) in bubble.lines.withIndex()) {
            if (remaining <= 0) break
            val visible = line.take(remaining)
            remaining -= line.length
            val y = top + index * LINE_HEIGHT
            if (throughAlpha >= 5) {
                font.drawInBatch(visible, left, y, (throughAlpha shl 24) or TEXT_RGB, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, 0, LIGHT)
            }
            font.drawInBatch(visible, left, y, (textAlpha shl 24) or TEXT_RGB, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, LIGHT)
        }
        poseStack.popPose()
    }

    private const val TEXT_RGB = 0xFFFFFF

    private const val BOX_DEPTH = 0.01f

    /** The faint pass that shows through blocks, like a name tag's. */
    private const val SEE_THROUGH_ALPHA = 0x20

    private const val LIGHT = LightTexture.FULL_BRIGHT
}
