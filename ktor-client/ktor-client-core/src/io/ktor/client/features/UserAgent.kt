package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * Default user-agent feature for [HttpClient].
 *
 * [agent] - value of user-agent header to set.
 */
class UserAgent(val agent: String) {

    class Config(var agent: String = "Ktor http-client")

    companion object Feature : HttpClientFeature<Config, UserAgent>{
        override val key: AttributeKey<UserAgent> = AttributeKey("UserAgent")

        override fun prepare(block: Config.() -> Unit): UserAgent = UserAgent(Config().apply(block).agent)

        override fun install(feature: UserAgent, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                context.header(HttpHeaders.UserAgent, feature.agent)
            }
        }
    }
}

/**
 * Install [UserAgent] feature with browser-like user agent.
 */
fun HttpClientConfig<*>.BrowserUserAgent() {
    install(UserAgent) {
        agent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/70.0.3538.77 Chrome/70.0.3538.77 Safari/537.36"
    }
}

/**
 * Install [UserAgent] feature with browser-like user agent.
 */
fun HttpClientConfig<*>.CurlUserAgent() {
    install(UserAgent) {
        agent = "curl/7.61.0"
    }
}

