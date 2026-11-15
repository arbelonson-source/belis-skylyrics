package dev.beli.skylyrics.mixin;

//? if <26.2 {
import net.minecraft.client.gui.Gui;
//?} else {
/*import net.minecraft.client.gui.Hud;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * How many ticks the action bar message (the text above the hotbar) has left, so the bottom bar can step out of its
 * way. The field has this name on every supported version (26.2 and later moved it to the Hud class).
 */
//? if <26.2 {
@Mixin(Gui.class)
//?} else {
/*@Mixin(Hud.class)
*///?}
public interface GuiOverlayAccessor {
	@Accessor("overlayMessageTime")
	int skylyrics$getOverlayMessageTime();
}
