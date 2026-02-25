/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import io.ktor.utils.io.charsets.name
import org.opensaml.core.xml.XMLObject
import org.opensaml.core.xml.XMLObjectBuilderFactory
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport
import org.opensaml.core.xml.io.MarshallingException
import org.opensaml.core.xml.io.UnmarshallingException
import org.opensaml.saml.common.SAMLObject
import org.opensaml.saml.common.SAMLObjectBuilder
import org.opensaml.security.credential.Credential
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.net.URLEncoder
import java.security.Signature
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import javax.xml.namespace.QName
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.encoding.Base64
import kotlin.text.Charsets
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Result of building a SAML redirect (AuthnRequest, LogoutRequest, or LogoutResponse).
 *
 * @property messageId The ID of the SAML message
 * @property redirectUrl The complete redirect URL with all parameters
 */
public class SamlRedirectResult(
    public val messageId: String,
    public val redirectUrl: String
)

/**
 * @return Secure random ID with the prefix "_"
 */
@OptIn(ExperimentalUuidApi::class)
internal fun generateSecureSamlId(): String = "_" + Uuid.random()

/**
 * Marshals a SAML XMLObject to an XML string.
 */
internal fun XMLObject.marshalToString(): String {
    val marshallerFactory = XMLObjectProviderRegistrySupport.getMarshallerFactory()
    val marshaller = marshallerFactory.getMarshaller(this)
        ?: throw MarshallingException("No marshaller found for object: $elementQName")

    val element = marshaller.marshall(this)

    val transformerFactory = TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    val stringWriter = StringWriter()
    transformer.transform(DOMSource(element), StreamResult(stringWriter))
    return stringWriter.toString()
}

/**
 * Unmarshalls a DOM Element to a SAML XMLObject of the specified type.
 */
internal inline fun <reified T : XMLObject> Element.unmarshall(): T {
    val unmarshallerFactory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory()
    val unmarshaller = unmarshallerFactory.getUnmarshaller(this)
        ?: throw UnmarshallingException("No unmarshaller found for element: ${this.localName}")
    return unmarshaller.unmarshall(this) as T
}

internal fun String.encodeSamlMessage(deflate: Boolean): String {
    val bytes = toByteArray(Charsets.UTF_8)
    if (!deflate) {
        return Base64.encode(source = bytes)
    }
    val bytesOut = ByteArrayOutputStream()
    val deflater = Deflater(Deflater.DEFLATED, true)
    DeflaterOutputStream(bytesOut, deflater).use { it.write(bytes) }
    return Base64.encode(source = bytesOut.toByteArray())
}

/**
 * Decodes a Base64-encoded SAML message.
 *
 * @param isDeflated Whether the message is deflated (HTTP-Redirect binding: true, HTTP-POST: false)
 * @return Decoded XML string
 */
internal fun String.decodeSamlMessage(isDeflated: Boolean): String {
    val decodedBytes = Base64.decode(source = this)
    if (!isDeflated) {
        return decodedBytes.toString(Charsets.UTF_8)
    }
    val inflater = Inflater(true)
    val inflaterInputStream = InflaterInputStream(decodedBytes.inputStream(), inflater)
    return inflaterInputStream.readBytes().toString(Charsets.UTF_8)
}

@Suppress("UNCHECKED_CAST")
internal inline fun <O : SAMLObject> XMLObjectBuilderFactory.build(
    key: QName,
    crossinline configure: O.() -> Unit
): O {
    val builder = getBuilder(key) as SAMLObjectBuilder<O>
    return builder.buildObject().apply(configure)
}

/**
 * Builds a SAML redirect result by marshaling a request/response object and constructing the redirect URL.
 *
 * @param messageId The ID of the SAML message
 * @param samlObject The SAML object to marshal
 * @param destinationUrl The IdP URL to redirect to
 * @param parameterName The query parameter name ("SAMLRequest" or "SAMLResponse")
 * @param relayState Optional RelayState parameter
 * @param signingCredential Optional credential for signing
 * @param signatureAlgorithm The signature algorithm to use if signing
 */
internal fun buildSamlRedirectResult(
    messageId: String,
    samlObject: XMLObject,
    destinationUrl: String,
    parameterName: String,
    relayState: String?,
    signingCredential: Credential?,
    signatureAlgorithm: SignatureAlgorithm
): SamlRedirectResult {
    val xml = samlObject.marshalToString()
    val encodedMessage = xml.encodeSamlMessage(deflate = true)
    val redirectUrl = buildSamlRedirectUrl(
        destinationUrl = destinationUrl,
        parameterName = parameterName,
        encodedMessage = encodedMessage,
        relayState = relayState,
        signingCredential = signingCredential,
        signatureAlgorithm = signatureAlgorithm
    )
    return SamlRedirectResult(messageId, redirectUrl)
}

private fun buildSamlRedirectUrl(
    destinationUrl: String,
    parameterName: String,
    encodedMessage: String,
    relayState: String?,
    signingCredential: Credential?,
    signatureAlgorithm: SignatureAlgorithm
): String {
    val enc = Charsets.UTF_8.name
    val urlEncodedMessage = URLEncoder.encode(encodedMessage, enc)

    val urlBuilder = StringBuilder(destinationUrl)
    urlBuilder.append(if (destinationUrl.contains("?")) "&" else "?")
    urlBuilder.append(parameterName).append("=").append(urlEncodedMessage)

    if (!relayState.isNullOrBlank()) {
        urlBuilder.append("&RelayState=").append(URLEncoder.encode(relayState, enc))
    }
    if (signingCredential != null) {
        urlBuilder
            .append("&SigAlg=")
            .append(URLEncoder.encode(signatureAlgorithm.uri, enc))

        val queryString = urlBuilder.toString().substringAfter("?")
        val signature = signQueryString(queryString, signingCredential, signatureAlgorithm)
        urlBuilder
            .append("&Signature=")
            .append(URLEncoder.encode(signature, enc))
    }

    return urlBuilder.toString()
}

@Suppress("UNCHECKED_CAST")
internal inline fun <O : XMLObject> XMLObjectBuilderFactory.buildXmlObject(
    key: QName,
    crossinline configure: O.() -> Unit
): O {
    val builder = getBuilder(key) as org.opensaml.core.xml.XMLObjectBuilder<O>
    return builder.buildObject(key).apply(configure)
}

/**
 * Signs a query string for HTTP-Redirect binding.
 *
 * In HTTP-Redirect binding, the signature is computed over the raw query string
 * (NOT the XML itself). The signature is then appended as a separate parameter.
 */
internal fun signQueryString(
    queryString: String,
    credential: Credential,
    signatureAlgorithm: SignatureAlgorithm
): String {
    val privateKey = requireNotNull(credential.privateKey) {
        "Credential must have a private key for signing"
    }

    val signature = Signature.getInstance(signatureAlgorithm.jcaAlgorithm)
    signature.initSign(privateKey)
    signature.update(queryString.toByteArray(Charsets.UTF_8))

    val signatureBytes = signature.sign()
    return Base64.encode(source = signatureBytes)
}

/**
 * Checks if [value] is not null, throwing [SamlValidationException] with a message from [lazyMessage] if it is null.
 */
internal inline fun <T : Any> samlRequire(value: T?, crossinline lazyMessage: () -> String): T {
    return value ?: throw SamlValidationException(lazyMessage())
}

/**
 * Checks if [value] is true, throwing [SamlValidationException] with a message from [lazyMessage] if it is false.
 */
internal inline fun samlAssert(value: Boolean, crossinline lazyMessage: () -> String) {
    if (!value) {
        throw SamlValidationException(lazyMessage())
    }
}
