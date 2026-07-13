package io.github.pfeisa.sapling.isl

/** Maps IDE darkness to the string the androidStudio ISL platform expects. */
fun themeName(isDark: Boolean): String = if (isDark) "dark" else "light"
