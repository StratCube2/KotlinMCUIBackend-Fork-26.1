package io.github.u2894638479.kotlinmcuibackend.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private double xpos;
    @Shadow
    private double ypos;

    @Inject(method = "onScroll", at = @At("HEAD"))
    void kotlinmcuibackend$mixinScroll(long window, double xoffset, double yoffset, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().handle()) {
            double f = (minecraft.options.discreteMouseScroll().get() ? Math.signum(xoffset) : xoffset) * minecraft.options.mouseWheelSensitivity().get();
            var func = io.github.u2894638479.kotlinmcuibackend.DefaultBackendKt.getHorizontalScroller();
            if (func != null && f != 0) func.invoke(xpos, ypos, f);
        }
    }

    // Mapped precisely to onButton (long, MouseButtonInfo, int) in 26.1/26.2
    @Inject(method = "onButton", at = @At("HEAD"))
    void kotlinmcuibackend$mixinPress(long window, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        io.github.u2894638479.kotlinmcuibackend.DefaultBackendKt.setEventModifier(rawButtonInfo.modifiers());
    }
}
