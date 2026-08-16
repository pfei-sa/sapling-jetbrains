package io.github.pfeisa.sapling.isl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Regression test for issue #3: the Rider / remote-dev backend ships without the
 * com.intellij.ui.jcef classes at all, so the availability probe itself must survive
 * `JBCefApp` failing to load. JcefSupport is re-defined in a classloader that hides the
 * JCEF package to reproduce that environment hermetically.
 */
class JcefSupportTest {

    @Test
    fun reportsUnavailableWhenJcefClassesAreAbsent() {
        val loader = JcefHidingClassLoader()
        val clazz = Class.forName("io.github.pfeisa.sapling.isl.JcefSupport", true, loader)
        // Child-first definition must have taken effect, or the probe under test is not isolated.
        assertSame(loader, clazz.classLoader)
        val instance = clazz.getDeclaredField("INSTANCE").get(null)
        assertEquals(false, clazz.getMethod("isAvailable").invoke(instance))
    }

    /**
     * Delegates everything to the test classpath except: com.intellij.ui.jcef.* is hidden
     * (fails to load, as in a JCEF-less IDE process) and JcefSupport itself (plus any
     * synthetic members) is re-defined child-first so its JBCefApp reference resolves
     * through this loader and actually hits the hidden package.
     */
    private class JcefHidingClassLoader :
        ClassLoader(JcefSupportTest::class.java.classLoader) {

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name.startsWith("com.intellij.ui.jcef.")) throw ClassNotFoundException(name)
            if (!name.startsWith("io.github.pfeisa.sapling.isl.JcefSupport")) {
                return super.loadClass(name, resolve)
            }
            synchronized(getClassLoadingLock(name)) {
                findLoadedClass(name)?.let { return it }
                val resource = name.replace('.', '/') + ".class"
                val bytes = parent.getResourceAsStream(resource)?.use { it.readBytes() }
                    ?: throw ClassNotFoundException(name)
                val clazz = defineClass(name, bytes, 0, bytes.size)
                if (resolve) resolveClass(clazz)
                return clazz
            }
        }
    }
}
