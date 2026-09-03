package com.nodespark.util

import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * Which package manager a project directory uses, and how to invoke it.
 * The `packageManager` field of package.json wins over lockfiles (corepack semantics).
 */
enum class NodePackageManager(val binName: String) {
    NPM("npm"),
    YARN("yarn"),
    PNPM("pnpm"),
    BUN("bun");

    /** Executable name for the platform — npm/yarn/pnpm ship .cmd shims on Windows, bun is a real .exe. */
    @JvmOverloads
    fun binary(windows: Boolean = SystemInfo.isWindows): String =
        if (windows && this != BUN) "$binName.cmd" else binName

    /** argv (after the binary) that runs a package.json script. */
    fun runArgs(script: String): List<String> =
        if (this == YARN) listOf(script) else listOf("run", script)

    /** argv (after the binary) that installs dependencies. */
    fun installArgs(): List<String> = listOf("install")

    /**
     * argv (after the binary) that adds [packages] as dev dependencies.
     * npm is the odd one out: `npm add` exists but the dev flag is spelled `--save-dev`, while the
     * other three take `add` with a short flag.
     */
    fun addDevArgs(packages: List<String>): List<String> = when (this) {
        NPM -> listOf("install", "--save-dev") + packages
        YARN -> listOf("add", "--dev") + packages
        PNPM -> listOf("add", "--save-dev") + packages
        BUN -> listOf("add", "--dev") + packages
    }

    companion object {
        private val PACKAGE_MANAGER_FIELD =
            Regex(""""packageManager"\s*:\s*"([a-zA-Z]+)""")

        /** Detects the package manager for [projectDir]. Never fails; defaults to NPM. */
        fun detect(projectDir: String): NodePackageManager {
            val base = File(projectDir)
            fromPackageJson(File(base, "package.json"))?.let { return it }
            return when {
                File(base, "pnpm-lock.yaml").exists() -> PNPM
                File(base, "yarn.lock").exists() -> YARN
                File(base, "bun.lockb").exists() || File(base, "bun.lock").exists() -> BUN
                File(base, "package-lock.json").exists() -> NPM
                else -> NPM
            }
        }

        /** Reads the `packageManager` field, e.g. "pnpm@9.1.0" -> PNPM. Null if absent/unknown. */
        fun fromPackageJson(packageJson: File): NodePackageManager? =
            if (packageJson.isFile) fromSpec(packageJson.readText()) else null

        /** Extracts the manager from raw package.json text, e.g. `"packageManager": "pnpm@9.1.0"` -> PNPM. */
        // ponytail: regex instead of JSON parse — a nested key named packageManager would fool it; use gson if that ever bites.
        fun fromSpec(text: String): NodePackageManager? {
            val name = PACKAGE_MANAGER_FIELD.find(text)?.groupValues?.get(1) ?: return null
            return values().firstOrNull { it.binName.equals(name, ignoreCase = true) }
        }
    }
}
