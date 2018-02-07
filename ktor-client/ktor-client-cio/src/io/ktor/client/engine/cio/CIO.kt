package io.ktor.client.engine.cio

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import kotlinx.coroutines.experimental.*

object CIO : HttpClientEngineFactory<CIOEngineConfig> {
    override fun create(block: CIOEngineConfig.() -> Unit): HttpClientEngine =
            CIOEngine(CIOEngineConfig().apply(block))
}


fun main(args: Array<String>) = runBlocking {
    val client = HttpClient(CIO)

    val response = client.get<String>("https://www.google.ru/")
    println(response)
    client.close()
}