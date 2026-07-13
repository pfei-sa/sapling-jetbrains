package io.github.pfeisa.sapling.cli

import com.intellij.execution.configurations.GeneralCommandLine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class SaplingCliTest {
    @Test
    fun buildsConsoleUtf8CommandLine() {
        val cli = SaplingCli("sl")
        val cmd = cli.buildCommandLine("/repo", listOf("status", "-Tjson"))

        assertEquals("sl", cmd.exePath)
        assertEquals(listOf("status", "-Tjson"), cmd.parametersList.parameters)
        assertEquals(File("/repo"), cmd.workDirectory)
        assertEquals(GeneralCommandLine.ParentEnvironmentType.CONSOLE, cmd.parentEnvironmentType)
        assertEquals(StandardCharsets.UTF_8, cmd.charset)
    }
}
