package io.ktor.compiler

import io.ktor.compiler.runners.AbstractOpenapiTest
import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5

fun main() {
    generateTestGroupSuiteWithJUnit5 {
        testGroup(testDataRoot = "ktor-compiler-plugin/testData", testsRoot = "ktor-compiler-plugin/test-gen") {
            testClass<AbstractOpenapiTest> {
                model("openapi")
            }
        }
    }
}
