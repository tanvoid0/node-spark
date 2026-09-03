package com.nodespark.noderun

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeAppArgvTest {

    @Test fun `run order is node options, script, program args`() = assertEquals(
        listOf("--experimental-vm-modules", "/p/server.js", "--port", "3000"),
        NodeAppRunState.argv("--experimental-vm-modules", "/p/server.js", "--port 3000"),
    )

    @Test fun `debug flag precedes node options and script`() = assertEquals(
        listOf("--inspect-brk=9229", "--experimental-vm-modules", "/p/server.js"),
        NodeAppRunState.argv("--experimental-vm-modules", "/p/server.js", "", 9229),
    )

    @Test fun `blank options and args contribute nothing`() = assertEquals(
        listOf("/p/server.js"),
        NodeAppRunState.argv("", "/p/server.js", ""),
    )

    @Test fun `quoted arg stays one token`() = assertEquals(
        listOf("/p/s.js", "hello world"),
        NodeAppRunState.argv("", "/p/s.js", "\"hello world\""),
    )

    @Test fun `non-positive debug port injects nothing`() = assertEquals(
        listOf("/p/s.js"),
        NodeAppRunState.argv("", "/p/s.js", "", -1),
    )
}
