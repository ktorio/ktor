package io.ktor.jrpc.model

/**
 * Class that must be used to interrupt normal code execution in JRPC-method like unavailable resources, etc.
 */
class JrpcException : Exception {
    /**
     * The error code
     */
    var code: Int = JrpcError.CODE_INTERNAL_ERROR
        private set

    /**
     * Constructor with only error message
     * The code [JrpcError.CODE_INTERNAL_ERROR] will be set as [code]
     * @param message the error message
     */
    constructor(message: String) : super(message)

    /**
     * Constructor with error message and Throwable
     * The code [JrpcError.CODE_INTERNAL_ERROR] will be set as [code]
     * @param message the error message
     * @param cause the error [Throwable]
     */
    constructor(message: String, cause: Throwable) : super(message, cause)

    /**
     * Constructor with error message and code
     * @param message the error message
     * @param code the error code
     */
    constructor(message: String, code: Int) : super(message) {
        this.code = code
    }

    /**
     * Constructor with error message, code and cause
     * @param message the error message
     * @param cause the error [Throwable]
     * @param code the error code
     */
    constructor(message: String, cause: Throwable, code: Int) : this(message, cause) {
        this.code = code
    }
}