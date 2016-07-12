package org.jetbrains.ktor.http

@Deprecated("Use localRoute or originRoute instead")
data class HttpRequestLine(val method: HttpMethod,
                                  val uri: String,
                                  val version: String
                                 ) {
    override fun toString(): String {
        return "$version - ${method.value} $uri"
    }
}
