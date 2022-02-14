/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.callid

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.internal.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import kotlin.random.*

/**
 * A function that retrieves or generates call id using provided call
 */
public typealias CallIdProvider = (call: ApplicationCall) -> String?

/**
 * A function that verifies retrieved or generated call id. Should return `true` for a valid call id.
 * Also it could throw a [RejectedCallIdException] to reject an [ApplicationCall] otherwise an illegal call id
 * will be ignored or replaced with generated one.
 */
public typealias CallIdVerifier = (String) -> Boolean

/**
 * An exception that could be thrown to reject a call due to illegal call id
 * @param illegalCallId that caused rejection
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class RejectedCallIdException(
    public val illegalCallId: String
) : IllegalArgumentException(), CopyableThrowable<RejectedCallIdException> {
    override fun createCopy(): RejectedCallIdException? = RejectedCallIdException(illegalCallId).also {
        it.initCauseBridge(this)
    }
}

/**
 * [CallId] plugin's configuration
 */
@KtorDsl
public class CallIdConfig {
    internal val retrievers = ArrayList<CallIdProvider>()
    internal val generators = ArrayList<CallIdProvider>()
    internal val responseInterceptors = ArrayList<(call: ApplicationCall, CallId: String) -> Unit>()

    internal var verifier: CallIdVerifier = { false }

    init {
        verify(CALL_ID_DEFAULT_DICTIONARY)
    }

    /**
     * [block] will be used to retrieve call id from a call. It should return `null` if no call id found in request
     */
    public fun retrieve(block: CallIdProvider) {
        retrievers.add(block)
    }

    /**
     * [block] function will be applied when there is no call id retrieved. It should generate a string to be used
     * as call id or `null` if it is impossible to generate call id for some reason.
     * Note that it should conform to call id verification otherwise it may be discarded or may lead to
     * complete call rejection
     *
     * @see CallIdVerifier
     * @see verify
     */
    public fun generate(block: CallIdProvider) {
        generators.add(block)
    }

    /**
     * Verify retrieved or generated call ids using the specified [predicate]. Should return `true` for valid
     * call ids, `false` to ignore an illegal retrieved or generated call id
     * or throw an [RejectedCallIdException] to reject an [ApplicationCall].
     * Only one verify condition could be specified.
     * It is not recommended to disable verification (allow all call id values) as it could be abused
     * so that it may become a security risk.
     * By default there is always the default verifier against [CALL_ID_DEFAULT_DICTIONARY]
     * so all illegal call ids will be discarded.
     *
     * @see [CallIdVerifier] for details.
     */
    public fun verify(predicate: CallIdVerifier) {
        verifier = predicate
    }

    /**
     * Verify retrieved or generated call ids against the specified [dictionary].
     * Rejects an [ApplicationCall] if [reject] is `true`
     * otherwise an illegal call id will be simply ignored.
     * Only one verify condition or dictionary could be specified
     */
    public fun verify(dictionary: String, reject: Boolean = false) {
        val dictionarySet = dictionary.toSet()
        verify { callId ->
            if (!verifyCallIdAgainstDictionary(callId, dictionarySet)) {
                if (reject) throw RejectedCallIdException(callId)
                false
            } else {
                true
            }
        }
    }

    /**
     * Replies with retrieved or generated [CallId]. Usually [replyToHeader] could be used instead.
     */
    public fun reply(block: (call: ApplicationCall, CallId: String) -> Unit) {
        responseInterceptors.add(block)
    }

    /**
     * Setup retrieve/reply cycle via HTTP request and response headers [headerName].
     * Identical to [retrieveFromHeader] and [replyToHeader] invocations with the same [headerName]
     */
    public fun header(headerName: String) {
        retrieveFromHeader(headerName)
        replyToHeader(headerName)
    }

    /**
     * Fetch call id from a request header named [headerName] that is treated as optional
     */
    public fun retrieveFromHeader(headerName: String) {
        retrieve { it.request.headers[headerName] }
    }

    /**
     * Replies retrieved or generated CallId using HTTP response header [headerName]
     */
    public fun replyToHeader(headerName: String) {
        reply { call, CallId ->
            call.response.header(headerName, CallId)
        }
    }
}

internal object BeforeSetup : Hook<suspend (ApplicationCall) -> Unit> {
    private val phase: PipelinePhase = PipelinePhase("CallId")
    private val logger by lazy { KtorSimpleLogger(phase.name) }

    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        pipeline.insertPhaseBefore(ApplicationCallPipeline.Setup, phase)

        pipeline.intercept(phase) {
            try {
                handler(call)
            } catch (rejection: RejectedCallIdException) {
                this@BeforeSetup.logger.warn(
                    "Illegal call id retrieved or generated that is rejected by call id verifier:" +
                        " (url-encoded) " +
                        rejection.illegalCallId.encodeURLParameter()
                )
                call.respond(HttpStatusCode.BadRequest)
                finish()
            }
        }
    }
}

internal val CallIdKey: AttributeKey<String> = AttributeKey<String>("ExtractedCallId")

/**
 * Retrieves and generates if necessary a call id. A call id (or correlation id) could be retrieved_ from a call
 * via [CallIdConfig.retrieve] function. Multiple retrieve functions could be configured that will be invoked
 * one by one until one of them return non-null value. If no value has been provided by retrievers then a generator
 * could be applied to generate a new call id. Generators could be provided via [CallIdConfig.generate] function.
 * Similar to retrieve, multiple generators could be configured so they will be invoked one by one.
 * Usually call id is passed via [io.ktor.http.HttpHeaders.XRequestId] so
 * one could use [CallIdConfig.retrieveFromHeader] function to retrieve call id from a header.
 *
 * All retrieved or generated call ids are verified against [CALL_ID_DEFAULT_DICTIONARY] by default. Alternatively
 * a custom dictionary or functional predicate could be provided via [CallIdConfig.verify] that could
 * pass a valid call id, discard an illegal call id
 * or reject completely an [ApplicationCall] with [HttpStatusCode.BadRequest] if an [RejectedCallIdException] is thrown.
 * Please note that this rejection functionality is not compatible with [StatusPages] for now and you cannot
 * configure rejection response message.
 *
 * Once a call id is retrieved or generated, it could be accessed via [ApplicationCall.CallId] otherwise it will be
 * always `null`. Also a call id could be replied with response by registering [CallIdConfig.reply] or
 * [CallIdConfig.replyToHeader] so client will be able to know call id in case when it is generated.
 *
 * Please note that call id plugin is only intended for debugging and troubleshooting purposes to correlate
 * client requests with logs in multitier/microservices architecture. So usually it is not guaranteed that call id
 * is strictly random/unique. This is why you should NEVER rely on it's uniqueness.
 *
 * [CallId] plugin will be installed to [BeforeSetup.phase] into [ApplicationCallPipeline].
 */
public val CallId: RouteScopedPlugin<CallIdConfig, PluginInstance> = createRouteScopedPlugin(
    "CallId",
    ::CallIdConfig
) {
    val providers = (pluginConfig.retrievers + pluginConfig.generators).toTypedArray()
    val repliers = pluginConfig.responseInterceptors.toTypedArray()
    val verifier = pluginConfig.verifier

    on(BeforeSetup) { call ->
        for (provider in providers) {
            val callId = provider(call) ?: continue
            if (!verifier(callId)) continue // could throw a RejectedCallIdException

            call.attributes.put(CallIdKey, callId)

            repliers.forEach { replier ->
                replier(call, callId)
            }
            break
        }
    }
}

/**
 * A call id that is retrieved or generated by [CallId] plugin or `null` (this is possible if there is no
 * call id provided and no generators configured or [CallId] plugin is not installed)
 */
public val ApplicationCall.callId: String? get() = attributes.getOrNull(CallIdKey)

private fun verifyCallIdAgainstDictionary(callId: String, dictionarySet: Set<Char>): Boolean {
    for (element in callId) {
        if (!dictionarySet.contains(element)) {
            return false
        }
    }

    return true
}

/**
 * The default call id's generator dictionary
 */
public const val CALL_ID_DEFAULT_DICTIONARY: String = "abcdefghijklmnopqrstuvwxyz0123456789+/=-"

/**
 * Generates fixed [length] call ids using the specified [dictionary].
 * Please note that this function generates pseudo-random identifiers via regular [java.util.Random]
 * and should not be considered as cryptographically secure.
 * Also note that you should use the same dictionary for [CallIdVerifier] otherwise a generated call id could be
 * discarded or may lead to complete call rejection.
 *
 * @see [CallIdConfig.verify]
 *
 * @param length of call ids to be generated, should be positive
 * @param dictionary to be used to generate ids, shouldn't be empty and it shouldn't contain duplicates
 */
public fun CallIdConfig.generate(length: Int = 64, dictionary: String = CALL_ID_DEFAULT_DICTIONARY) {
    require(length >= 1) { "Call id should be at least one characters length: $length" }
    require(dictionary.length > 1) { "Dictionary should consist of several different characters" }

    val dictionaryCharacters = dictionary.toCharArray().distinct().toCharArray()

    require(dictionaryCharacters.size == dictionary.length) {
        "Dictionary should not contain duplicates, found: ${dictionary.duplicates()}"
    }

    generate { Random.nextString(length, dictionaryCharacters) }
}

private fun String.duplicates() = toCharArray().groupBy { it }.filterValues { it.size > 1 }.keys.sorted()
private fun Random.nextString(length: Int, dictionary: CharArray): String {
    val chars = CharArray(length)
    val dictionarySize = dictionary.size

    for (index in 0 until length) {
        chars[index] = dictionary[nextInt(dictionarySize)]
    }

    return chars.concatToString()
}
