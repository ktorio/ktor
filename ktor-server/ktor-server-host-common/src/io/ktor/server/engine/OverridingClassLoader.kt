package io.ktor.server.engine

import java.io.*
import java.net.*

/**
 * A parent-last classloader that will try the child classloader first and then the parent.
 */
internal class OverridingClassLoader(classpath: List<URL>, parentClassLoader: ClassLoader?) : ClassLoader(parentClassLoader), Closeable {
    private val childClassLoader = ChildURLClassLoader(classpath.toTypedArray(), parent)

    @Synchronized
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        try {
            // first we try to find a class inside the child classloader
            return childClassLoader.findClass(name)
        } catch (e: ClassNotFoundException) {
            // didn't find it, try the parent
            return super.loadClass(name, resolve)
        }
    }

    override fun close() {
        childClassLoader.close()
    }

    /**
     * This class delegates (child then parent) for the findClass method for a URLClassLoader.
     * We need this because findClass is protected in URLClassLoader
     */
    private class ChildURLClassLoader(urls: Array<URL>, private val realParent: ClassLoader) : URLClassLoader(urls, null) {
        public override fun findClass(name: String): Class<*> {
            val loaded = super.findLoadedClass(name)
            if (loaded != null)
                return loaded

            try {
                // first try to use the URLClassLoader findClass
                return super.findClass(name)
            } catch (e: ClassNotFoundException) {
                // if that fails, we ask our real parent classloader to load the class (we give up)
                return realParent.loadClass(name)
            }
        }
    }
}