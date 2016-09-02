package org.jetbrains.ktor.tests.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import kotlin.reflect.*
import kotlin.test.*

private fun withLocationsApplication(test: TestApplicationHost.() -> Unit) = withApplicationFeature<TestApplication> {
    application.install(CustomLocationsFeature)
    test()
}

class CustomLocationRouteService : LocationRouteService {
    override fun findRoute(klass: KClass<*>): String? = klass.simpleName
}

object CustomLocationsFeature : ApplicationFeature<Application, Locations> {
    override val key: AttributeKey<Locations> = Locations.key

    override fun install(pipeline: Application, configure: Locations.() -> Unit): Locations {
        return Locations(DefaultConversionService(), CustomLocationRouteService()).apply(configure)
    }
}

class index()
class bye(val value: String)

class CustomLocationsTest {

    @Test fun `custom location index`() = withLocationsApplication {
        val href = application.feature(Locations).href(index())
        assertEquals("/index", href)
        application.routing {
            get<index> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/")
    }

    @Test fun `custom location bye`() = withLocationsApplication {
        val href = application.feature(Locations).href(bye("farewall"))
        assertEquals("/bye?value=farewall", href)
        application.routing {
            get<bye> {
                assertEquals("farewall", it.value)
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/")
    }

}