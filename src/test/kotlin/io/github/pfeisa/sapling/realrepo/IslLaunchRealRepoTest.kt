package io.github.pfeisa.sapling.realrepo

import io.github.pfeisa.sapling.isl.IslLaunchResult
import io.github.pfeisa.sapling.isl.parseIslLaunch
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket

class IslLaunchRealRepoTest {

    @Test
    fun launchesParsesAndServesThenShutsDown() {
        IntegrationTools.assumeSl()
        SlTestRepo.create().use { repo ->
            val launch = repo.sl(
                "web", "--platform", "androidStudio", "--json", "--no-open", "--cwd", repo.root.toString(),
            )
            // NOTE: launch.stdout contains the auth token — never log it or put it in a message.
            assertTrue("sl web did not exit cleanly", launch.success)
            val parsed = parseIslLaunch(launch.stdout.trim())
            if (parsed !is IslLaunchResult.Ready) {
                fail("expected IslLaunchResult.Ready (details withheld: output carries a token)")
                return
            }
            val info = (parsed as IslLaunchResult.Ready).info
            try {
                assertTrue("url present", info.url.isNotBlank())
                assertTrue("token present", info.token.isNotBlank())
                assertTrue("port in range", info.port in 1..65535)
                assertTrue("pid present", (info.pid ?: 0L) > 0L)
                // Liveness: the server accepts a TCP connection on its port.
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", info.port), 3000)
                    assertTrue("socket connected", s.isConnected)
                }
            } finally {
                // Always shut the server down (mirrors IslServerManager.stop()).
                repo.sl("web", "--kill", "-p", info.port.toString(), "--cwd", repo.root.toString())
            }
        }
    }
}
