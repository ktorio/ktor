package io.ktor.jrpc.model


/**
 * JrpcError class according to specification
 * https://www.jsonrpc.org/specification#error_object
 */
class JrpcError(
        /**
         * JRPC error message
         */
        val message: String,
        /**
         * JRPC error code
         */
        val code: Int = CODE_INTERNAL_ERROR
) {
    /**
     * Companion object containing error codes and generic error objects
     */
    companion object {
        /**
         * Invalid JSON was received by the server.
         * An error occurred on the server while parsing the JSON text.
         * https://www.jsonrpc.org/specification#error_object
         */
        const val CODE_PARSE_ERROR = -32700
        /**
         * The JSON sent is not a valid Request object.
         * https://www.jsonrpc.org/specification#error_object
         */
        const val CODE_INVALID_REQUEST = -32600
        /**
         * The method does not exist / is not available.
         * https://www.jsonrpc.org/specification#error_object
         */
        const val CODE_METHOD_NOT_FOUND = -32601
        /**
         * Invalid method parameter(s).
         * https://www.jsonrpc.org/specification#error_object
         */
        const val CODE_INVALID_PARAMS = -32602
        /**
         * Internal JSON-RPC error.
         * https://www.jsonrpc.org/specification#error_object
         */
        const val CODE_INTERNAL_ERROR = -32603
        /**
         * Reserved for implementation-defined server-errors
         * https://www.jsonrpc.org/specification#error_object
         */
        const val CODE_SERVER_ERROR = -32000 //to -32099, Reserved for implementation-defined server-errors.

        //The remainder of the space is available for application defined errors.
        private const val MESSAGE_NULL_PARAMS_NOT_UNIT = "Params is null while handler func is not Unit"

        /**
         * Generic JrpcError for code CODE_PARSE_ERROR
         */
        val PARSE_ERROR = JrpcError("Parse error", CODE_PARSE_ERROR)
        /**
         * Generic JrpcError for code CODE_INVALID_REQUEST
         */
        val INVALID_REQUEST = JrpcError("Invalid Request", CODE_INVALID_REQUEST)
        /**
         * Generic JrpcError  for code CODE_METHOD_NOT_FOUND
         */
        val METHOD_NOT_FOUND = JrpcError("Method not found", CODE_METHOD_NOT_FOUND)
        /**
         * Generic JrpcError for code CODE_INVALID_PARAMS
         */
        val INVALID_PARAMS = JrpcError("Invalid params", CODE_INVALID_PARAMS)
        /**
         * Generic JrpcError for code CODE_INVALID_PARAMS_NOT_UNIT
         * This error does not correspond any declared code in https://www.jsonrpc.org/specification#error_object
         * This is only Kotlin-specific error.
         * Thrown only when JRPC method type param is [Unit] but method receive not empty params
         */
        val INVALID_PARAMS_NOT_UNIT = JrpcError(MESSAGE_NULL_PARAMS_NOT_UNIT, CODE_INVALID_PARAMS)
        /**
         * Generic JrpcError for code CODE_INTERNAL_ERROR
         */
        val INTERNAL_ERROR = JrpcError("Internal error", CODE_INTERNAL_ERROR)
        /**
         * Generic JrpcError for code CODE_SERVER_ERROR
         */
        val SERVER_ERROR = JrpcError("Server error", CODE_SERVER_ERROR)
    }
}
