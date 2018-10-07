package io.ktor.client.response

import kotlinx.io.charsets.Charset

open class HttpResponseConfig {
    /**
     * Default [Charset] for response content if not specified, the initial value is ISO_8859_1.
     */
    var defaultCharset: Charset = Charset.forName("ISO_8859_1")
}
