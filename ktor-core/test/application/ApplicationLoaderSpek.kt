package ktor.tests.application

import com.typesafe.config.*
import ktor.application.*
import org.jetbrains.spek.api.*
import kotlin.test.*

class ApplicationLoaderSpek : Spek() {init{

    given("an invalid class") {
        val testConfig = ConfigFactory.parseMap(
                mapOf(
                        "ktor.environment" to "test",
                        "ktor.application.package" to "ktor.test",
                        "ktor.application.class" to "NonExistingApplicationName"
                     ))
        val config = ApplicationConfig(testConfig)
        on("accessing the application") {
            val result = fails {
                ApplicationLoader(config).application
            }

            it("should raise exception: class not found") {
                shouldEqual(result?.javaClass, javaClass<ClassNotFoundException>())
            }
        }
    }

    given("a valid class") {
        val testConfig = ConfigFactory.parseMap(
                mapOf(
                        "ktor.environment" to "test",
                        "ktor.application.package" to "ktor.tests",
                        "ktor.application.class" to "ktor.tests.TestApplication"
                     ))

        val config = ApplicationConfig(testConfig)
        on("accessing the application") {

            val application = ApplicationLoader(config).application

            it("should return valid instance") {
                shouldNotBeNull(application)
            }
        }
    }
}}


