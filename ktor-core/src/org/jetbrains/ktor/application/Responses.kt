package org.jetbrains.ktor.application

/** Simple text result.
 */
fun ApplicationRequest.sendText(text: String) = respond {
    content(text)
    send()
}

fun ApplicationRequest.sendRedirect(url: String) = respond { sendRedirect(url) }
fun ApplicationRequest.sendError(code: Int, message: String) = respond {
    status(code)
    content(message)
    send()
}

fun ApplicationRequest.sendAuthenticationRequest(realm: String) = respond {
    status(401)
    content("Not authorized")
    header("WWW-Authenticate", "Basic realm=\"$realm\"")
    send()
}


