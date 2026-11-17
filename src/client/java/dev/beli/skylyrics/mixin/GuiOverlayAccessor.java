package dev.beli.skylyrics.mixin;

//? if <26.2 {
import net.minecraft.client.gui.Gui;
//?} else {
/*import net.minecraft.client.gui.Hud;
*///?}
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * How many ticks the action bar message (the text above the hotbar) has left, and its text, so the bottom bar can step out of its
 * way. The fields have these names on every supported version (26.2 and later moved it to the Hud class).
 */
//? if <26.2 {
@Mixin(Gui.class)
//?} else {
/*@Mixin(Hud.class)
*///?}
public interface GuiOverlayAccessor {
	@Accessor("overlayMessageTime")
	int skylyrics$getOverlayMessageTime();

	@Accessor("overlayMessageString")
	Component skylyrics$getOverlayMessageString();

	/** The subtitles (same field name on every version), so the Record card can keep clear of the boxes showing. */
	@Accessor("subtitleOverlay")
	SubtitleOverlay skylyrics$getSubtitleOverlay();
}
