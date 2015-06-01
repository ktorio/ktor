package ktor.application

/** Simple text result.
 */
fun ApplicationRequest.sendText(text: String) = response {
    content(text)
    send()
}

fun ApplicationRequest.sendRedirect(url: String) = response().sendRedirect(url)
fun ApplicationRequest.sendError(code: Int, message: String) = response {
    status(code)
    content(message)
    send()
}

fun ApplicationRequest.sendAuthenticationRequest(realm: String) = response {
    status(401)
    content("Not authorized")
    header("WWW-Authenticate", "Basic realm=\"$realm\"")
    send()
}


