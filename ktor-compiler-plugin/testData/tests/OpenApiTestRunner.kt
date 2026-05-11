package tests

import com.typesafe.config.ConfigFactory
import io.ktor.server.routing.openapi.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.fail

const val MODULE_REFERENCE = ""
const val ACTUAL_FILE = ""
const val SNAPSHOT_FILE = ""
const val SNAPSHOT_REPLACE = false
const val EXPECTED_JSON = ""

val json = Json {
    encodeDefaults = false
    prettyPrint = true
    prettyPrintIndent = "    "
}

fun box(): String {
    testApplication {
        environment {
            config = HoconApplicationConfig(
                ConfigFactory.parseString(
                    """
                        ktor {
                            application {
                                modules = [ $MODULE_REFERENCE ]
                            }
                        }
                    """.trimIndent()
                )
            )
        }

        routing {
            get("/openapi.json") {
                try {
                    val routes = OpenApiDoc(info = OpenApiInfo("OpenAPI Document", version = "1.0.0")) +
                            call.application.routingRoot.descendants()
                    call.respondText(
                        json.encodeToString(
                            routes.copy(
                                paths = routes.paths - "/openapi.json"
                            )
                        )
                    )
                } catch (e: Throwable) {
                    call.respondText(
                        status = HttpStatusCode.InternalServerError,
                        text = e.stackTraceToString()
                    )
                }
            }
        }
        val response = client.get("/openapi.json")
        val responseText = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            System.err.println("""
                Model request failed.
                
                ${response.status}
                $responseText
            """.trimIndent())
            fail("Request for model failed. See logs for details.")
        }
        val actualFile = Path(ACTUAL_FILE)
        val expectedFile = Path(SNAPSHOT_FILE)
        actualFile.writeText(responseText)
        if (SNAPSHOT_REPLACE) {
            actualFile.copyTo(expectedFile, overwrite = true)
        } else {
            assertEquals(EXPECTED_JSON.trim(), responseText.trim())
        }
    }
    return "OK"
}
