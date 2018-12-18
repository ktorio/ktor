package io.ktor.client.engine.urlfetch

import io.ktor.client.engine.*
import java.net.HttpURLConnection

class UrlFetchConfig : HttpClientEngineConfig() {

    var followRedirects = false
    var useCaches = true
    var allowUserInteraction = false

    internal fun applyOn(connection: HttpURLConnection) = connection.let {
        it.instanceFollowRedirects = followRedirects
        it.useCaches = useCaches
        it.allowUserInteraction = allowUserInteraction
    }
}
