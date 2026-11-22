package dev.beli.skylyrics.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The chat's lines as it draws them (each message wrapped to the chat's width) and how far it is scrolled up, so the
 * Record card knows how tall the open chat's box really is: the game draws it behind those lines only. The lines' class
 * moved package in 26.1, so they come as {@code List<?>}. Same field names on every supported version.
 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("trimmedMessages")
	List<?> skylyrics$getTrimmedMessages();

	@Accessor("chatScrollbarPos")
	int skylyrics$getChatScrollbarPos();
}
