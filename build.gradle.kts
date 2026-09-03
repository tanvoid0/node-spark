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

group = providers.gradleProperty("pluginGroup").get()
// The two variants must not overwrite each other's zip, and the marketplace needs distinct,
// correctly ORDERED versions for the two build ranges. A "-262" suffix would be read as a semver
// pre-release and sort BELOW the plain version, so the branch number goes in front instead
// (JetBrains' own recommended scheme for multi-branch plugins): 241.0.2.0 and 262.0.2.0.
version = (if (is262) "262." else "241.") + providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)  // compile with Java 21 SDK
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

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
