pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
	}
}

plugins {
	// One source tree for every Minecraft version: comments like //? if >=26.2 pick the lines for each one
	id("dev.kikugie.stonecutter") version "0.9.8"
	// Picks the right Loom for each version (remapped before 26.1, plain from 26.1 on)
	id("dev.kikugie.loom-back-compat") version "0.4.2"
}

stonecutter {
	create(rootProject) {
		// Each one becomes its own jar. The code in git is written for vcsVersion.
		versions("1.21.8", "1.21.10", "1.21.11", "26.1.2", "26.2", "26.3")
		vcsVersion = "1.21.11"
	}
}

rootProject.name = "belis-skylyrics"
