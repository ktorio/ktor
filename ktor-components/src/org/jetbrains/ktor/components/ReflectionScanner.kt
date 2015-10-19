package org.jetbrains.ktor.components

import java.io.*
import java.net.*
import java.util.jar.*

fun ClassLoader.scanForClasses(prefix: String): Sequence<Class<*>> {
    val path = prefix.replace(".", "/")
    val urls = getResources(path).toList()
    return urls.asSequence().map {
        it.scanForClasses(prefix, this@scanForClasses)
    }.flatten()

}

private fun URL.scanForClasses(prefix: String = "", classLoader: ClassLoader): Sequence<Class<*>> {
    return when (protocol) {
        "jar" -> JarFile(urlDecode(toExternalForm().substringAfter("file:").substringBeforeLast("!"))).scanForClasses(prefix, classLoader)
        else -> File(urlDecode(path)).scanForClasses(prefix, classLoader)
    }
}

private fun String.packageToPath() = replace(".", File.separator)

private fun File.scanForClasses(prefix: String, classLoader: ClassLoader): Sequence<Class<*>> {
    val packageRoot = File(this, prefix.packageToPath())
    return packageRoot.walkTopDown().treeFilter {
        it.isDirectory || (it.isFile && it.extension == "class")
    }.map {
        if (it.isFile) {
            val classFileLocation = it.absolutePath
            val relativeToRoot = classFileLocation.removePrefix(path).removePrefix(File.separator)
            val className = relativeToRoot.removeSuffix(".class").replace(File.separator, ".")
            val clazz = classLoader.tryLoadClass(className)
            clazz
        } else
            null
    }.filterNotNull()
}

private fun JarFile.scanForClasses(prefix: String, classLoader: ClassLoader): Sequence<Class<*>> {
    val path = prefix.replace(".", "/") + "/"
    return entries().asSequence().map {
        if (!it.isDirectory && it.name.endsWith(".class") && it.name.contains(path)) {
            classLoader.tryLoadClass(prefix + "." + it.name.substringAfterLast(path).removeSuffix(".class").replace("/", "."))
        } else
            null
    }.filterNotNull()
}

public fun ClassLoader.tryLoadClass(fqName: String): Class<*>? {
    try {
        return loadClass(fqName)
    } catch (e: ClassNotFoundException) {
        return null
    }
}

private fun urlDecode(encoded: String): String {
    try {
        return URLDecoder.decode(encoded, "UTF-8")
    } catch(e: Exception) {
        return encoded
    }
}
