package io.ktor.jrpc

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.util.AttributeKey
import java.text.DateFormat

/**
 * Activating support for JSON-RPC handling like:
 *    routing {
 *
 *        get("/") { rootHandler() }
 *
 *        jrpc("/json-rpc") {
 *
 *            method<Unit>("ping") {
 *                "pong"
 *            }
 *
 *            method<User>("hello") { user ->
 *                val token = call.request.headers["Session-Token"]
 *                "Hello, ${user.name}${if (token == null) " you must authorize" else " your token is $token"}"
 *            }
 *        }
 *    }
 *
 *
 * @param configuration the feature configuration-block
 *
 *    install(JrpcSupport) {
 *        gson {
 *            setDateFormat(DateFormat.LONG)
 *        }
 *        printStackTraces = true
 *        useInternalSerialization = true
 *    }
 *
 * Can be used with
 *    install(ContentNegotiation) {
 *        gson {
 *            setDateFormat(DateFormat.LONG)
 *        }
 *    }
 *
 * (in this case you MUST set useInternalSerialization = false)
 *
 * or without (in this case the default behavior is useInternalSerialization = false, will use gson for converting content)
 *
 * GSON still can set null to non-nullable fields, so you need to use TypeAdapters
 *
 * Batch currently is not supported
 */
class JrpcSupport(configuration: Configuration) {

    init {
        gson = configuration.gson
        printStackTraces = configuration.printStackTraces
        useInternalSerialization = configuration.useInternalSerialization
    }

    /**
     * Configuration for feature JrpcSupport
     * custom Gson may be set to decode JRPC params
     * function [gson] can be used to pass GsonBuilder configuration
     * or you may set already configured gson by
     * gson = myGson //usable if you pass configured gson in separate modules
     * [printStackTraces] may be set to show stack traces in JrpcResponse.error.message . Default if false
     *
     *     install(JrpcSupport) {
     *         gson {
     *             setDateFormat(DateFormat.LONG)
     *         }
     *        printStackTraces = true
     *        useInternalSerialization = true
     *     }
     *
     * or
     *
     *     val myGson = Gson()
     *     install(JrpcSupport) {
     *        gson = myGson
     *        printStackTraces = true
     *     }
     *
     * If gson is not passed, the DEFAULT_GSON will used with configuration
     * GsonBuilder().apply { setDateFormat(DateFormat.LONG) }.create()
     *
     * GSON still can set null to non-nullable fields, so you need to use TypeAdapters
     */
    class Configuration {

        /**
         * When set to true, feature will convert input/output data by itself
         * if set to false, then the feature will use context.call.receiveOrNull<JrpcRequest>()
         * to parse and get JrpcRequest, and context.call.respond(result), offering parsing and serialization to
         * side features (like GSON which use ContentNegotiation feature to convert mime type application/json
         *
         * Simple:
         * if used ContentNegotiation for mime type application/json
         *
         *     install(ContentNegotiation) {
         *         gson {
         *             setDateFormat(DateFormat.LONG)
         *         }
         *     }
         *
         * then
         *
         *     install(JrpcSupport) {
         *         gson {
         *             setDateFormat(DateFormat.LONG)
         *         }
         *         useInternalSerialization = false
         *     }
         *
         * if no any mime type processors for application/json
         *
         *     install(JrpcSupport) {
         *         gson {
         *             setDateFormat(DateFormat.LONG)
         *         }
         *         useInternalSerialization = true
         *     }
         */
        var useInternalSerialization: Boolean = true

        /**
         * GSON used to decode JrpcRequest params
         */
        var gson: Gson = DEFAULT_GSON

        /**
         * Print stack traces in JRPC Error messages if set to true
         * Usable for debug
         */
        var printStackTraces: Boolean = false

        /**
         * Setup GSON for JRPC-Support with configuration block
         */
        fun gson(builderBlock: GsonBuilder.() -> Unit) {
            gson = GsonBuilder().apply(builderBlock).create()
        }

    }

    /**
     * Installable feature for [JrpcSupport].
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, JrpcSupport.Configuration, JrpcSupport> {

        /**
         * Unique key that identifies a feature
         * Our feature is JrpcSupport
         */
        override val key = AttributeKey<JrpcSupport>("JrpcSupport")

        /**
         * Feature installation code
         */
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): JrpcSupport {
            val configuration = Configuration().apply(configure)
            return JrpcSupport(configuration)
        }

        private val DEFAULT_GSON: Gson = GsonBuilder().apply { setDateFormat(DateFormat.LONG) }.create()

        /**
         * GSON for all instances of JrpcRouter by default is
         * GsonBuilder().apply { setDateFormat(DateFormat.LONG) }.create()
         */
        var gson: Gson = DEFAULT_GSON
            private set

        /**
         * Print stack traces in JRPC Error messages for all JrpcRouter instances if set to true
         * Usable for debug
         */
        var printStackTraces: Boolean = false
            private set

        /**
         * When set to true, feature's JrpcRouters will convert input/output data by itself
         * if set to false, then the feature will use context.call.receiveOrNull<JrpcRequest>()
         * to parse and get JrpcRequest, and context.call.respond(result), offering parsing and serialization to
         * side features (like GSON which use ContentNegotiation feature to convert mime type application/json
         *
         * Simple:
         * if used ContentNegotiation for mime type application/json
         *
         *     install(ContentNegotiation) {
         *         gson {
         *             setDateFormat(DateFormat.LONG)
         *         }
         *     }
         *
         * then
         *
         *     install(JrpcSupport) {
         *         gson {
         *             setDateFormat(DateFormat.LONG)
         *         }
         *         useInternalSerialization = false
         *     }
         *
         * if no any mime type processors for application/json
         *
         *     install(JrpcSupport) {
         *         gson {
         *             setDateFormat(DateFormat.LONG)
         *         }
         *         useInternalSerialization = true
         *     }
         *
         * Important: in all cases feature require gson for parsing JRPC params
         */
        var useInternalSerialization: Boolean = true
            private set
    }
}