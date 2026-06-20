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
        
        // Map uv to your custom 200x20 single bar
        val uvOuter = Rect(0.px, 0.px, 200.px, 20.px)
        val image = ImageHolder("kotlinmcuibackend:textures/gui/slider.png", 200.px, 20.px)
        
        // Procedural tint to handle active, hover, and disabled states dynamically
        val finalColor = when {
            !active -> color.change(
                r = (color.rInt * 0.5).toInt(), 
                g = (color.gInt * 0.5).toInt(), 
                b = (color.bInt * 0.5).toInt()
            ) // Darkened/dimmed for disabled
            highlighted -> color.change(
                r = minOf(255, (color.rInt * 1.25).toInt()), 
                g = minOf(255, (color.gInt * 1.25).toInt()), 
                b = minOf(255, (color.bInt * 1.25).toInt())
            ) // Brightened highlight for hover
            else -> color
        }
        
        // Shrunk nine-slice border inset to -1px to match your slider.png.mcmeta border configuration
        ImageStrategy.nineSlice(uvOuter, uvOuter.expand(-1.px), ctx.scale).render(rect, image, finalColor)
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
        // Point to your custom bundled container background texture
        ImageStrategy.nineSlice(
            Rect(0.px, 0.px, 248.px, 166.px), Rect(3.px, 3.px, 245.px, 163.px), ctx.scale
        ).render(
            rect, 
            ImageHolder("kotlinmcuibackend:textures/gui/demo_background.png", 256.px, 256.px), 
            Color.WHITE
        )
    }

    context(renderParam: GuiGraphicsExtractor, ctx: DslScaleContext)
    override fun renderSlot(rect: Rect) {
        // Point to your custom slot layout inside inventory.png
        ImageStrategy.nineSlice(
            Rect(7.px, 141.px, 25.px, 159.px), Rect(8.px, 142.px, 24.px, 158.px), ctx.scale
        ).render(
            rect, 
            ImageHolder("kotlinmcuibackend:textures/gui/inventory.png", 256.px, 256.px), 
            Color.WHITE
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
        if (!itemOpt.isPresent) {
            renderImage(ImageHolder("missing", 16.px, 16.px), rect, Rect(0.px, 0.px, 16.px, 16.px), Color.WHITE)
        } else {
            stack {
                val itemStack = itemOpt.get().defaultInstance.also {
                    it.count = count
                    if (damage != null) {
                        it.damageValue = (damage * it.maxDamage).roundToInt()
                    }
                    if (enchanted) it.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                }
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
        val r = (rect / guiScale).toInt()
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
        
        // Dynamic textures render seamlessly with 26.1 scaling blit
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
        // Point to your custom dark tiled background texture.
        // Color(0.25, 0.25, 0.25) multiplies your brown tiled background to darken it cleanly.
        ImageStrategy.repeat(scale = ctx.scale).render(
            rect,
            ImageHolder("kotlinmcuibackend:textures/gui/background.png", 32.px, 32.px),
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
            
            val xPos = x.raw.toInt()
            val yPos = y.raw.toInt()

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