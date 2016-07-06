package org.jetbrains.ktor.http

interface RequestSocketRoute {
    val scheme: String
    val port: Int
    val host: String

    val remoteHost: String
}