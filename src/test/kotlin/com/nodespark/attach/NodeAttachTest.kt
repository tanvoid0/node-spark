package com.nodespark.attach

import com.nodespark.debug.NodeCdpPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeAttachTest {

    // ── host / port validation ──────────────────────────────────────────────

    @Test fun `defaults are valid`() {
        assertNull(NodeAttachRunConfiguration.validate(DEFAULT_ATTACH_HOST, DEFAULT_ATTACH_PORT))
    }

    @Test fun `blank host rejected`() {
        assertEquals("Host is required", NodeAttachRunConfiguration.validate("  ", "9229"))
    }

    @Test fun `non numeric port rejected`() {
        assertEquals("Port must be a number", NodeAttachRunConfiguration.validate("localhost", "abc"))
    }

    @Test fun `empty port rejected`() {
        assertEquals("Port must be a number", NodeAttachRunConfiguration.validate("localhost", ""))
    }

    @Test fun `port zero rejected`() {
        assertEquals("Port must be between 1 and 65535", NodeAttachRunConfiguration.validate("localhost", "0"))
    }

    @Test fun `port above range rejected`() {
        assertEquals("Port must be between 1 and 65535", NodeAttachRunConfiguration.validate("localhost", "65536"))
    }

    @Test fun `port at range edges accepted`() {
        assertNull(NodeAttachRunConfiguration.validate("localhost", "1"))
        assertNull(NodeAttachRunConfiguration.validate("localhost", "65535"))
    }

    @Test fun `surrounding whitespace in port tolerated`() {
        assertNull(NodeAttachRunConfiguration.validate("localhost", " 9229 "))
    }

    // ── CDP url <-> local path ──────────────────────────────────────────────

    @Test fun `windows path to cdp url`() {
        assertEquals("file:///C:/x/y.js", NodeCdpPaths.toCdpUrl("C:\\x\\y.js"))
        assertEquals("file:///C:/x/y.js", NodeCdpPaths.toCdpUrl("C:/x/y.js"))
    }

    @Test fun `spaces are percent encoded`() {
        assertEquals("file:///C:/Program%20Files/a.js", NodeCdpPaths.toCdpUrl("C:\\Program Files\\a.js"))
    }

    @Test fun `cdp url to windows path drops the leading slash`() {
        assertEquals("C:/x/y.js", NodeCdpPaths.toLocalPath("file:///C:/x/y.js"))
    }

    @Test fun `intellij vfs url form is understood`() {
        // XLineBreakpoint.getFileUrl() gives two slashes, not three
        assertEquals("C:/x/y.js", NodeCdpPaths.toLocalPath("file://C:/x/y.js"))
    }

    @Test fun `percent escapes are decoded`() {
        assertEquals("C:/Program Files/a.js", NodeCdpPaths.toLocalPath("file:///C:/Program%20Files/a.js"))
    }

    @Test fun `posix path keeps its leading slash`() {
        assertEquals("/home/x/y.js", NodeCdpPaths.toLocalPath("file:///home/x/y.js"))
    }

    @Test fun `plus is not turned into a space`() {
        assertEquals("C:/a+b/y.js", NodeCdpPaths.toLocalPath("file:///C:/a+b/y.js"))
    }

    @Test fun `node internal urls are not local files`() {
        assertNull(NodeCdpPaths.toLocalPath("node:internal/modules/cjs/loader"))
        assertNull(NodeCdpPaths.toLocalPath("http://example.com/a.js"))
    }

    @Test fun `round trip survives spaces`() {
        val url = NodeCdpPaths.toCdpUrl("C:\\Program Files\\a.js")
        assertEquals("C:/Program Files/a.js", NodeCdpPaths.toLocalPath(url))
    }
}
