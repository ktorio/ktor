package io.ktor.samples.auth

import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.response.*
import io.ktor.routing.*

fun Application.jwtApplication() {
    val issuer = environment.config.property("jwt.domain").getString()
    val audience = environment.config.property("jwt.audience").getString()
    val realm = environment.config.property("jwt.realm").getString()

    install(DefaultHeaders)
    install(CallLogging)
    install(Routing) {
        route("/who") {
            authentication {
                val jwtVerifier = makeJwtVerifier(issuer, audience)
                jwtAuthentication(jwtVerifier, realm) { credential ->
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

private val algorithm = Algorithm.HMAC256("secret")
private fun makeJwtVerifier(issuer: String, audience: String): JWTVerifier = JWT
        .require(algorithm)
        .withAudience(audience)
        .withIssuer(issuer)
        .build()