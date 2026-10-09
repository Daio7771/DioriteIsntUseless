package daio7771.dioriteisntuseless.client.abuse

import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

/**
 * Créditos del final del Abuse Mode (HORROR_DESIGN.md, apartado 5.3).
 *
 * Regla de oro 2: nada aparece de golpe. El fondo se oscurece poco a poco sobre el juego (sin
 * salto de brillo) y se queda quieto; cada línea aparece con un fundido largo; al final, el botón
 * "Start over". Hasta que el botón está del todo, Escape no hace nada. Pausa el juego en un solo
 * jugador.
 *
 * @param next la pantalla que iba a abrirse (pausa o cama), a la que se vuelve si se cierra con
 *   Escape sin empezar de nuevo; null si se han abierto solos (se vuelve al juego).
 */
class CreditsScreen(private val next: Screen?) : Screen(Component.translatable("$LANG.3")) {

    /** null = línea en blanco. */
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
        // Fondo: de transparente a casi negro, despacio; luego, quieto.
        val background = fade(elapsed, 0L, BACKGROUND_FADE_MS)
        graphics.fill(0, 0, width, height, argb(background, 0x080808))

        var textIndex = 0
        lines.forEachIndexed { i, line ->
            if (line == null) return@forEachIndexed
            val alpha = fade(elapsed, FIRST_LINE_MS + textIndex * LINE_STAGGER_MS, LINE_FADE_MS)
            textIndex++
            // Con alfa casi 0 el texto se pintaría opaco (así funciona Font): mejor no pintarlo.
            if (alpha * 255 >= MIN_TEXT_ALPHA) {
                graphics.drawCenteredString(font, line, width / 2, top + i * LINE_HEIGHT, argb(alpha, TEXT_COLOR))
            }
        }
        updateButton()
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    /** El botón aparece con un fundido cuando ya están todas las líneas, y solo entonces funciona. */
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

        // Tiempos (ms): todo lento y suave.
        const val BACKGROUND_FADE_MS = 3_000L
        const val FIRST_LINE_MS = 2_500L
        const val LINE_STAGGER_MS = 2_000L
        const val LINE_FADE_MS = 3_000L
        const val BUTTON_DELAY_MS = 1_500L
        const val BUTTON_FADE_MS = 2_000L

        fun line(n: Int): Component = Component.translatable("$LANG.$n")

        /** De 0 a 1 entre [start] y [start] + [duration], con entrada y salida suaves. */
        fun fade(elapsed: Long, start: Long, duration: Long): Float {
            val x = Mth.clamp((elapsed - start).toFloat() / duration, 0f, 1f)
            return x * x * (3 - 2 * x)
        }

        fun argb(alpha: Float, rgb: Int): Int = (Mth.clamp((alpha * 255).toInt(), 0, 255) shl 24) or rgb
    }
}
