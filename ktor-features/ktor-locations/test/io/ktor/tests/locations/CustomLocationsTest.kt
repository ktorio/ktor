@file:UseExperimental(KtorExperimentalLocationsAPI::class)

package io.ktor.tests.locations

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import org.junit.Test
import kotlin.reflect.*
import kotlin.test.*

private fun withLocationsApplication(test: TestApplicationEngine.() -> Unit) = withTestApplication {
    application.install(CustomLocationsFeature)
    test()
}

class CustomLocationRouteService : LocationRouteService {
    override fun findRoute(locationClass: KClass<*>): String? = locationClass.simpleName
}

object CustomLocationsFeature : ApplicationFeature<Application, Locations, Locations> {
    override val key: AttributeKey<Locations> = Locations.key

    override fun install(pipeline: Application, configure: Locations.() -> Unit): Locations {
        return Locations(pipeline, CustomLocationRouteService()).apply(configure)
    }
}

class index()
class bye(val value: String)

@Location("entity/{id}")
class entity(val id: EntityID)

data class EntityID(val typeId: Int, val entityId: Int)

@UseExperimental(KtorExperimentalLocationsAPI::class)
class CustomLocationsTest {

    @Test
    fun `custom location index`() = withLocationsApplication {
        val href = application.locations.href(index())
        assertEquals("/index", href)
        application.routing {
            get<index> {
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/")
    }

    @Test
    fun `custom location bye`() = withLocationsApplication {
        val href = application.locations.href(bye("farewall"))
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

    @Test
    fun `custom data conversion`() = withTestApplication {
        application.install(Locations)
        application.install(DataConversion) {
            convert<EntityID> {
                decode { values, _ ->
                    val (typeId, entityId) = values.single().split('-').map { it.toInt() }
                    EntityID(typeId, entityId)
                }

                encode { value ->
                    when (value) {
                        null -> listOf()
                        is EntityID -> listOf("${value.typeId}-${value.entityId}")
                        else -> throw DataConversionException("Cannot convert $value as EntityID")
                    }
                }
            }
        }

        val href = application.locations.href(entity(EntityID(42, 999)))
        assertEquals("/entity/42-999", href)
        application.routing {
            get<entity> {
                assertEquals(42, it.id.typeId)
                assertEquals(999, it.id.entityId)
                call.respond(HttpStatusCode.OK)
            }
        }
        urlShouldBeHandled(href)
        urlShouldBeUnhandled("/")
    }
}
