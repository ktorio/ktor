package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*

class HttpRequestContext(val client: HttpClient, val request: HttpRequest)