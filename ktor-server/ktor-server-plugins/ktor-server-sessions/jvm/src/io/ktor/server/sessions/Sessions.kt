/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.sessions

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*

private object BeforeSend : Hook<suspend (ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        pipeline.sendPipeline.intercept(ApplicationSendPipeline.Before) {
            handler(call)
        }
    }
}

internal val SessionProvidersKey = AttributeKey<List<SessionProvider<*>>>("SessionProvidersKey")

/**
 * A plugin that provides a mechanism to persist data between different HTTP requests.
 * Typical use cases include storing a logged-in user's ID, the contents of a shopping basket,
 * or keeping user preferences on the client.
 * In Ktor, you can implement sessions by using cookies or custom headers,
 * choose whether to store session data on the server or pass it to the client,
 * sign and encrypt session data and more.
 *
 * You can learn more from [Sessions](https://ktor.io/docs/sessions.html).
 * @property providers list of session providers
 */
public val Sessions: RouteScopedPlugin<SessionsConfig> = createRouteScopedPlugin("Sessions", ::SessionsConfig) {
    val providers = pluginConfig.providers.toList()

    application.attributes.put(SessionProvidersKey, providers)

    onCall { call ->
        // For each call, call each provider and retrieve session data if needed.
        // Capture data in the attribute's value
        val providerData = providers.associateBy({ it.name }) {
            it.receiveSessionData(call)
        }
        val sessionData = SessionData(providerData)
        call.attributes.put(SessionDataKey, sessionData)
    }

    // When response is being sent, call each provider to update/remove session data
    on(BeforeSend) { call ->

        /*
         If sessionData is not available it means response happened before Session plugin got a chance to deserialize the data.
         We should ignore this call in this case.

         An example would be CORS plugin responding with 403 Forbidden
         */
        val sessionData = call.attributes.getOrNull(SessionDataKey) ?: return@on

        sessionData.providerData.values.forEach { data ->
            data.sendSessionData(call)
        }

        sessionData.commit()
    }
}
