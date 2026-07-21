package io.ktor.compiler.services

import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.testInfo
import java.io.File

object KtorTestEnvironmentProperties {
    val samplesClasspath by lazy {
        System.getProperty("testSamples.classpath")
            ?.split(File.pathSeparator)?.map(::File)
            ?: error("Unable to get a valid classpath from the 'testSamples.classpath' property")
    }
    val samplesLocation by lazy {
        System.getProperty("testSamples.location")
            ?: error("'testSamples.location' is not set")
    }
    val replaceSnapshots by lazy {
        System.getProperty("testSamples.replaceSnapshots")?.toBoolean() ?: false
    }
    val TestServices.openApiOutputFile: String get() {
        val testCase = testInfo.methodName.removePrefix("test")
        return "$samplesLocation/openapi/$testCase.actual.json"
    }
    val TestServices.openApiExpectedFile: String get() {
        val testCase = testInfo.methodName.removePrefix("test")
        return "$samplesLocation/openapi/$testCase.expected.json"
    }
}