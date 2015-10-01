package org.jetbrains.ktor.http.auth.basic

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.auth.*
import org.jetbrains.ktor.http.auth.simple.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import java.util.*

val BasicAuthAttributeKey = AttributeKey<SimpleUserPassword>()

public fun RoutingEntry.basicAuthValidate(vararg challenges: HttpAuthChallenge = arrayOf(HttpAuthChallenge.basic("ktor")), validator: (SimpleUserPassword) -> Boolean) {
    auth(ApplicationRequestContext::basicAuth, validator,
            onSuccess = { t, next ->
                attributes.put(BasicAuthAttributeKey, t)
                next()
            },
            onFailed = { next ->
                response.sendAuthenticationRequest(*challenges)
            }
    )
}

fun ApplicationRequestContext.basicAuth(): SimpleUserPassword? = request.basicAuth()

fun ApplicationRequest.basicAuth(): SimpleUserPassword? {
    val parsed = parseAuthorizationHeader()
    if (parsed is HttpAuthCredentials.Single) {
        // here we can only use ISO 8859-1 character encoding because there is no character encoding specified as per RFC
        //     see http://greenbytes.de/tech/webdav/draft-reschke-basicauth-enc-latest.html
        //      http://tools.ietf.org/html/draft-ietf-httpauth-digest-15
        //      https://bugzilla.mozilla.org/show_bug.cgi?id=41489
        //      https://code.google.com/p/chromium/issues/detail?id=25790

        val userPass = Base64.getDecoder().decode(parsed.blob).toString(Charsets.ISO_8859_1)

        if (":" !in userPass) {
            return null
        }

        return SimpleUserPassword(userPass.substringBefore(":"), userPass.substringAfter(":"))
    }

    return null
}