/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import android.content.Context
import android.content.ContextWrapper
import android.net.http.*
import android.os.Build
import android.os.ext.SdkExtensions
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.internal.bytecode.*
import org.robolectric.sandbox.ShadowMatcher
import org.robolectric.util.ReflectionHelpers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.Executor
import kotlin.test.*

@EnabledForJreRange(min = JRE.JAVA_21)
class AndroidHttpEngineRobolectricTest {

    @Test
    fun testHttpEngineWithRobolectricShadows() {
        RobolectricHttpEngineTestRunner.executeInSandbox()
    }
}

internal object RobolectricHttpEngineTestRunner {
    fun executeInSandbox() {
        val config = InstrumentationConfiguration.newBuilder()
            .addInstrumentedPackage("android.")
            .doNotAcquirePackage("org.jetbrains.")
            .doNotAcquirePackage("kotlinx.")
            .doNotAcquirePackage("kotlin.")
            .build()

        val classLoader = Thread.currentThread().contextClassLoader
        val resourceProvider = object : ResourceProvider {
            override fun getResource(name: String): URL? = classLoader.getResource(name)
            override fun getResourceAsStream(name: String): InputStream? = classLoader.getResourceAsStream(name)
            override fun close() {}
        }
        val sandbox = Sandbox(config, resourceProvider, ClassInstrumentor())

        val shadowMap = ShadowMap.Builder()
            .addShadowClasses(
                sandbox.bootstrappedClass<Any>(ShadowBuild::class.java),
                sandbox.bootstrappedClass<Any>(ShadowBuildVersion::class.java),
                sandbox.bootstrappedClass<Any>(ShadowSdkExtensions::class.java),
                sandbox.bootstrappedClass<Any>(ShadowHttpEngineBuilder::class.java),
                sandbox.bootstrappedClass<Any>(ShadowAndroidHttpProxy::class.java),
                sandbox.bootstrappedClass<Any>(ShadowProxyOptions::class.java),
            )
            .build()

        sandbox.replaceShadowMap(shadowMap)
        sandbox.configure(ShadowWrangler(shadowMap, ShadowMatcher.MATCH_ALL, Interceptors()), Interceptors())

        val previousVmName = System.getProperty("java.vm.name")
        System.setProperty("java.vm.name", "Dalvik")
        try {
            sandbox.runOnMainThread {
                val runner = sandbox.bootstrappedClass<Any>(SandboxAssertions::class.java)
                runner.getMethod("runAssertions").invoke(null)
            }
        } finally {
            if (previousVmName != null) {
                System.setProperty("java.vm.name", previousVmName)
            } else {
                System.clearProperty("java.vm.name")
            }
        }
    }
}

@Suppress("FunctionName")
@Implements(Build::class)
class ShadowBuild {
    companion object {
        @Implementation
        @JvmStatic
        fun __staticInitializer__() {}
    }
}

@Suppress("FunctionName")
@Implements(Build.VERSION::class)
class ShadowBuildVersion {
    companion object {
        @Implementation
        @JvmStatic
        fun __staticInitializer__() {
            ReflectionHelpers.setStaticField(
                Build.VERSION::class.java,
                "SDK_INT",
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            )
            ReflectionHelpers.setStaticField(
                Build.VERSION::class.java,
                "CODENAME",
                "REL"
            )
        }
    }
}

@Suppress("FunctionName")
@Implements(SdkExtensions::class)
class ShadowSdkExtensions {
    companion object {
        var extensionVersion: Int = 7

        @Implementation
        @JvmStatic
        fun __staticInitializer__() {}

        @Implementation
        @JvmStatic
        fun getExtensionVersion(extension: Int): Int = extensionVersion
    }
}

@Implements(android.net.http.Proxy::class)
class ShadowAndroidHttpProxy {
    companion object {
        var lastScheme: Int? = null
        var lastHost: String? = null
        var lastPort: Int? = null

        fun reset() {
            lastScheme = null
            lastHost = null
            lastPort = null
        }

        @Implementation
        @JvmStatic
        fun createHttpProxy(
            scheme: Int,
            host: String,
            port: Int,
            executor: Executor,
            callback: android.net.http.Proxy.HttpConnectCallback
        ): android.net.http.Proxy {
            lastScheme = scheme
            lastHost = host
            lastPort = port
            return ReflectionHelpers.callConstructor(android.net.http.Proxy::class.java)
        }
    }
}

@Implements(ProxyOptions::class)
class ShadowProxyOptions {
    companion object {
        var lastProxiesCount: Int = 0
        var lastFallbackBehavior: Int? = null

        fun reset() {
            lastProxiesCount = 0
            lastFallbackBehavior = null
        }

        @Implementation
        @JvmStatic
        fun fromProxyList(
            proxyList: List<android.net.http.Proxy>,
            fallbackBehavior: Int
        ): ProxyOptions {
            lastProxiesCount = proxyList.size
            lastFallbackBehavior = fallbackBehavior
            return ReflectionHelpers.callConstructor(ProxyOptions::class.java)
        }
    }
}

@Suppress("FunctionName")
@Implements(HttpEngine.Builder::class)
class ShadowHttpEngineBuilder {
    @RealObject
    private lateinit var realBuilder: HttpEngine.Builder

    companion object {
        var constructedWithContext: Context? = null
        var brotliEnabled: Boolean = false
        val quicHints = mutableListOf<Triple<String, Int, Int>>()
        var proxyOptionsSet: ProxyOptions? = null
        var lastBuiltEngine: FakeHttpEngine? = null

        fun reset() {
            constructedWithContext = null
            brotliEnabled = false
            quicHints.clear()
            proxyOptionsSet = null
            lastBuiltEngine = null
        }
    }

    @Implementation
    fun __constructor__(context: Context) {
        constructedWithContext = context
    }

    @Implementation
    fun setEnableBrotli(value: Boolean): HttpEngine.Builder {
        brotliEnabled = value
        return realBuilder
    }

    @Implementation
    fun addQuicHint(host: String, port: Int, alternatePort: Int): HttpEngine.Builder {
        quicHints.add(Triple(host, port, alternatePort))
        return realBuilder
    }

    @Implementation
    fun setDnsOptions(options: DnsOptions): HttpEngine.Builder = realBuilder

    @Implementation
    fun setConnectionMigrationOptions(options: ConnectionMigrationOptions): HttpEngine.Builder = realBuilder

    @Implementation
    fun setQuicOptions(options: QuicOptions): HttpEngine.Builder = realBuilder

    @Implementation
    fun setProxyOptions(options: ProxyOptions): HttpEngine.Builder {
        proxyOptionsSet = options
        return realBuilder
    }

    @Implementation
    fun build(): HttpEngine {
        return FakeHttpEngine().also { lastBuiltEngine = it }
    }
}

class FakeHttpEngine : HttpEngine() {
    val openedUrls = mutableListOf<URL>()
    var isShutdown = false
    var responseStatus = 200
    var responseMessageText = "OK"
    var responseBodyBytes = "Hello from Shadow HttpEngine".encodeToByteArray()
    var responseHeadersMap: Map<String, List<String>> = mapOf(
        "Content-Type" to listOf("text/plain")
    )
    var lastConnection: FakeHttpURLConnection? = null

    override fun openConnection(url: URL): URLConnection {
        openedUrls.add(url)
        return FakeHttpURLConnection(
            url = url,
            status = responseStatus,
            message = responseMessageText,
            bodyBytes = responseBodyBytes,
            headers = responseHeadersMap
        ).also { lastConnection = it }
    }

    override fun shutdown() {
        isShutdown = true
    }

    override fun createUrlStreamHandlerFactory(): java.net.URLStreamHandlerFactory {
        throw UnsupportedOperationException()
    }

    override fun newUrlRequestBuilder(
        url: String,
        executor: Executor,
        callback: UrlRequest.Callback
    ): UrlRequest.Builder {
        throw UnsupportedOperationException()
    }

    override fun newBidirectionalStreamBuilder(
        url: String,
        executor: Executor,
        callback: BidirectionalStream.Callback
    ): BidirectionalStream.Builder {
        throw UnsupportedOperationException()
    }
}

class FakeHttpURLConnection(
    url: URL,
    private val status: Int,
    private val message: String,
    private val bodyBytes: ByteArray,
    private val headers: Map<String, List<String>>
) : HttpURLConnection(url) {
    val requestOutput = ByteArrayOutputStream()
    var disconnected = false

    override fun connect() {
        connected = true
    }

    override fun disconnect() {
        disconnected = true
    }

    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int = status

    override fun getResponseMessage(): String = message

    override fun getInputStream(): InputStream = ByteArrayInputStream(bodyBytes)

    override fun getErrorStream(): InputStream? =
        if (status >= 400) ByteArrayInputStream(bodyBytes) else null

    override fun getOutputStream(): OutputStream = requestOutput

    override fun getHeaderFields(): Map<String, List<String>> = headers
}

object SandboxAssertions {
    @JvmStatic
    fun runAssertions() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        val appContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }
        val activityContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = appContext
        }

        // 1. Verify availability checks across SDK Extension versions & proxy types
        ShadowSdkExtensions.extensionVersion = 6
        val configNoExt = AndroidEngineConfig().apply {
            useHttpEngine(activityContext)
        }
        assertFalse(isHttpEngineAvailable(configNoExt), "HttpEngine requires SDK Extensions S >= 7")

        ShadowSdkExtensions.extensionVersion = 7
        assertTrue(isHttpEngineAvailable(configNoExt), "HttpEngine should be available on SDK Extensions S == 7")
        assertSame(appContext, configNoExt.context, "useHttpEngine should prefer applicationContext")

        configNoExt.httpEngineEnabled = false
        assertFalse(isHttpEngineAvailable(configNoExt), "HttpEngine should be disabled when httpEngineEnabled is false")
        configNoExt.httpEngineEnabled = true

        val configHttpProxy = AndroidEngineConfig().apply {
            useHttpEngine(activityContext)
            proxy = ProxyBuilder.http("http://127.0.0.1:8888/")
        }
        assertFalse(
            isHttpEngineAvailable(configHttpProxy),
            "HTTP proxy on HttpEngine requires SDK Extensions S >= 22"
        )

        ShadowSdkExtensions.extensionVersion = 22
        assertTrue(
            isHttpEngineAvailable(configHttpProxy),
            "HTTP proxy on HttpEngine should be supported on SDK Extensions S >= 22"
        )

        val configSocksProxy = AndroidEngineConfig().apply {
            useHttpEngine(activityContext)
            proxy = ProxyBuilder.socks("127.0.0.1", 1080)
        }
        assertFalse(
            isHttpEngineAvailable(configSocksProxy),
            "SOCKS proxy is not supported by HttpEngine and must fall back to URLConnection"
        )

        // 2. Verify end-to-end request execution, proxy setup, builder config, and shutdown via AndroidClientEngine
        ShadowHttpEngineBuilder.reset()
        ShadowAndroidHttpProxy.reset()
        ShadowProxyOptions.reset()

        runBlocking {
            val client = HttpClient(Android) {
                engine {
                    proxy = ProxyBuilder.http("http://proxy.example.com:8080/")
                    useHttpEngine(activityContext) {
                        setEnableBrotli(true)
                    }
                    useHttpEngine(activityContext) {
                        addQuicHint("ktor.io", 443, 443)
                    }
                }
            }

            val response = client.post("https://ktor.io/test") {
                header("X-Test-Header", "Ktor-Android-14")
                setBody("request-payload")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Hello from Shadow HttpEngine", response.body<String>())

            assertSame(appContext, ShadowHttpEngineBuilder.constructedWithContext)
            assertTrue(ShadowHttpEngineBuilder.brotliEnabled)
            assertEquals(listOf(Triple("ktor.io", 443, 443)), ShadowHttpEngineBuilder.quicHints)

            assertEquals(android.net.http.Proxy.SCHEME_HTTP, ShadowAndroidHttpProxy.lastScheme)
            assertEquals("proxy.example.com", ShadowAndroidHttpProxy.lastHost)
            assertEquals(8080, ShadowAndroidHttpProxy.lastPort)
            assertEquals(1, ShadowProxyOptions.lastProxiesCount)
            assertEquals(
                ProxyOptions.ALL_PROXIES_FAILED_BEHAVIOR_DISALLOW_DIRECT,
                ShadowProxyOptions.lastFallbackBehavior
            )
            assertNotNull(ShadowHttpEngineBuilder.proxyOptionsSet)

            val engine = assertNotNull(ShadowHttpEngineBuilder.lastBuiltEngine)
            assertEquals(listOf(URL("https://ktor.io/test")), engine.openedUrls)

            val connection = assertNotNull(engine.lastConnection)
            assertEquals("POST", connection.requestMethod)
            assertEquals("Ktor-Android-14", connection.getRequestProperty("X-Test-Header"))
            assertEquals("request-payload", connection.requestOutput.toString(Charsets.UTF_8.name()))
            assertFalse(engine.isShutdown)

            client.close()
            client.coroutineContext[Job]?.join()
            assertTrue(engine.isShutdown, "HttpEngine.shutdown() should be called when AndroidClientEngine closes")

            // 3. Verify cancelling HttpClient also triggers HttpEngine.shutdown()
            ShadowHttpEngineBuilder.reset()
            val cancelledClient = HttpClient(Android) {
                engine {
                    useHttpEngine(activityContext)
                }
            }
            assertEquals("Hello from Shadow HttpEngine", cancelledClient.get("https://ktor.io/cancel").body<String>())
            val cancelledEngine = assertNotNull(ShadowHttpEngineBuilder.lastBuiltEngine)
            assertFalse(cancelledEngine.isShutdown)

            cancelledClient.cancel()
            cancelledClient.coroutineContext[Job]?.join()
            assertTrue(
                cancelledEngine.isShutdown,
                "HttpEngine.shutdown() should be called when HttpClient is cancelled"
            )
        }
    }
}
