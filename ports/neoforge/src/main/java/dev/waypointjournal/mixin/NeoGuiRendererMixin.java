package dev.waypointjournal.mixin;

import dev.waypointjournal.BookRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.neoforged.neoforge.client.gui.PictureInPictureRendererRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(GuiRenderer.class)
abstract class NeoGuiRendererMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static List<PictureInPictureRendererRegistration<?>> waypointjournal$book(List<PictureInPictureRendererRegistration<?>> renderers) {
        var result = new ArrayList<>(renderers);
        result.add(new PictureInPictureRendererRegistration<>(BookRenderer.State.class, BookRenderer::new));
        return result;
    }
}
