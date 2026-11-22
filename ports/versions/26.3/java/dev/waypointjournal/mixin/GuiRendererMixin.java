package dev.waypointjournal.mixin;

import dev.waypointjournal.BookRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static List<PictureInPictureRenderer<?>> waypointjournal$book(List<PictureInPictureRenderer<?>> renderers) {
        var result = new ArrayList<>(renderers);
        result.add(new BookRenderer());
        return result;
    }
}
