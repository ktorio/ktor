package io.ktor.http

import io.ktor.http.parsing.*
import io.ktor.http.parsing.regex.*

typealias URLParts = ParseResult

/**
 * Extract [URLParts] from [String]
 */
fun String.urlParts(): URLParts = URL_PARSER.parse(this) ?: error("Invalid url format: $this")

/**
 * According to https://tools.ietf.org/html/rfc1738
 */
private val safe = anyOf("$-_.+")
private val extra = anyOf("!*'(),")
private val escape = "%" then hex then hex

private val unreserved = alphaDigit or safe or extra
private val urlChar = unreserved or escape
private val protocolChar = lowAlpha or digit or anyOf("+-.")

private val protocol = atLeastOne(protocolChar).named("protocol")
private val domainLabel = alphaDigit or (alphaDigit then many(alphaDigit or "-") then alphaDigit)
private val topLabel = alpha or (alpha then many(alphaDigit or "-") then alphaDigit)
private val hostName = many(domainLabel then ".") then topLabel

private val hostNumber = digits then "." then digits then "." then digits then "." then digits
private val credentialChar = urlChar or anyOf(";?&=")
private val user = atLeastOne(credentialChar).named("user")
private val password = atLeastOne(credentialChar).named("password")
private val auth = user then maybe(":" then password) then "@"
private val host = (hostName or hostNumber).named("host")
private val port = ":" then digits.named("port")
private val pathSegment = many(urlChar or anyOf(";&="))
private val parameters = pathSegment.named("parameters")
private val encodedPath = atLeastOne("/" then pathSegment).named("encodedPath")
private val fragment = ("#" then maybe(pathSegment).named("fragment"))

private val URL_PARSER = grammar {
    +maybe(protocol then "://")
    +maybe(auth)
    +maybe(host then maybe(port))
    +maybe(encodedPath then maybe("?" then parameters) then maybe(fragment))
}.buildRegexParser()
