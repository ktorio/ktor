package io.ktor.compiler.services

import io.ktor.compiler.services.KtorTestEnvironmentProperties.openApiExpectedFile
import io.ktor.compiler.services.KtorTestEnvironmentProperties.openApiOutputFile
import io.ktor.compiler.services.KtorTestEnvironmentProperties.samplesLocation
import io.ktor.compiler.services.KtorTestEnvironmentProperties.replaceSnapshots
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.AdditionalSourceProvider
import org.jetbrains.kotlin.test.services.TestModuleStructure
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.configuration.kotlinFiles
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.readText

class KtorOpenApiTestAdditionalSources(testServices: TestServices) : AdditionalSourceProvider(testServices) {
    companion object {
        val constantPattern by lazy {
            Regex("const val ([A-Z_]+) = [^\n]+")
        }
    }

    override fun produceAdditionalFiles(
        globalDirectives: RegisteredDirectives,
        module: TestModule,
        testModuleStructure: TestModuleStructure
    ): List<TestFile> {
        val boxTestRelativePath = "${samplesLocation}/tests/OpenApiTestRunner.kt"
        val boxTestPath = Paths.get(boxTestRelativePath)
        val rawBoxTestText = Files.readString(boxTestPath)
        val testName = module.kotlinFiles.first().name
        val facadeClassName = testName.replace(".kt", "Kt")
        val functionName = "install${testName.removeSuffix(".kt")}"
        val testFunctionFqName = "openapi.$facadeClassName.$functionName"
        val expectedContent = runCatching {
            Paths.get(testServices.openApiExpectedFile).readText()
        }.getOrNull()
        val templatedContent = rawBoxTestText.replace(constantPattern) { match ->
            val constantName = match.groupValues[1]
            val value = when(constantName) {
                "MODULE_REFERENCE" -> "$$\"$testFunctionFqName\""
                "SNAPSHOT_REPLACE" -> replaceSnapshots.toString()
                "SNAPSHOT_FILE" -> "$$\"${testServices.openApiExpectedFile}\""
                "ACTUAL_FILE" -> "$$\"${testServices.openApiOutputFile}\""
                "EXPECTED_JSON" -> expectedContent?.let { $$"$$\"\"\"\n$$it\n\"\"\"" } ?: "\"\""
                else -> error("Unknown constant: $constantName")
            }
            "const val $constantName = $value"
        }

        return listOf(TestFile(
            relativePath = "build/generated-tests/$testName",
            originalContent = templatedContent,
            originalFile = boxTestPath.toFile(),
            startLineNumberInOriginalFile = 0,
            isAdditional = true,
            directives = RegisteredDirectives.Empty
        ))
    }
}