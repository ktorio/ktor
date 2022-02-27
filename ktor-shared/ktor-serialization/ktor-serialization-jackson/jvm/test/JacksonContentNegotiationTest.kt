import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class JacksonContentNegotiationTest : JsonContentNegotiationTest(JacksonConverter()) {
    override val extraFieldResult = HttpStatusCode.BadRequest
}
