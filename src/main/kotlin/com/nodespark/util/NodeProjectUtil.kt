package com.nodespark.util

import com.intellij.openapi.project.Project
import java.io.File

object NodeProjectUtil {

    /**
     * Nearest package.json at or above [startPath] (a file or a directory),
     * stopping once [stopAt] has been passed. Null if there is none.
     */
    @JvmOverloads
    fun findNearestPackageJson(startPath: String, stopAt: String? = null): File? {
        val stop = stopAt?.let { File(it).absoluteFile }
        var dir: File? = File(startPath).absoluteFile.let { if (it.isFile) it.parentFile else it }
        while (dir != null) {
            val pkg = File(dir, "package.json")
            if (pkg.isFile) return pkg
            if (stop != null && dir == stop) return null
            dir = dir.parentFile
        }
        return null
    }

    /** Directory of the package.json owning [contextFile], else the project base, else the user home. */
    @JvmOverloads
    fun projectRootFor(project: Project, contextFile: String? = null): String {
        val base = project.basePath ?: System.getProperty("user.home")
        if (contextFile.isNullOrBlank()) return base
        return findNearestPackageJson(contextFile, base)?.parent ?: base
    }
}
