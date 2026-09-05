package com.nodespark.npm

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.nodespark.sdk.NodeProjectSdkService
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches [LockfileSync] reports per package directory.
 *
 * Parsing a lockfile is cheap but not free — a monorepo's package-lock.json runs to megabytes — and
 * the editor banner asks the same question for every file that is opened. The answer only changes
 * when package.json, the lockfile or node_modules changes, so it is keyed on a stamp of those three.
 */
@Service(Service.Level.PROJECT)
class LockfileSyncService(private val project: Project) {

    private data class Cached(val stamp: String, val report: LockfileSync.Report)

    private val cache = ConcurrentHashMap<String, Cached>()

    /** The report for [dir], parsing the lockfile only when something it depends on has changed. */
    fun report(dir: String): LockfileSync.Report {
        val stamp = stampOf(File(dir))
        cache[dir]?.takeIf { it.stamp == stamp }?.let { return it.report }
        val pm = NodeProjectSdkService.getInstance(project).packageManagerFor(dir)
        val report = LockfileSync.reportFor(File(dir), pm)
        cache[dir] = Cached(stamp, report)
        return report
    }

    /** The cached report, if the cached one is still current. Never touches the lockfile. */
    fun cached(dir: String): LockfileSync.Report? {
        val entry = cache[dir] ?: return null
        return entry.report.takeIf { entry.stamp == stampOf(File(dir)) }
    }

    fun invalidate(dir: String) {
        cache.remove(dir)
    }

    /** Size and timestamp of everything the report is derived from. Stat calls only. */
    private fun stampOf(dir: File): String {
        val parts = ArrayList<String>()
        for (name in LOCK_INPUTS) {
            val file = File(dir, name)
            parts.add(if (file.exists()) "${file.lastModified()}:${file.length()}" else "-")
        }
        return parts.joinToString("|")
    }

    companion object {
        /**
         * node_modules' own timestamp moves when a package manager adds or removes a top-level
         * entry, which is the case worth invalidating on. A version changing inside an existing
         * directory does not move it — that only happens through an install, which moves the
         * lockfile too.
         */
        private val LOCK_INPUTS = listOf("package.json", "node_modules") +
            com.nodespark.util.NodePackageManager.values().flatMap { Lockfile.namesFor(it) }.distinct()

        fun getInstance(project: Project): LockfileSyncService = project.getService(LockfileSyncService::class.java)
    }
}
