package io.ktor.jrpc.model

import com.google.gson.JsonElement

/**
 * Class corresponding https://www.jsonrpc.org/specification#request_object
 */
data class JrpcRequest(
        /**
         * An identifier established by the Client that MUST contain a String, Number, or NULL value if included.
         * If it is not included it is assumed to be a notification.
         * The value SHOULD normally not be Null and Numbers SHOULD NOT contain fractional parts
         * https://www.jsonrpc.org/specification#request_object
         */
        val id: Long,

        /**
         * A String containing the name of the method to be invoked.
         * Method names that begin with the word rpc followed by a period character (U+002E or ASCII 46)
         * are reserved for rpc-internal methods and extensions and MUST NOT be used for anything else
         * https://www.jsonrpc.org/specification#request_object
         */
        val method: String,
        /**
         * A Structured value that holds the parameter values to be used during the
         * invocation of the method. This member MAY be omitted.
         * https://www.jsonrpc.org/specification#request_object
         */
        val params: JsonElement? = null,

        /**
         * A String specifying the version of the JSON-RPC protocol.
         * MUST be exactly "2.0".
         * BUT the server does not deny request if this param is not set/omitted
         */
        val jsonrpc: String = "2.0"
)