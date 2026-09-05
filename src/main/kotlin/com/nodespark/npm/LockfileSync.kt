package com.nodespark.npm

import com.nodespark.util.NodePackageManager
import java.io.File

/**
 * Three-way comparison of package.json, the lockfile and node_modules — the check `npm ci` runs
 * before it refuses to start, made visible before it costs anyone an afternoon.
 *
 * Pure: [check] takes what it needs and returns findings. Reading the files, and caching the
 * result, is [LockfileSyncService]'s job.
 */
object LockfileSync {

    /**
     * [INSTALL] drifts are fixed by a plain install, which rewrites the lockfile.
     * [FROZEN] drifts leave the lockfile alone and only bring node_modules back to it.
     */
    enum class Fix { INSTALL, FROZEN }

    enum class Kind(val fix: Fix, val label: String) {
        MISSING_FROM_LOCK(Fix.INSTALL, "declared in package.json but missing from the lockfile"),
        RANGE_CHANGED(Fix.INSTALL, "the lockfile was resolved for a different range"),
        EXTRA_IN_LOCK(Fix.INSTALL, "in the lockfile but no longer declared in package.json"),
        NOT_INSTALLED(Fix.FROZEN, "locked but not present in node_modules"),
        INSTALLED_MISMATCH(Fix.FROZEN, "the installed version is not the locked one"),
    }

    data class Drift(val name: String, val kind: Kind, val detail: String) {
        override fun toString(): String = "$name: ${kind.label}${detail.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""}"
    }

    /**
     * The state of one package directory.
     *
     * [lockfile] is null when none has been written. [readable] is false for a lockfile whose
     * format cannot be inspected (bun.lockb), where the honest answer is "unknown", not "in sync".
     */
    data class Report(
        val lockfile: String? = null,
        val readable: Boolean = true,
        val strays: List<String> = emptyList(),
        val drifts: List<Drift> = emptyList(),
        val berry: Boolean = false,
        /** node_modules is missing entirely; a separate banner already offers to install it. */
        val notInstalled: Boolean = false,
    ) {
        val needsInstall: Boolean get() = lockfile == null || drifts.any { it.kind.fix == Fix.INSTALL }
        val needsFrozen: Boolean get() = !needsInstall && drifts.any { it.kind.fix == Fix.FROZEN }
        val inSync: Boolean get() = readable && !notInstalled && lockfile != null && drifts.isEmpty()

        /** One line for a banner or a status chip. */
        fun summary(): String = when {
            lockfile == null -> "No lockfile"
            !readable -> "Lockfile format cannot be checked"
            notInstalled -> "Dependencies are not installed"
            drifts.isEmpty() -> "In sync with $lockfile"
            needsInstall -> "${drifts.size} ${plural(drifts.size)} out of step with $lockfile"
            else -> "${drifts.size} installed ${plural(drifts.size)} out of step with $lockfile"
        }

        private fun plural(count: Int) = if (count == 1) "package" else "packages"
    }

    /**
     * [installed] gives the version of a package in node_modules, or null when it is not there;
     * pass null for [installed] when node_modules is absent, so the whole tree is not reported as
     * missing one package at a time.
     */
    fun check(
        declared: List<NpmScripts.Dependency>,
        lock: Lockfile.Lock,
        installed: ((String) -> String?)?,
    ): List<Drift> {
        val drifts = ArrayList<Drift>()
        for (dep in declared) {
            val entries = lock.entries[dep.name]
            if (entries.isNullOrEmpty()) {
                drifts.add(Drift(dep.name, Kind.MISSING_FROM_LOCK, dep.version))
                continue
            }
            val match = entries.firstOrNull { it.range == dep.version }
            if (match == null && lock.declaresRanges) {
                val known = entries.map { it.range }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
                drifts.add(Drift(dep.name, Kind.RANGE_CHANGED, "package.json asks for ${dep.version}, lockfile has $known"))
            }
            if (installed == null) continue
            val locked = (match ?: entries.first()).version
            if (locked.isEmpty()) continue // a link:/workspace: entry resolves to no published version
            val onDisk = installed(dep.name)
            when {
                onDisk == null -> drifts.add(Drift(dep.name, Kind.NOT_INSTALLED, locked))
                onDisk != locked -> drifts.add(Drift(dep.name, Kind.INSTALLED_MISMATCH, "$onDisk installed, $locked locked"))
            }
        }
        if (lock.rootScoped) {
            val names = declared.mapTo(HashSet()) { it.name }
            for (name in lock.entries.keys) {
                if (name !in names) drifts.add(Drift(name, Kind.EXTRA_IN_LOCK, ""))
            }
        }
        return drifts
    }

    /** Everything above, over one package directory. Hits the disk; keep it off the EDT. */
    fun reportFor(dir: File, pm: NodePackageManager): Report {
        val packageJson = File(dir, "package.json")
        if (!packageJson.isFile) return Report()
        val strays = Lockfile.strays(dir, pm)
        val lockfile = Lockfile.find(dir, pm)
            ?: return Report(strays = strays, notInstalled = !File(dir, "node_modules").isDirectory)
        if (Lockfile.isBinary(lockfile.name)) {
            return Report(lockfile = lockfile.name, readable = false, strays = strays)
        }

        val declared = NpmScripts.dependenciesOf(packageJson.readSafely())
        val lock = Lockfile.parse(lockfile)
        val modules = File(dir, "node_modules")
        val installed: ((String) -> String?)? =
            if (modules.isDirectory) { name -> NpmScripts.installedVersion(modules, name) } else null

        return Report(
            lockfile = lockfile.name,
            strays = strays,
            drifts = check(declared, lock, installed),
            berry = lock.berry,
            notInstalled = installed == null,
        )
    }

    private fun File.readSafely(): String = runCatching { readText() }.getOrDefault("")
}
