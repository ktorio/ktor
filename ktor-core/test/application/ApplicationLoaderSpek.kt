package ktor.tests.application

import ktor.application.*
import kotlin.test.*
import org.jetbrains.spek.api.*

class ApplicationLoaderSpek : Spek() {{

    given("an invalid class") {

        val config = ApplicationConfig(MemoryConfig { set("environment", "test") })
        config.set("ktor.application.package", "ktor.test")
        config.set("ktor.application.class", "NonExistingApplicationName")

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

        val config = ApplicationConfig(MemoryConfig { set("environment", "test") })
        config.set("ktor.application.package", "ktor.tests")
        config.set("ktor.application.class", "ktor.tests.TestApplication")

        on("accessing the application") {

            val application = ApplicationLoader(config).application

            it("should return valid instance") {
                shouldNotBeNull(application)
            }
        }

    }

}
}


