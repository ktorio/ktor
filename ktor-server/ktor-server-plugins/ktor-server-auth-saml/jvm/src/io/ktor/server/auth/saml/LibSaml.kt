/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import net.shibboleth.shared.xml.*
import net.shibboleth.shared.xml.impl.*
import org.opensaml.core.config.*
import org.slf4j.*
import java.util.concurrent.locks.*
import javax.xml.XMLConstants
import kotlin.concurrent.*

/**
 * Thread-safe singleton initializer for OpenSAML library.
 *
 * This object handles the one-time initialization of the OpenSAML library, which includes:
 * - Registering XML object providers (builders, marshallers, unmarshallers)
 * - Loading XML security algorithms
 * - Configuring the secure XML parser pool with XXE protection
 *
 * The initialization is thread-safe and happens exactly once, even in multithreaded environments.
 */
internal object LibSaml {
    private val logger: Logger = LoggerFactory.getLogger(LibSaml::class.java)
    private val lock = ReentrantLock()

    @Volatile
    private var initialized = false

    /**
     * The secure XML parser pool used for parsing SAML messages.
     * Configured with XXE protection features.
     */
    internal lateinit var parserPool: ParserPool
        private set

    /**
     * Ensures OpenSAML is initialized exactly once.
     * This method is thread-safe and idempotent.
     */
    fun ensureInitialized() {
        if (initialized) {
            return
        }
        lock.withLock {
            if (initialized) return
            try {
                logger.info("Initializing OpenSAML library...")

                // Initialize OpenSAML library (loads XML object providers and security algorithms)
                InitializationService.initialize()

                parserPool = createSecureParserPool()
                initialized = true

                logger.info("OpenSAML library initialized successfully")
            } catch (e: Exception) {
                logger.error("Failed to initialize OpenSAML library", e)
                throw InitializationException("OpenSAML initialization failed", e)
            }
        }
    }

    /**
     * Creates a secure BasicParserPool with XXE protection.
     *
     * The parser is configured to prevent XXE attacks by:
     * - Disallowing DOCTYPE declarations
     * - Disabling external entity processing
     * - Disabling DTD loading
     */
    private fun createSecureParserPool(): BasicParserPool = BasicParserPool().apply {
        maxPoolSize = 50
        isCoalescing = true
        isIgnoreComments = true
        isNamespaceAware = true
        isIgnoreElementContentWhitespace = true

        // XXE protection features
        setBuilderFeatures(
            mapOf(
                // Disallow DOCTYPE declarations to prevent XXE attacks
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                // Disable external general entities
                "http://xml.org/sax/features/external-general-entities" to false,
                // Disable external parameter entities
                "http://xml.org/sax/features/external-parameter-entities" to false,
                // Disable loading external DTDs
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
                // Enable secure processing mode (limits entity expansion)
                XMLConstants.FEATURE_SECURE_PROCESSING to true
            )
        )

        // Additional security attributes to prevent external access
        // Prevent access to external DTDs and schemas
        setBuilderAttributes(
            mapOf(
                XMLConstants.ACCESS_EXTERNAL_DTD to "",
                XMLConstants.ACCESS_EXTERNAL_SCHEMA to ""
            )
        )

        initialize()
    }

    /**
     * Exception thrown when OpenSAML initialization fails.
     */
    class InitializationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
}
