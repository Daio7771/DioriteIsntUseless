package daio7771.dioriteisntuseless.client.abuse

import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

/**
 * Credits of the Abuse Mode ending (HORROR_DESIGN.md, section 5.3).
 *
 * Golden rule 2: nothing appears suddenly. The background darkens little by little over the game
 * (with no jump in brightness) and then stays still; each line appears with a long fade; at the
 * end, the "Start over" button. Until the button is fully there, Escape does nothing. Pauses the
 * game in single player.
 *
 * @param next the screen that was about to open (pause or bed), which it goes back to if closed
 *   with Escape without starting over; null if they opened on their own (back to the game).
 */
class CreditsScreen(private val next: Screen?) : Screen(Component.translatable("$LANG.3")) {

    /** null = blank line. */
    private val lines: List<Component?> = listOf(line(1), line(2), null, line(3), line(4), line(5), null, line(6))
    private val textLineCount = lines.count { it != null }

    private val openedAt = Util.getMillis()
    private var top = 0
    private var button: Button? = null

    override fun init() {
        val total = lines.size * LINE_HEIGHT + GAP + Button.DEFAULT_HEIGHT
        top = maxOf(10, (height - total) / 2)
        button = addRenderableWidget(
            Button.builder(Component.translatable("$LANG.button")) { startOver() }
                .bounds((width - Button.DEFAULT_WIDTH) / 2, top + lines.size * LINE_HEIGHT + GAP,
                    Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT)
                .build()
        )
        updateButton()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val elapsed = elapsed()
        // Background: from transparent to almost black, slowly; then, still.
        val background = fade(elapsed, 0L, BACKGROUND_FADE_MS)
        graphics.fill(0, 0, width, height, argb(background, 0x080808))

        var textIndex = 0
        lines.forEachIndexed { i, line ->
            if (line == null) return@forEachIndexed
            val alpha = fade(elapsed, FIRST_LINE_MS + textIndex * LINE_STAGGER_MS, LINE_FADE_MS)
            textIndex++
            // With an alpha close to 0 the text would be drawn opaque (that is how Font works): better not to draw it.
            if (alpha * 255 >= MIN_TEXT_ALPHA) {
                graphics.drawCenteredString(font, line, width / 2, top + i * LINE_HEIGHT, argb(alpha, TEXT_COLOR))
            }
        }
        updateButton()
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    /** The button fades in once every line is there, and only then does it work. */
    private fun updateButton() {
        val button = button ?: return
        val alpha = fade(elapsed(), buttonAt(), BUTTON_FADE_MS)
        button.visible = alpha > 0f
        button.setAlpha(alpha)
        button.active = alpha >= 1f
    }

    override fun shouldCloseOnEsc(): Boolean = button?.active == true

    override fun onClose() {
        CreditsGate.onDismissed(next)
        minecraft?.setScreen(next)
    }

    override fun isPauseScreen(): Boolean = true

    override fun getNarrationMessage(): Component = CommonComponents.joinForNarration(*lines.filterNotNull().toTypedArray())

    private fun startOver() {
        if (button?.active != true) return
        CreditsGate.onStartOver()
        minecraft?.setScreen(null)
    }

    private fun elapsed(): Long = Util.getMillis() - openedAt

    private fun buttonAt(): Long = FIRST_LINE_MS + (textLineCount - 1) * LINE_STAGGER_MS + LINE_FADE_MS + BUTTON_DELAY_MS

    private companion object {
        const val LANG = "screen.dioriteisntuseless.credits"
        const val LINE_HEIGHT = 14
        const val GAP = 24
        const val TEXT_COLOR = 0xD8D8D8
        const val MIN_TEXT_ALPHA = 5f

        // Timings (ms): everything slow and smooth.
        const val BACKGROUND_FADE_MS = 3_000L
        const val FIRST_LINE_MS = 2_500L
        const val LINE_STAGGER_MS = 2_000L
        const val LINE_FADE_MS = 3_000L
        const val BUTTON_DELAY_MS = 1_500L
        const val BUTTON_FADE_MS = 2_000L

        fun line(n: Int): Component = Component.translatable("$LANG.$n")

        /** From 0 to 1 between [start] and [start] + [duration], easing in and out. */
        fun fade(elapsed: Long, start: Long, duration: Long): Float {
            val x = Mth.clamp((elapsed - start).toFloat() / duration, 0f, 1f)
            return x * x * (3 - 2 * x)
        }

        fun argb(alpha: Float, rgb: Int): Int = (Mth.clamp((alpha * 255).toInt(), 0, 255) shl 24) or rgb
    }
}
