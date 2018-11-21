/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.tools

import org.junit.*
import org.junit.runner.*
import org.junit.runners.*
import java.io.*
import java.util.jar.*

@RunWith(Parameterized::class)
class PublicApiTest(
    private val rootDir: File,
    private val moduleName: String
) {
    companion object {
        private val modulesList = System.getProperty("validator.input.modules")
        private val nonPublicPackages: List<String> = emptyList()

        @Parameterized.Parameters(name = "{1}")
        @JvmStatic
        fun modules(): List<Array<Any>> {
            return modulesList.split(File.pathSeparator).map { path ->
                val dir = File(path)
                arrayOf<Any>(dir, dir.name)
            }
        }
    }

    @Test
    fun testApi() {
        val libsDir = File(rootDir, "/build/libs").absoluteFile.normalize()
        val jarFile = getJarPath(libsDir)
        val kotlinJvmMappingsFiles = listOf(libsDir.resolve("../visibilities.json"))
        val visibilities =
            kotlinJvmMappingsFiles
                .map { readKotlinVisibilities(it) }
                .reduce { m1, m2 -> m1 + m2 }
        val api = getBinaryAPI(JarFile(jarFile), visibilities).filterOutNonPublic(nonPublicPackages)
        api.dumpAndCompareWith(File("reference-public-api").resolve("${moduleName.removeSuffix("-jvm")}.txt"))
    }

    private fun getJarPath(libsDir: File): File {
        val regex = Regex("$moduleName.+\\.jar")
        val files = (libsDir.listFiles() ?: throw Exception("Cannot list files in $libsDir"))
            .filter {
                it.name.let {
                    it matches regex
                        && !it.endsWith("-sources.jar")
                        && !it.endsWith("-javadoc.jar")
                        && !it.endsWith("-tests.jar")
                        && !it.endsWith("-kdoc.jar")
                }
            }
        return files.singleOrNull()
            ?: throw Exception("No single file matching $regex in $libsDir:\n${files.joinToString("\n")}")
    }
}
