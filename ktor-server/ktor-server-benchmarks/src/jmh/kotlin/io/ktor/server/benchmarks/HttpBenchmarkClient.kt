package io.ktor.server.benchmarks

import okhttp3.*
import org.apache.http.client.methods.*
import org.apache.http.impl.client.*
import java.net.*

interface HttpBenchmarkClient {
    fun setup()
    fun shutdown()
    fun load(url: String)
}

class UrlHttpBenchmarkClient : HttpBenchmarkClient {
    override fun setup() {}
    override fun shutdown() {}
    override fun load(url: String) {
        val urlConnection = URL(url).openConnection() as HttpURLConnection
        urlConnection.setRequestProperty("Accept-Encoding", "gzip")
        val stream = urlConnection.inputStream
        val buf = ByteArray(8192)
        while (stream.read(buf) != -1);
        stream.close()
    }
}

class ApacheHttpBenchmarkClient : HttpBenchmarkClient {
    var httpClient: CloseableHttpClient? = null

    override fun setup() {
        val builder = HttpClientBuilder.create()
        httpClient = builder.build()
    }

    override fun shutdown() {
        httpClient!!.close()
        httpClient = null
    }

    override fun load(url: String) {
        val httpGet = HttpGet(url)
        val response = httpClient!!.execute(httpGet)
        val stream = response.entity.content
        val buf = ByteArray(8192)
        while (stream.read(buf) != -1);
        stream.close()
        response.close()
    }
}

class OkHttpBenchmarkClient : HttpBenchmarkClient {
    var httpClient: OkHttpClient? = null

    override fun setup() {
        httpClient = OkHttpClient()
    }

    override fun shutdown() {
        httpClient = null
    }

    override fun load(url: String) {
        val request = Request.Builder().url(url).build()
        val response = httpClient!!.newCall(request).execute()
        response.body().byteStream().use { stream ->
            val buf = ByteArray(8192)
            while (stream.read(buf) != -1);
        }
        response.close()
    }
}
