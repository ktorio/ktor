package io.ktor.jrpc.model


/**
 * Class corresponds
 * https://www.jsonrpc.org/specification#response_object
 */
data class JrpcResponse(
        /**
         * This member is REQUIRED.
         * It MUST be the same as the value of the id member in the Request Object.
         * If there was an error in detecting the id in the Request object (e.g. Parse error/Invalid Request), it MUST be Null.
         * https://www.jsonrpc.org/specification#response_object
         */
        val id: Long? = null,

        /**
         * This member is REQUIRED on success.
         * This member MUST NOT exist if there was an error invoking the method.
         * The value of this member is determined by the method invoked on the Server.
         * https://www.jsonrpc.org/specification#response_object
         */
        val result: Any? = null,

        /**
         * This member is REQUIRED.
         * It MUST be the same as the value of the id member in the Request Object.
         * If there was an error in detecting the id in the Request object (e.g. Parse error/Invalid Request), it MUST be Null.
         * https://www.jsonrpc.org/specification#response_object
         */
        val jsonrpc: String = "2.0",

        /**
         * This member is REQUIRED on error.
         * This member MUST NOT exist if there was no error triggered during invocation.
         * The value for this member MUST be an Object as defined in section 5.1.
         * https://www.jsonrpc.org/specification#response_object
         */
        val error: JrpcError? = null
)