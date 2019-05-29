/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.validator

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
        private val artifactNames = System.getProperty("validator.input.artifactNames")
        private val nonPublicPackages: List<String> = emptyList()

        @Parameterized.Parameters(name = "{1}")
        @JvmStatic
        fun modules(): List<Array<Any>> {
            val names = artifactNames.split(File.pathSeparator)
            return modulesList.split(File.pathSeparator).mapIndexed { id, path ->
                val dir = File(path)
                arrayOf<Any>(dir, names[id])
            }
        }
    }

    @Test
    fun testApi() {
        val libsDir = File(rootDir, "/build/libs").absoluteFile.normalize()
        val jarFile = getJarPath(libsDir)

        println("Reading binary API from $jarFile")
        val api = getBinaryAPI(JarFile(jarFile)).filterOutNonPublic(nonPublicPackages)
        val target = File("reference-public-api").resolve("$moduleName.txt")

        api.dumpAndCompareWith(target)
    }

    private fun getJarPath(libsDir: File): File {
        val regex = Regex("$moduleName-jvm.+\\.jar")
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
