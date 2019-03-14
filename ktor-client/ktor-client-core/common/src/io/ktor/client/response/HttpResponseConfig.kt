package io.ktor.client.response

import kotlinx.io.charsets.*

open class HttpResponseConfig {
    /**
     * Default [Charset] for response content if not specified, the initial value is ISO_8859_1.
     * If ISO_8859_1 is not available, UTF-8 is used as a fallback.
     */

    @Deprecated(
        "Use [Charsets { responseFallbackCharset }] in [HttpClientConfig] instead.",
        replaceWith = ReplaceWith("Config { responseFallbackCharset = TODO() }"),
        level = DeprecationLevel.ERROR
    )
    var defaultCharset: Charset
        get() = error("defaultCharset is deprecated")
        set(value) = error("defaultCharsetIsDeprecated")
}
