package org.jetbrains.ktor.samples.jwt

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import org.jetbrains.ktor.application.Application
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.jwt.JWTPrincipal
import org.jetbrains.ktor.auth.authentication
import org.jetbrains.ktor.jwt.jwtAuthentication
import org.jetbrains.ktor.features.CallLogging
import org.jetbrains.ktor.features.DefaultHeaders
import org.jetbrains.ktor.gson.GsonSupport
import org.jetbrains.ktor.response.respond
import org.jetbrains.ktor.routing.Routing
import org.jetbrains.ktor.routing.route
import java.util.concurrent.TimeUnit

data class Who(val name: String, val planet: String)

fun Application.JwtApplication() {
    val issuer = environment.config.property("jwt.domain").getString()
    val audience = environment.config.property("jwt.audience").getString()
    install(DefaultHeaders)
    install(CallLogging)
    install(GsonSupport)
    install(Routing) {
        route("/who") {
            val jwkProvider = makeJwkProvider(issuer)

            authentication {
                jwtAuthentication(jwkProvider, issuer) { credential ->
                    when {
                        credential.payload.audience.contains(audience)
                            -> JWTPrincipal(credential.payload)
                        else -> null
                    }
                }
            }
            handle {
                val principal = call.authentication.principal<JWTPrincipal>()
                val subjectString = principal!!.payload.subject.removePrefix("auth0|")
                val doktor = Who(subjectString, "Gallifrey")
                call.respond(doktor)
            }
        }
    }
}

private fun makeJwkProvider(issuer: String): JwkProvider {
    val jwkProvider = JwkProviderBuilder(issuer)
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()
    return jwkProvider
}

