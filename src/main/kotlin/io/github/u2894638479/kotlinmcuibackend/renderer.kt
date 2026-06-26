package io.github.u2894638479.kotlinmcuibackend

import com.mojang.blaze3d.systems.RenderSystem
import io.github.u2894638479.kotlinmcui.backend.DslBackendRenderer
import io.github.u2894638479.kotlinmcui.context.DslScaleContext
import io.github.u2894638479.kotlinmcui.image.ImageHolder
import io.github.u2894638479.kotlinmcui.image.ImageStrategy
import io.github.u2894638479.kotlinmcui.math.Color
import io.github.u2894638479.kotlinmcui.math.Measure
import io.github.u2894638479.kotlinmcui.math.px
import io.github.u2894638479.kotlinmcui.math.rect.*
import io.github.u2894638479.kotlinmcui.math.transform.Transform
import io.github.u2894638479.kotlinmcui.text.DslFont
import io.github.u2894638479.kotlinmcui.text.DslGlyph
import io.github.u2894638479.kotlinmcui.text.DslRenderableChar
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack
import org.joml.Matrix3x2f
import kotlin.math.roundToInt

internal val renderer = object : DslBackendRenderer<GuiGraphicsExtractor> {
    override val guiScale get() = Minecraft.getInstance().window.guiScale.toDouble()

    context(renderParam: GuiGraphicsExtractor, ctx: DslScaleContext)
    override fun renderButton(rect: Rect, highlighted: Boolean, active: Boolean, color: Color) {
        if (rect.isEmpty) return
        val r = rect.toInt()
        
        val spriteName = when {
            !active -> "widget/button_disabled"
            highlighted -> "widget/button_highlighted"
            else -> "widget/button"
        }
        
        // blitSprite with direct ARGB color parameter in 26.1
        renderParam.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            Identifier.parse("minecraft:$spriteName"),
            r.left, r.top, r.width, r.height,
            color.argbInt
        )
    }

    context(renderParam: GuiGraphicsExtractor)
    override fun fillRect(rect: Rect, color: Color) {
        val r = rect.toInt()
        renderParam.fill(r.left, r.top, r.right, r.bottom, color.argbInt)
    }

    context(renderParam: GuiGraphicsExtractor)
    override fun fillRectGradient(rect: Rect, lt: Color, rt: Color, lb: Color, rb: Color) {
        fillRect(rect, lt)
    }

    context(renderParam: GuiGraphicsExtractor, ctx: DslScaleContext)
    override fun renderContainer(rect: Rect) {
        if (rect.isEmpty) return
        val r = rect.toInt()
        
        renderParam.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            Identifier.parse("minecraft:popup/background"),
            r.left, r.top, r.width, r.height,
            0xFFFFFFFF.toInt() // opaque white tint
        )
    }

    context(renderParam: GuiGraphicsExtractor, ctx: DslScaleContext)
    override fun renderSlot(rect: Rect) {
        if (rect.isEmpty) return
        val r = rect.toInt()
        
        renderParam.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            Identifier.parse("minecraft:container/slot"),
            r.left, r.top, r.width, r.height,
            0xFFFFFFFF.toInt()
        )
    }

    context(renderParam: GuiGraphicsExtractor, ctx: DslScaleContext)
    override fun renderTooltip(rect: Rect) {
        stack {
            renderParam.pose().scale(ctx.scale.toFloat(), ctx.scale.toFloat())
            val r = rect.div(ctx.scale).toInt().ifEmpty { return@stack }
            TooltipRenderUtil.extractTooltipBackground(renderParam, r.left, r.top, r.width, r.height, null)
        }
    }

    context(renderParam: GuiGraphicsExtractor, ctx: DslScaleContext)
    override fun renderItem(rect: Rect, item: String, count: Int, damage: Double?, enchanted: Boolean) {
        val itemOpt = BuiltInRegistries.ITEM.getOptional(Identifier.parse(item))
        val itemStackOrNull = if (!itemOpt.isPresent) null else try {
            itemOpt.get().defaultInstance.also {
                it.count = count
                if (damage != null) {
                    it.damageValue = (damage * it.maxDamage).roundToInt()
                }
                if (enchanted) it.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
            }
        } catch (e: NullPointerException) {
            // Holder<Item>.itemComponents() (and thus getDefaultInstance()) NPEs with
            // "Components not bound yet" when this Holder's component-binding pass hasn't
            // run for the current registry instance — observed during extractRenderState,
            // i.e. before client startup's registry/component bootstrap has fully completed.
            // Degrade to the missing-item placeholder instead of crashing the frame.
            null
        }
        if (itemStackOrNull == null) {
            renderImage(ImageHolder("missing", 16.px, 16.px), rect, Rect(0.px, 0.px, 16.px, 16.px), Color.WHITE)
        } else {
            stack {
                val itemStack = itemStackOrNull
                val r = rect.toDouble().ifEmpty { return@stack }
                renderParam.pose().translate(r.left.toFloat(), r.top.toFloat())
                renderParam.pose().scale((r.width / 16.0).toFloat(), (r.height / 16.0).toFloat())

                renderParam.item(itemStack, 0, 0)
                val countStr = if (count > 1) count.toString() else null
                renderParam.itemDecorations(Minecraft.getInstance().font, itemStack, 0, 0, countStr)
            }
        }
    }

    context(renderParam: GuiGraphicsExtractor)
    override fun withScissor(rect: Rect, block: () -> Unit) {
        // enableScissor expects the same logical GUI-scaled coordinate space as every
        // other draw call (blit/blitSprite/fill) — it multiplies by guiScale itself
        // internally to get physical pixels. Dividing by guiScale here was double-scaling:
        // correct by coincidence at 1x, increasingly wrong (over-clipped) at higher scales.
        val r = rect.toInt()
        renderParam.enableScissor(r.left, r.top, r.right, r.bottom)
        try {
            block()
        } finally {
            renderParam.disableScissor()
        }
    }

    context(renderParam: GuiGraphicsExtractor)
    override fun withTransform(transform: Transform, block: () -> Unit) {
        renderParam.pose().pushMatrix()
        renderParam.pose().mul(
            Matrix3x2f(
                transform.m00, transform.m01,
                transform.m10, transform.m11,
                transform.m02, transform.m12
            )
        )
        try {
            block()
        } finally {
            renderParam.pose().popMatrix()
        }
    }

    context(renderParam: GuiGraphicsExtractor)
    private inline fun stack(block: () -> Unit) {
        renderParam.pose().pushMatrix()
        try {
            block()
        } finally {
            renderParam.pose().popMatrix()
        }
    }

    context(renderParam: GuiGraphicsExtractor)
    override fun renderImage(image: ImageHolder, rect: Rect, uv: Rect, color: Color) {
        if (image.isEmpty) return
        val r = rect.toInt()
        val destX = r.left
        val destY = r.top
        val destW = r.width
        val destH = r.height
        
        val u = uv.left.raw.toFloat()
        val v = uv.top.raw.toFloat()

        val imgW = image.width.raw.toInt()
        val imgH = image.height.raw.toInt()
        
        // Correct 11-argument blit method signature for 26.1, including direct ARGB tint
        renderParam.blit(
            RenderPipelines.GUI_TEXTURED,
            Identifier.parse(image.id),
            destX,
            destY,
            u,
            v,
            destW,
            destH,
            imgW,
            imgH,
            color.argbInt
        )
    }

    context(ctx: DslScaleContext, renderParam: GuiGraphicsExtractor)
    override fun renderDefaultBackground(rect: Rect) {
        if (rect.isEmpty) return
        // "minecraft:menu/background" is not a real sprite — there is no sprites/menu/
        // folder in the 26.1 GUI atlas (confirmed by browsing the jar directly).
        // The vanilla menu/options background is a raw tiled texture, not a sprite,
        // so it has to go through ImageStrategy.repeat + Screen.MENU_BACKGROUND
        // instead of blitSprite.
        ImageStrategy.repeat(scale = ctx.scale).render(
            rect,
            ImageHolder(Screen.MENU_BACKGROUND.toString(), 32.px, 32.px),
            Color(0.25, 0.25, 0.25)
        )
    }

    override fun getFont(name: String?) = defaultFont

    val defaultFont = object : DslFont<GuiGraphicsExtractor> {
        val font get() = Minecraft.getInstance().font
        override val lineHeight get() = font.lineHeight.px
        
        override fun glyph(code: Int) = object : DslGlyph {
            override val normalAdvance get() = font.width(code.toChar().toString()).px
            override val boldOffset get() = 1.px
            override val shadowOffset get() = 1.px
        }

        context(renderParam: GuiGraphicsExtractor)
        override fun renderChar(char: DslRenderableChar, x: Measure, y: Measure, effectLeft: Measure, effectRight: Measure) {
            val style = Style.EMPTY
                .withBold(char.style.isBold)
                .withItalic(char.style.isItalic)
                .withUnderlined(char.style.isUnderlined)
                .withStrikethrough(char.style.isStrikeThrough)
                .withObfuscated(char.style.isObfuscated)

            val comp = Component.literal(char.code.toChar().toString()).withStyle(style)
            val shadow = char.style.isShadowed

            // The 1.20.1 backend scaled the pose matrix per-character by
            // (char.size / lineHeight) so DSL text using a custom .size()
            // different from the font's native size actually rendered at
            // that size. This step was dropped during the 26.1 port —
            // renderChar always drew at native font size regardless of
            // char.size — which is why text using a non-default size
            // rendered too small.
            val scale = (char.size.raw / lineHeight.raw).toFloat()
            stack {
                renderParam.pose().scale(scale, scale)
                val xPos = (x.raw / scale).toInt()
                val yPos = (y.raw / scale).toInt()

                renderParam.text(
                    font,
                    comp,
                    xPos,
                    yPos,
                    char.color.argbInt,
                    shadow
                )
            }
        }
    }
}
