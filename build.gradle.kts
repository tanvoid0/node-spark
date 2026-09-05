import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    // Kotlin 2.4 is required by the 262 variant: that platform ships Kotlin 2.4 metadata, which an
    // older compiler refuses to read. It compiles the 241 variant just as happily.
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

// See gradle.properties: 262 moved the SM test-tree classes out of the platform core classloader.
val is262 = providers.gradleProperty("ideVariant").getOrElse("241") == "262"

/**
 * Release secrets, read from the environment first and then from a gitignored `.env` beside this
 * file, so a release can be cut from a plain terminal without exporting anything by hand. `.env` is
 * never committed - see `.env.example` for the keys and how to fill them.
 */
val dotenv: Map<String, String> = file(".env").takeIf { it.isFile }
    ?.readLines()
    .orEmpty()
    .mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
        val separator = trimmed.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
        val value = trimmed.substring(separator + 1).trim().trim('"')
        if (value.isEmpty()) null else trimmed.substring(0, separator).trim() to value
    }
    .toMap()

fun secret(name: String): Provider<String> =
    providers.environmentVariable(name).orElse(providers.provider { dotenv[name] })

group = providers.gradleProperty("pluginGroup").get()
// The two variants must not overwrite each other's zip, and the marketplace needs distinct,
// correctly ORDERED versions for the two build ranges. A "-262" suffix would be read as a semver
// pre-release and sort BELOW the plain version, so the branch number goes in front instead
// (JetBrains' own recommended scheme for multi-branch plugins): 241.0.2.0 and 262.0.2.0.
version = (if (is262) "262." else "241.") + providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)  // compile with Java 21 SDK

    // One file differs between the two branches - the coverage engine's view-extension hook, whose
    // signature 2026.2 changed. Everything else stays in src/main.
    sourceSets["main"].kotlin.srcDir(if (is262) "src/variant262/kotlin" else "src/variant241/kotlin")
}

// 2024.1 bundles JBR 17 and rejects newer bytecode; 262 runs on JBR 21.
val target = if (is262) "21" else "17"

tasks.withType<JavaCompile> {
    sourceCompatibility = target
    targetCompatibility = target
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(target))
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        if (is262) {
            local(providers.gradleProperty("localIde262"))
        } else {
            create(
                providers.gradleProperty("platformType"),
                providers.gradleProperty("platformVersion")
            )
        }
        // Java plugin: supplies com.intellij.projectImport (module import from external model)
        bundledPlugin("com.intellij.java")
        // JSON PSI (package.json scripts) is part of the platform up to 261 and a separate
        // bundled plugin from 262 on.
        if (is262) {
            bundledPlugin("com.intellij.modules.json")
            // The SM test tree: platform core up to 261, its own modules from 262. smRunner's own
            // supertypes (TestConsoleProperties, AbstractTestProxy) live in testRunner.
            bundledModule("intellij.platform.smRunner")
            bundledModule("intellij.platform.testRunner")
        }
        // LSP4IJ: the LSP client Community lacks. Optional at runtime (see plugin.xml), so this is
        // only a compile classpath. 0.17.0 is the last release whose sinceBuild (233) covers 2024.1;
        // newer ones require 242+, and the API used here has not changed since.
        plugin("com.redhat.devtools.lsp4ij", "0.17.0")
        testFramework(TestFrameworkType.Platform)
    }
    // The IDE ships gson in util-8.jar; compileOnly keeps a duplicate copy out of the plugin zip.
    compileOnly("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}

// One plugin.xml, two variants: the 262 build gets the module dependencies appended to the patched
// descriptor, rather than keeping a second copy of the whole thing in sync by hand. This has to
// hook patchPluginXml, not processResources — the jar takes the descriptor from patchPluginXml's
// own output, so a filter on the resource copy never reaches it.
if (is262) {
    tasks.patchPluginXml {
        doLast {
            val xml = outputFile.get().asFile
            xml.writeText(
                xml.readText().replace(
                    "</idea-plugin>",
                    "  <dependencies>\n" +
                        "    <module name=\"intellij.platform.testRunner\" />\n" +
                        "    <module name=\"intellij.platform.smRunner\" />\n" +
                        "  </dependencies>\n</idea-plugin>",
                ),
            )
        }
    }
}

// Upload the signed artifact, explicitly: the default picked the plain zip, and an unsigned upload
// is accepted by the Marketplace without complaint, so the mistake is silent.
tasks.publishPlugin {
    archiveFile = tasks.signPlugin.flatMap { it.signedArchiveFile }
}

// One check before a release: are the secrets there at all? Names and presence only - a value is
// never printed, so this is safe to run with output shared.
tasks.register("releaseCheck") {
    group = "intellij platform"
    description = "Reports which release secrets are set, without printing any of their values."
    val found = listOf(
        "PUBLISH_TOKEN", "PRIVATE_KEY_PASSWORD", "PUBLISH_CHANNEL",
        "PRIVATE_KEY", "CERTIFICATE_CHAIN",
    ).associateWith { secret(it).isPresent }
    val files = listOf("PRIVATE_KEY_FILE", "CERTIFICATE_CHAIN_FILE").associateWith { name ->
        secret(name).orNull?.let { it to file(it).isFile }
    }
    doLast {
        found.forEach { (name, present) -> logger.lifecycle("${if (present) "set    " else "missing"}  $name") }
        files.forEach { (name, value) ->
            logger.lifecycle(
                when {
                    value == null -> "missing  $name"
                    value.second -> "set      $name -> ${value.first}"
                    else -> "BROKEN   $name -> ${value.first} does not exist"
                },
            )
        }
    }
}

// Open the bundled demo project on runIde, so a sandbox launch lands straight in something testable.
tasks.runIde {
    args(project.file("demo").absolutePath)
}

tasks.withType<Test> {
    jvmArgs(
        "-Djava.awt.headless=true",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
    )
}

intellijPlatform {
    // Searchable-options indexing runs the target IDE headless; the pinned Gradle plugin cannot
    // drive a 262 IDE that way. It only pre-builds the Settings search index, so skip it there.
    buildSearchableOptions = !is262

    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = provider { project.version.toString() }

        ideaVersion {
            sinceBuild = provider { if (is262) "262" else "241" }
            // The 241 build must stop at 261, where the test-tree classes move; 262 is open-ended.
            untilBuild = provider { if (is262) null else "261.*" }
        }
    }

    pluginVerification {
        // The plugin's default is [COMPATIBILITY_PROBLEMS, INTERNAL_API_USAGES,
        // OVERRIDE_ONLY_API_USAGES]; only the middle one is dropped, and only because both hits are
        // unavoidable. CoverageEngine.getQualifiedName and coverageProjectViewStatisticsApplicableTo
        // are @Internal from 242 on with no public replacement — without them a Node coverage engine
        // paints no gutters and shows an empty tool window (see NodeCoverageEngine). The
        // ToolWindowFactory ones are not written here at all: Kotlin generates delegating overrides
        // of an interface's default methods, internal or not, for every implementing class.
        failureLevel = listOf(
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
        )
    }

    // A PEM does not fit on one line, so the key and the chain are given as paths to files kept
    // outside version control; the inline forms still work for CI, where a secret is a string.
    signing {
        certificateChain = secret("CERTIFICATE_CHAIN")
        privateKey = secret("PRIVATE_KEY")
        password = secret("PRIVATE_KEY_PASSWORD")
        secret("CERTIFICATE_CHAIN_FILE").orNull?.let { certificateChainFile = layout.projectDirectory.file(it) }
        secret("PRIVATE_KEY_FILE").orNull?.let { privateKeyFile = layout.projectDirectory.file(it) }
    }

    publishing {
        token = secret("PUBLISH_TOKEN")
        // A plugin can be published to a non-default channel ("eap", "beta") for a pre-release.
        secret("PUBLISH_CHANNEL").orNull?.let { channels = listOf(it) }
    }
}
