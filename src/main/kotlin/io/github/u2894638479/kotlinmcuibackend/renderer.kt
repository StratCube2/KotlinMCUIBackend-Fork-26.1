package io.github.u2894638479.kotlinmcuibackend

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexConsumer
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
import net.minecraft.client.renderer.RenderType
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

    private fun VertexConsumer.color(color: Color) = color(color.rInt, color.gInt, color.bInt, color.aInt)

    context(renderParam: GuiGraphicsExtractor, ctx: DslScaleContext)
    override fun renderButton(rect: Rect, highlighted: Boolean, active: Boolean, color: Color) {
        if (rect.isEmpty) return
        val r = rect.toInt()
        
        // Point to the native vanilla button sprites we saw in your JAR folder
        val spriteName = when {
            !active -> "widget/button_disabled"
            highlighted -> "widget/button_highlighted"
            else -> "widget/button"
        }
        
        // blitSprite reads the .mcmeta and automatically applies the 9-slice borders!
        renderParam.blitSprite(
            RenderType::guiTextured,
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
        
        // Uses native popup/background from the JAR with auto 9-slice
        renderParam.blitSprite(
            RenderType::guiTextured,
            Identifier.parse("minecraft:popup/background"),
            r.left, r.top, r.width, r.height,
            Color.WHITE.argbInt
        )
    }

    context(renderParam: GuiGraphicsExtractor, ctx: DslScaleContext)
    override fun renderSlot(rect: Rect) {
        if (rect.isEmpty) return
        val r = rect.toInt()
        
        // Uses native container/slot from the JAR with auto 9-slice
        renderParam.blitSprite(
            RenderType::guiTextured,
            Identifier.parse("minecraft:container/slot"),
            r.left, r.top, r.width, r.height,
            Color.WHITE.argbInt
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
        if (!itemOpt.isPresent) return
        
        stack {
            val itemStack = try {
                val baseItem = itemOpt.get()
                // Prevent crash: Cannot generate DefaultInstance for Air
                if (baseItem == net.minecraft.world.item.Items.AIR) return@stack
                
                ItemStack(baseItem, count).also {
                    if (damage != null) {
                        it.damageValue = (damage * it.maxDamage).roundToInt()
                    }
                    if (enchanted) it.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                }
            } catch (e: Exception) {
                null
            }

            if (itemStack == null) return@stack

            val r = rect.toDouble().ifEmpty { return@stack }
            renderParam.pose().translate(r.left.toFloat(), r.top.toFloat())
            renderParam.pose().scale((r.width / 16.0).toFloat(), (r.height / 16.0).toFloat())

            renderParam.item(itemStack, 0, 0)
            val countStr = if (count > 1) count.toString() else null
            renderParam.itemDecorations(Minecraft.getInstance().font, itemStack, 0, 0, countStr)
        }
    }

    context(renderParam: GuiGraphicsExtractor)
    override fun withScissor(rect: Rect, block: () -> Unit) {
        // Fix split-half and UI bleed: end rendering batch before scissor bounds are applied
        renderParam.bufferSource.endBatch()
        val r = (rect / guiScale).toInt()
        renderParam.enableScissor(r.left, r.top, r.right, r.bottom)
        try {
            block()
        } finally {
            // End batch while scissor is still active before disabling bounds
            renderParam.bufferSource.endBatch()
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
        if (image.isEmpty || image.id == "missing") return
        val r = rect.toFloat().ifEmpty { return }
        
        val minU = (uv.left / image.width).toFloat()
        val maxU = (uv.right / image.width).toFloat()
        val minV = (uv.top / image.height).toFloat()
        val maxV = (uv.bottom / image.height).toFloat()

        // Safely fetch buffer using vanilla RenderType
        val renderType = RenderType.guiTextured(Identifier.parse(image.id))
        val vc = renderParam.bufferSource.getBuffer(renderType)
        val matrix = renderParam.pose().last().pose()
        
        vc.vertex(matrix, r.left, r.top, 0f).color(color).uv(minU, minV).endVertex()
        vc.vertex(matrix, r.left, r.bottom, 0f).color(color).uv(minU, maxV).endVertex()
        vc.vertex(matrix, r.right, r.bottom, 0f).color(color).uv(maxU, maxV).endVertex()
        vc.vertex(matrix, r.right, r.top, 0f).color(color).uv(maxU, minV).endVertex()
    }

    context(ctx: DslScaleContext, renderParam: GuiGraphicsExtractor)
    override fun renderDefaultBackground(rect: Rect) {
        // Tiled dark dirt menu background
        val bgLocation = try {
            Screen.BACKGROUND_LOCATION.toString()
        } catch (e: Throwable) {
            "minecraft:textures/gui/options_background.png"
        }
        
        ImageStrategy.repeat(scale = ctx.scale).render(
            rect,
            ImageHolder(bgLocation, 32.px, 32.px),
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
