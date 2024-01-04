package io.ktor.csrf

import io.ktor.server.application.*
import io.ktor.server.plugins.csrf.*

public fun Application.install() {
    install(CSRF) {
        // tests Origin is an expected value
        allowOrigin("http://localhost:8080")

        // tests Origin matches Host header
        originMatchesHost()

        // custom header checks
        checkHeader("X-CSRF-Token")
    }
}
