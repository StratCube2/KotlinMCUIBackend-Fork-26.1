package io.github.u2894638479.kotlinmcuibackend

import io.github.u2894638479.kotlinmcui.DslDataStore
import io.github.u2894638479.kotlinmcui.InternalBackend
import io.github.u2894638479.kotlinmcui.backend.*
import io.github.u2894638479.kotlinmcui.context.DslScaleContext
import io.github.u2894638479.kotlinmcui.context.scaled
import io.github.u2894638479.kotlinmcui.functions.DslTopFunction
import io.github.u2894638479.kotlinmcui.glfw.EventModifier
import io.github.u2894638479.kotlinmcui.glfw.MouseButton
import io.github.u2894638479.kotlinmcui.math.Position
import io.github.u2894638479.kotlinmcui.math.px
import io.github.u2894638479.kotlinmcui.math.rect.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.nio.file.Path

internal var eventModifier: Int = 0

internal var horizontalScroller: ((Double, Double, Double) -> Unit)? = null

@InternalBackend
val defaultBackend: DslBackend<GuiGraphicsExtractor, Screen> = object : DslBackend<GuiGraphicsExtractor, Screen>,
    DslBackendRenderer<GuiGraphicsExtractor> by renderer,
    DslBackendMetadata by metadata,
    DslBackendUtils by utils
{
    override fun create(title: String, dslFunction: DslTopFunction): DslBackendScreenHolder<Screen> = object : DslBackendScreenHolder<Screen> {
        override fun show() {
            Minecraft.getInstance().execute {
                Minecraft.getInstance().setScreen(screen)
            }
        }
        override val screen = object : Screen(Component.literal(title)), DslScaleContext {
            override val scale get() = guiScale
            val parent = Minecraft.getInstance().screen
            fun DslBackend<*, *>.createDataStore() = DslDataStore(this, title, {
                Minecraft.getInstance().execute { Minecraft.getInstance().setScreen(parent) }
            }, dslFunction)
            val dataStore = createDataStore()
            val dslScreen = dataStore.dslScreen
            override fun onClose() {
                horizontalScroller = null
                dslScreen.close()
            }

            override fun isPauseScreen() = dataStore.pauseGame

            override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
                if (context(EventModifier(event.modifiers())) { dslScreen.keyDown(event.key(), event.scancode()) }) return true
                return super.keyPressed(event)
            }

            override fun keyReleased(event: net.minecraft.client.input.KeyEvent): Boolean {
                if (context(EventModifier(event.modifiers())) { dslScreen.keyUp(event.key(), event.scancode()) }) return true
                return super.keyReleased(event)
            }

            override fun mouseClicked(event: net.minecraft.client.input.MouseButtonEvent, bl: Boolean): Boolean {
                if (context(EventModifier(event.modifiers()), Position(event.x().scaled, event.y().scaled)) {
                    dslScreen.mouseDown(MouseButton.from(event.button()))
                }) return true
                return super.mouseClicked(event, bl)
            }

            // Fixed signature for 26.1
            override fun mouseReleased(event: net.minecraft.client.input.MouseButtonEvent): Boolean {
                if (context(EventModifier(event.modifiers()), Position(event.x().scaled, event.y().scaled)) {
                    dslScreen.mouseUp(MouseButton.from(event.button()))
                }) return true
                return super.mouseReleased(event)
            }

            override fun mouseMoved(d: Double, e: Double) {
                context(Position(d.scaled, e.scaled)) { dslScreen.mouseMove() }
                super.mouseMoved(d, e)
            }

            override fun mouseScrolled(d: Double, e: Double, scrollX: Double, scrollY: Double): Boolean {
                val remain = context(Position(d.scaled, e.scaled)) {
                    dslScreen.mouseScrollVertical(scrollY)
                }
                if (remain == 0.0) return true
                return super.mouseScrolled(d, e, scrollX, remain)
            }

            override fun charTyped(event: net.minecraft.client.input.CharacterEvent): Boolean {
                val charVal = event.codepoint().toChar()
                // Some Android keyboard/launcher input bridges (e.g. Zalith Launcher) forward
                // control characters like '\n' through charTyped instead of a proper key event.
                // A single-line text field should never accept these, so drop them here.
                if (Character.isISOControl(charVal)) return true
                if (context(EventModifier(0)) { dslScreen.charTyped(charVal) }) return true
                return super.charTyped(event)
            }

            override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, i: Int, j: Int, f: Float) {
                context(guiGraphics, Position(i.scaled, j.scaled)) {
                    guiGraphics.pose().pushMatrix()
                    guiGraphics.pose().scale(1 / guiScale.toFloat(), 1 / guiScale.toFloat())
                    dslScreen.render()
                    guiGraphics.pose().popMatrix()
                }
            }

            override fun onFilesDrop(list: List<Path>) {
                if (context(dataStore.mouse) { dslScreen.dropFiles(list) }) return
                return super.onFilesDrop(list)
            }

            override fun init() {
                super.init()
                horizontalScroller = { x, y, f ->
                    context(Position(x.px, y.px)) {
                        dslScreen.mouseScrollHorizontal(f)
                    }
                }
                dslScreen.init(Rect(right = width.scaled, bottom = height.scaled))
            }
        }
    }
}