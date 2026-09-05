package com.nodespark.coverage

import com.intellij.coverage.CoverageEngine
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.junit.Test
import java.io.File

/**
 * [NodeCoverageEngine] overrides two `@ApiStatus.Internal` methods of [CoverageEngine]: there is no
 * public equivalent, the platform reads both off the engine, and without them the Coverage tool
 * window shows no percentages and the gutter shows no stripes.
 *
 * Internal API carries no deprecation cycle, and an override that stops overriding anything fails
 * silently — no exception, just the features going dark. So assert the base class still declares
 * them. This test failing on an SDK bump means the platform moved: find what replaced the method,
 * do not delete the assertion.
 */
class NodeCoverageEngineContractTest {

    private fun assertDeclared(name: String, vararg params: Class<*>) {
        // getDeclaredMethod, not getMethod: getQualifiedName is protected, and we care that the
        // supertype itself still declares it rather than that it is reachable somehow.
        CoverageEngine::class.java.getDeclaredMethod(name, *params)
    }

    @Test fun `the platform still asks the engine which files get percentages`() =
        assertDeclared("coverageProjectViewStatisticsApplicableTo", VirtualFile::class.java)

    @Test fun `the platform still asks the engine to name a source file's output`() =
        assertDeclared("getQualifiedName", File::class.java, PsiFile::class.java)
}
