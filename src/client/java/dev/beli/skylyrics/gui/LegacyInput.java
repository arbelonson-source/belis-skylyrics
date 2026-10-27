package dev.beli.skylyrics.gui;

//? if <1.21.9 {
/*import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.StringUtil;

// Stand-ins for the input event records that only exist from Minecraft 1.21.9 on.
public final class LegacyInput {
	private LegacyInput() {
	}

	public record KeyEvent(int key, int scancode, int modifiers) {
		public boolean isEscape() {
			return key == InputConstants.KEY_ESCAPE;
		}

		public boolean isConfirmation() {
			return key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
		}

		public boolean isSelection() {
			return isConfirmation() || key == InputConstants.KEY_SPACE;
		}

		public boolean isCycleFocus() {
			return key == InputConstants.KEY_TAB;
		}

		public boolean isLeft() {
			return key == InputConstants.KEY_LEFT;
		}

		public boolean isRight() {
			return key == InputConstants.KEY_RIGHT;
		}

		public boolean isUp() {
			return key == InputConstants.KEY_UP;
		}

		public boolean isDown() {
			return key == InputConstants.KEY_DOWN;
		}

		public boolean isPaste() {
			return Screen.isPaste(key);
		}

		public boolean hasControlDown() {
			return Screen.hasControlDown();
		}

		public boolean hasShiftDown() {
			return Screen.hasShiftDown();
		}
	}

	public record MouseButtonEvent(double x, double y, int button) {
	}

	public record CharacterEvent(int codepoint) {
		public boolean isAllowedChatCharacter() {
			return StringUtil.isAllowedChatCharacter((char) codepoint) && codepoint <= 0xFFFF;
		}

		public String codepointAsString() {
			return Character.toString(codepoint);
		}
	}
}
*///?}
