plugins {
	// Applies the right Loom for this Minecraft version (see settings.gradle.kts)
	id("dev.kikugie.loom-back-compat")
}

// Built once per Minecraft version (stonecutter.properties.toml). The version goes in the jar name so the jars are
// told apart.
val mc = sc.current.version
version = "${property("mod.version")}+$mc"
base.archivesName = property("mod.archives") as String

val requiredJava: JavaVersion = if (sc.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21

repositories {
	maven("https://maven.terraformersmc.com/releases/") { name = "Terraformers" }
	maven("https://api.modrinth.com/maven") {
		name = "Modrinth"
		content { includeGroup("maven.modrinth") }
	}
	mavenCentral()
}

loom {
	splitEnvironmentSourceSets()

	mods {
		create("skylyrics") {
			sourceSet(sourceSets["main"])
			sourceSet(sourceSets["client"])
		}
	}
}

// Use mod{type} even on 26.1+: loom-back-compat turns them into the plain ones there
dependencies {
	minecraft("com.mojang:minecraft:$mc")
	loomx.applyMojangMappings()
	modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

	// Optional: adds a config button in Mod Menu when it is installed
	modCompileOnly("com.terraformersmc:modmenu:${property("deps.modmenu")}")

	// Only in the test game (runClient), to check the lyrics with Sodium + Iris shaders. Not shipped.
	modLocalRuntime("maven.modrinth:sodium:${property("deps.sodium")}")
	modLocalRuntime("maven.modrinth:iris:${property("deps.iris")}")
	// Iris bundles these, but the test game needs them on the classpath directly
	localRuntime("org.anarres:jcpp:1.4.14")
	localRuntime("io.github.douira:glsl-transformer:3.0.0-pre3")
	localRuntime("org.antlr:antlr4-runtime:4.13.1")

	// Talks to music players over D-Bus (MPRIS) on Linux. Bundled inside the mod jar.
	val dbus = property("deps.dbus_java")
	"clientImplementation"("com.github.hypfvieh:dbus-java-core:$dbus")
	"clientImplementation"("com.github.hypfvieh:dbus-java-transport-native-unixsocket:$dbus")
	include("com.github.hypfvieh:dbus-java-core:$dbus")
	include("com.github.hypfvieh:dbus-java-transport-native-unixsocket:$dbus")

	testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// A test game that runs like a real install: the built jar with the published Fabric API, Sodium, Iris and Mod Menu,
// the way a player installs it. Catches problems that only show up outside the dev game.
val productionMods: Configuration by configurations.creating { isTransitive = false }

dependencies {
	productionMods("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
	productionMods("maven.modrinth:sodium:${property("deps.sodium")}")
	productionMods("maven.modrinth:iris:${property("deps.iris")}")
	productionMods("com.terraformersmc:modmenu:${property("deps.modmenu")}")
}

tasks.register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("runProductionClient") {
	mods.from(loomx.modJar)
	// -PmodsDir=/path/to/mods tries the jar next to a player's own mod list instead
	if (project.hasProperty("modsDir")) {
		mods.from(fileTree(project.property("modsDir").toString()) { include("*.jar") })
	} else {
		mods.from(productionMods)
	}
	jvmArgs.add("-Dskylyrics.testHook=true")
	// -PwindowSize=1600x900 sets the game window's size
	if (project.hasProperty("windowSize")) {
		val (w, h) = project.property("windowSize").toString().split("x")
		programArgs.addAll("--width", w, "--height", h)
	}
	// -PquickPlay=WorldName opens that world straight away
	if (project.hasProperty("quickPlay")) {
		programArgs.addAll("--quickPlaySingleplayer", project.property("quickPlay").toString())
	}
	// Each version gets its own game folder, since worlds don't load in older versions
	runDir = rootProject.file(if (mc == "1.21.11") "run-prod" else "run-prod-$mc")
}

sourceSets {
	test {
		compileClasspath += sourceSets["client"].output + sourceSets["client"].compileClasspath
		runtimeClasspath += sourceSets["client"].output + sourceSets["client"].runtimeClasspath
	}
}

tasks.test {
	useJUnitPlatform()
	// Some tests read the lang files and sources from src/
	workingDir = rootProject.projectDir
	// Name every test in the log, so CI shows that the Windows ones really ran there
	testLogging {
		events("passed", "skipped", "failed")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
	}
}

val resourceProps = mapOf(
	"version" to project.version.toString(),
	"minecraft" to property("mod.mc_compat").toString(),
	"java" to requiredJava.majorVersion,
)

tasks.withType<ProcessResources>().configureEach {
	val props = resourceProps
	inputs.properties(props)
	filesMatching("fabric.mod.json") { expand(props) }
	filesMatching("*.mixins.json") { expand(props) }
}

tasks.withType<JavaCompile>().configureEach {
	options.release = requiredJava.majorVersion.toInt()
}

java {
	withSourcesJar()
	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava
}

tasks.withType<Jar>().configureEach {
	val projectName = rootProject.name
	inputs.property("projectName", projectName)
	from(rootProject.file("LICENSE")) { rename { "${it}_$projectName" } }
}

// Puts every version's jar in the top build/libs, for the release
val collectJars by tasks.registering(Copy::class) {
	from(loomx.modJar.flatMap { it.archiveFile })
	into(rootProject.layout.buildDirectory.dir("libs"))
}
tasks.named("build") { finalizedBy(collectJars) }
