package io.ktor.tests.server.jetty

import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.servlet.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.eclipse.jetty.servlet.*
import org.junit.*
import org.slf4j.*
import java.security.*
import java.util.concurrent.locks.*
import javax.servlet.*
import javax.servlet.http.*
import kotlin.concurrent.*
import kotlin.coroutines.*

class JettyAsyncServletContainerEngineTest :
    EngineTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = true))

class JettyBlockingServletContainerEngineTest :
    EngineTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = false)) {
    @Ignore
    override fun testUpgrade() {
    }
}

// the factory and engine are only suitable for testing
// you shouldn't use it for production code

private class Servlet(private val async: Boolean) :
    ApplicationEngineFactory<JettyServletApplicationEngine, JettyApplicationEngineBase.Configuration> {
    override fun create(
        environment: ApplicationEngineEnvironment,
        configure: JettyApplicationEngineBase.Configuration.() -> Unit
    ): JettyServletApplicationEngine {
        return JettyServletApplicationEngine(environment, configure, async)
    }
}

@UseExperimental(EngineAPI::class)
private class JettyServletApplicationEngine(
    environment: ApplicationEngineEnvironment,
    configure: JettyApplicationEngineBase.Configuration.() -> Unit,
    async: Boolean
) : JettyApplicationEngineBase(environment, configure) {
    init {
        val servletHandler = ServletContextHandler().apply {
            classLoader = environment.classLoader
            setAttribute(ServletApplicationEngine.ApplicationEngineEnvironmentAttributeKey, environment)

            insertHandler(
                ServletHandler().apply {
                    val h = ServletHolder("ktor-servlet", ServletApplicationEngine::class.java).apply {
                        isAsyncSupported = async
                        registration.setLoadOnStartup(1)
                        registration.setMultipartConfig(MultipartConfigElement(System.getProperty("java.io.tmpdir")))
                        registration.setAsyncSupported(async)
                    }

                    addServlet(h)
                    addServletMapping(ServletMapping().apply {
                        pathSpecs = arrayOf("*.", "/*")
                        servletName = "ktor-servlet"
                    })
                })
        }

        if (async) {
            server.handler = servletHandler
        } else {
            server.handler = JavaSecurityHandler().apply {
                handler = servletHandler
            }
        }
    }
}

private class JavaSecurityHandler : HandlerWrapper() {
    private val securityManager = RestrictThreadCreationSecurityManager(null)

    override fun handle(
        target: String?,
        baseRequest: Request?,
        request: HttpServletRequest?,
        response: HttpServletResponse?
    ) {
        securityManager.enter()
        RestrictedCoroutineThreadLocal.set(true)
        try {
            super.handle(target, baseRequest, request, response)
        } finally {
            RestrictedCoroutineThreadLocal.remove()
            securityManager.leave()
        }
    }
}

private val RestrictedCoroutineThreadLocal = object : ThreadLocal<Boolean>() {
    override fun initialValue() = false
}

private class RestrictThreadCreationSecurityManager(
    val delegate: SecurityManager?
) : SecurityManager(), ThreadContextElement<SecurityManager?> {
    private val lock = ReentrantLock()
    private var refCount = 0

    internal fun enter(): SecurityManager? {
        return lock.withLock {
            val manager = System.getSecurityManager()
            refCount++
            if (refCount == 1) {
                System.setSecurityManager(this)
            }
            manager
        }
    }

    internal fun leave(manager: SecurityManager? = null) {
        lock.withLock {
            if (refCount == 0) throw IllegalStateException("enter/leave balance violation")
            refCount--
            if (refCount == 0) {
                System.setSecurityManager(manager)
            }
        }
    }

    private object Key : CoroutineContext.Key<RestrictThreadCreationSecurityManager>

    override val key: CoroutineContext.Key<*> get() = Key

    override fun restoreThreadContext(context: CoroutineContext, oldState: SecurityManager?) {
        leave(oldState)
        if (oldState !== this) {
            RestrictedCoroutineThreadLocal.set(false)
        }
    }

    override fun updateThreadContext(context: CoroutineContext): SecurityManager? {
        if (!RestrictedCoroutineThreadLocal.get()) {
            val e = SecurityException("A coroutine has migrated to another thread")
            LoggerFactory.getLogger(RestrictThreadCreationSecurityManager::class.java)
                .error("Failed to prepare continuation", e)
            context.cancel(e)
            // throw e - this is not going to work as it breaks coroutine state machinery
        }

        return enter()
    }

    override fun checkPermission(perm: Permission?) {
        if (perm is RuntimePermission && perm.name == "modifyThreadGroup") {
            if (inJavaSecurityHandler()) {
                throw SecurityException("Thread modifications are not allowed")
            }
        }
        if (perm is RuntimePermission && perm.name == "setSecurityManager") {
            if (!isCalledByMe()) {
                throw SecurityException("SecurityManager change is not allowed")
            }
            return
        }

        delegate?.checkPermission(perm)
    }

    private fun inJavaSecurityHandler(): Boolean {
        return JavaSecurityHandler::class.java in classContext || RestrictedCoroutineThreadLocal.get() == true
    }

    private fun isCalledByMe(): Boolean {
        return javaClass in classContext
    }

    private var rootGroup: ThreadGroup? = null

    override fun getThreadGroup(): ThreadGroup? {
        if (rootGroup == null) {
            rootGroup = findRootGroup()
        }
        return rootGroup
    }

    private fun findRootGroup(): ThreadGroup {
        var root = Thread.currentThread().threadGroup
        while (root.parent != null) {
            root = root.parent
        }
        return root
    }
}

