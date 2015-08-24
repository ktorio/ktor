package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*

/** Established connection with client, encapsulates request and response facilities
 */
public interface ApplicationRequest {
    public val requestLine: HttpRequestLine
    public val parameters: ValuesMap
    public val headers: ValuesMap
    public val body: String
    public val attributes: Attributes
}
