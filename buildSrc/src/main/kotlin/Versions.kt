/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

val jdk8Modules = setOf(
    "ktor-client-tests",

    "ktor-server-core", "ktor-server-host-common", "ktor-server-servlet", "ktor-server-netty", "ktor-server-tomcat",
    "ktor-server-test-host", "ktor-server-test-suites",

    "ktor-websockets", "ktor-webjars", "ktor-metrics", "ktor-server-sessions", "ktor-auth", "ktor-auth-jwt",

    "ktor-network-tls-certificates"
)

val jdk7Modules = setOf(
    "ktor-http",
    "ktor-http-cio",
    "ktor-utils",
    "ktor-network-tls",
    "ktor-websockets",
    "ktor-client-okhttp",
    "ktor-metrics-micrometer"
)

val jdk11Modules = setOf(
    "ktor-client-java"
)
