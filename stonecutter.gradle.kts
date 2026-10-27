plugins {
	id("dev.kikugie.stonecutter")
}

// The version the code in src/ is currently written for
stonecutter active "1.21.11"

stonecutter parameters {
	// Names Minecraft changed: written the 1.21.11 way in src/, swapped when building newer versions
	replacements {
		string(current.parsed >= "26.2") {
			replace("GuiGraphics", "GuiGraphicsExtractor")
			replace(".drawString(", ".text(")
			replace(".drawCenteredString(", ".centeredText(")
		}
	}
}
