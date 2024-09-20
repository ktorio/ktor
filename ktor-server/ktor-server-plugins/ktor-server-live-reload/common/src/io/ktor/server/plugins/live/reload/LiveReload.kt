/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.server.plugins.live.reload

import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

private const val FILE_CHANGES_LOCATION = "/server-file-changes"

private const val WAIT_FOR_RESPONSE = """
(function checkForChanges() {
    fetch('/server-file-changes')
        .then(response => {
            if (response.status === 200) {
                window.location.reload();
            } else {
                setTimeout(checkForChanges, 1000);
            }
        })
        .catch(() => {
            setTimeout(() => {
                window.location.reload();                
            }, 200);
        });
})();
"""

public class LiveReloadConfig {
    public var developmentMode: Boolean = false
}

public val LiveReload: ApplicationPlugin<LiveReloadConfig> = createApplicationPlugin("LiveReload", ::LiveReloadConfig) {
    if (application.developmentMode) {
        val clients = mutableListOf<CancellableContinuation<Boolean>>()

        onCallRespond { _, body ->
            val textContent = body as? TextContent ?: return@onCallRespond
            if (ContentType.Text.Html.match(textContent.contentType)) return@onCallRespond
            val modifiedContent = textContent.text.replace(
                "</body>",
                "<script>$WAIT_FOR_RESPONSE</script></body>"
            )
            transformBody {
                TextContent(modifiedContent, textContent.contentType, textContent.status)
            }
        }
        application.routing {
            get(FILE_CHANGES_LOCATION) {
                val refresh = suspendCancellableCoroutine<Boolean> {
                    clients.add(it)
                }
                call.response.status(if (refresh) HttpStatusCode.OK else HttpStatusCode.Gone)
            }
        }
        // TODO There is actually no event published when the server restarts
        application.monitor.subscribe(ApplicationStopping) {
            for (client in clients) {
                client.resume(true)
            }
        }
    }
}
