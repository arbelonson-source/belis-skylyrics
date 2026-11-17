package dev.beli.skylyrics.mixin;

import java.util.List;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The subtitles the game drew last frame (it rebuilds this list each frame from the audible ones and drops the expired).
 * The entries' class is private to the game, so they come as {@code List<?>} and are read through {@link SubtitleTextAccessor}.
 * Same field name on every supported version.
 */
@Mixin(SubtitleOverlay.class)
public interface SubtitleOverlayAccessor {
	@Accessor("audibleSubtitles")
	List<?> skylyrics$getAudibleSubtitles();
}
