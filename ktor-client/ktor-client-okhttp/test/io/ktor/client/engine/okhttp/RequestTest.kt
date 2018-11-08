package io.ktor.client.engine.okhttp

import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import okhttp3.*
import kotlin.test.*

class RequestTest {

    class LoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            println("execute request: $request")
            val response = chain.proceed(request)
            println("received response: $response")
            return response
        }
    }

    @Test
    fun featuresTest() = clientTest(OkHttp) {
        config {
            engine {
                addInterceptor(LoggingInterceptor())
                addNetworkInterceptor(LoggingInterceptor())
            }
        }

        test { client ->
            client.get<String>("https://google.com")
        }
    }

}
