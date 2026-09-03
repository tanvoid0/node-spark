package com.nodespark.debug

import org.junit.Assert.*
import org.junit.Test

class NodeCdpPathsTest {

    // ── toLocalPath ─────────────────────────────────────────────────────────

    @Test fun `canonical file triple slash url with windows drive`() {
        assertEquals("C:/x/y.js", NodeCdpPaths.toLocalPath("file:///C:/x/y.js"))
    }

    @Test fun `vfs style double slash url`() {
        assertEquals("C:/x/y.js", NodeCdpPaths.toLocalPath("file://C:/x/y.js"))
    }

    @Test fun `single slash url from File toURI`() {
        assertEquals("C:/x/y.js", NodeCdpPaths.toLocalPath("file:/C:/x/y.js"))
    }

    @Test fun `unix path has no drive letter stripped`() {
        assertEquals("/home/x/y.js", NodeCdpPaths.toLocalPath("file:///home/x/y.js"))
    }

    @Test fun `node internal url is not a file path`() {
        assertNull(NodeCdpPaths.toLocalPath("node:internal/main/run_main_module"))
    }

    // ── toCdpUrl ────────────────────────────────────────────────────────────

    @Test fun `windows path round trips through toCdpUrl`() {
        val url = NodeCdpPaths.toCdpUrl("C:\\x\\y.js")
        assertEquals("C:/x/y.js", NodeCdpPaths.toLocalPath(url))
    }
}
