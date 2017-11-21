package io.ktor.samples.auth

import com.auth0.jwk.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.response.*
import io.ktor.routing.*
import java.util.concurrent.*

fun Application.jwkApplication() {
    val issuer = environment.config.property("jwt.domain").getString()
    val audience = environment.config.property("jwt.audience").getString()
    val realm = environment.config.property("jwt.realm").getString()

    install(DefaultHeaders)
    install(CallLogging)
    install(Routing) {
        route("/who") {
            val jwkProvider = makeJwkProvider(issuer)

            authentication {
                jwtAuthentication(jwkProvider, issuer, realm) { credential ->
                    if (credential.payload.audience.contains(audience))
                        JWTPrincipal(credential.payload)
                    else
                        null
                }
            }
            handle {
                val principal = call.authentication.principal<JWTPrincipal>()
                val subjectString = principal!!.payload.subject.removePrefix("auth0|")
                call.respondText("Success, $subjectString")
            }
        }
    }
}

private fun makeJwkProvider(issuer: String): JwkProvider = JwkProviderBuilder(issuer)
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

