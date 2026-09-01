package openshock.integrations.minecraft.mixin;

import net.minecraft.client.MouseHandler;
import openshock.integrations.minecraft.RemoteTuning;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the sneak-and-scroll gesture take the wheel before the hotbar does.
 *
 * The only mixin in the mod, and it exists because nothing else is early enough. Vanilla moves the
 * selected hotbar slot while polling input, which happens once per frame; a mod watching from a
 * client tick only sees it twenty times a second, so putting the slot back from there leaves the
 * moved slot on screen for several frames and the hotbar flickers. Cancelling here means the
 * scroll never reaches the hotbar at all.
 *
 * {@code onScroll} is private, and identical - {@code (long, double, double)} - on every version
 * the collar exists on, from 1.21.5 to 26.2.
 *
 * {@link RemoteTuning#onScroll(double)} answers false for every scroll that is not deliberately
 * part of the gesture, which is nearly all of them, so the wheel behaves normally the rest of the
 * time. Below 1.21.5 there is no remote and it always answers false.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void shockcraft$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (RemoteTuning.INSTANCE.onScroll(vertical)) {
            ci.cancel();
        }
    }
}
