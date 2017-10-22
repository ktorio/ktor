package org.jetbrains.ktor.samples.jwt

import com.auth0.jwk.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.jwt.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.jwt.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.gson.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import java.util.concurrent.*

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

