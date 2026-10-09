package daio7771.dioriteisntuseless.client.warning

import net.minecraft.ChatFormatting
import net.minecraft.Util
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.MultiLineLabel
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import kotlin.math.min

/**
 * Psychological horror warning, before the main menu and only once (see WarningGate).
 *
 * Static on purpose (golden rule 2): plain background, no animations. The button goes from
 * disabled to enabled after [BUTTON_DELAY_MS] so it is not skipped without reading, and Escape does
 * not close it.
 */
class WarningScreen(private val next: Screen) : Screen(Component.translatable("$LANG.title").withStyle(ChatFormatting.YELLOW)) {

    private val body = Component.translatable("$LANG.body")
    private val openedAt = Util.getMillis()
    private var bodyLabel = MultiLineLabel.EMPTY
    private var bodyTop = 0
    private var button: Button? = null

    override fun init() {
        bodyLabel = MultiLineLabel.create(font, body, min(width - 40, MAX_TEXT_WIDTH))
        val lineHeight = font.lineHeight + 2
        // Title, body and button, centered vertically as one block.
        val total = font.lineHeight + GAP + bodyLabel.lineCount * lineHeight + GAP + Button.DEFAULT_HEIGHT
        val top = maxOf(10, (height - total) / 2)
        bodyTop = top + font.lineHeight + GAP
        button = addRenderableWidget(
            Button.builder(Component.translatable("$LANG.button")) { accept() }
                .bounds((width - Button.DEFAULT_WIDTH) / 2, bodyTop + bodyLabel.lineCount * lineHeight + GAP,
                    Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT)
                .build()
        ).also { it.active = canAccept() }
    }

    override fun tick() {
        button?.active = canAccept()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, BACKGROUND)
        graphics.drawCenteredString(font, title, width / 2, bodyTop - GAP - font.lineHeight, 0xFFFFFF)
        bodyLabel.renderCentered(graphics, width / 2, bodyTop, font.lineHeight + 2, 0xD0D0D0)
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun shouldCloseOnEsc(): Boolean = false

    override fun getNarrationMessage(): Component = CommonComponents.joinForNarration(title, body)

    private fun canAccept(): Boolean = Util.getMillis() - openedAt >= BUTTON_DELAY_MS

    private fun accept() {
        if (!canAccept()) return
        WarningGate.markShown()
        minecraft?.setScreen(next)
    }

    private companion object {
        const val LANG = "screen.dioriteisntuseless.warning"
        const val BUTTON_DELAY_MS = 4_000L
        const val MAX_TEXT_WIDTH = 360
        const val GAP = 20
        const val BACKGROUND = 0xFF0C0C0C.toInt()
    }
}
