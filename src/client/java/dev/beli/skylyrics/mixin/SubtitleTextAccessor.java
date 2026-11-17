package dev.beli.skylyrics.mixin;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** One subtitle entry's text (the entry class is private to the game, so it is targeted by name). */
@Mixin(targets = "net.minecraft.client.gui.components.SubtitleOverlay$Subtitle")
public interface SubtitleTextAccessor {
	@Accessor("text")
	Component skylyrics$getText();
}
