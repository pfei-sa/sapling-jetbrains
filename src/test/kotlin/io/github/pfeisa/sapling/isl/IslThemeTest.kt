package io.github.pfeisa.sapling.isl

import org.junit.Assert.assertEquals
import org.junit.Test

class IslThemeTest {
    @Test fun mapsDark() = assertEquals("dark", themeName(isDark = true))
    @Test fun mapsLight() = assertEquals("light", themeName(isDark = false))
}
