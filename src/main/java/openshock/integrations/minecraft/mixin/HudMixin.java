package openshock.integrations.minecraft.mixin;

//? if >=1.21 {
import net.minecraft.client.DeltaTracker;
//?}
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else {
/*import net.minecraft.client.gui.Gui;
*///?}
import openshock.integrations.minecraft.ShockOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws {@link ShockOverlay} on top of the finished HUD.
 *
 * A mixin rather than a loader event, because the HUD hook is the one piece of API that differs on
 * both axes at once: Fabric and NeoForge spell it differently, and each of them has changed its
 * spelling again since 1.20.4. The vanilla method underneath is the same thing on all sixteen
 * targets, so hooking that directly is one file where the loader route would be four.
 *
 * What that method is called has still moved twice. It is {@code Gui.render} up to 1.21.11; 26.1
 * turned the HUD into a render state it records rather than draws, renaming it to
 * {@code extractRenderState}; and 26.2 split the HUD proper out of {@code Gui} into {@code Hud},
 * taking that method with it. Only the name, the owner and the parameter list change - the tail of
 * it is the top of the HUD in every case.
 *
 * TAIL, so the lightning lands over the hotbar and the health bar rather than under them. Vanilla
 * only reaches any of these while the HUD is being drawn at all, so F1 hides the overlay too.
 */
//? if >=26.2 {
@Mixin(Hud.class)
//?} else {
/*@Mixin(Gui.class)
*///?}
public class HudMixin {

    //? if >=26.1 {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void shockcraft$shockOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ShockOverlay.INSTANCE.render(graphics);
    }
    //?} elif >=1.21 {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void shockcraft$shockOverlay(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ShockOverlay.INSTANCE.render(graphics);
    }
    *///?} else {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void shockcraft$shockOverlay(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        ShockOverlay.INSTANCE.render(graphics);
    }
    *///?}
}
