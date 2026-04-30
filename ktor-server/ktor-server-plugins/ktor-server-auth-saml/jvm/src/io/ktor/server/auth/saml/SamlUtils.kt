/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.saml

import org.opensaml.core.xml.XMLObject
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport
import org.opensaml.core.xml.io.UnmarshallingException
import org.w3c.dom.Element

/**
 * Unmarshalls a DOM Element to a SAML XMLObject of the specified type.
 */
internal inline fun <reified T : XMLObject> Element.unmarshall(): T {
    val unmarshallerFactory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory()
    val unmarshaller = unmarshallerFactory.getUnmarshaller(this)
        ?: throw UnmarshallingException("No unmarshaller found for element: ${this.localName}")
    return unmarshaller.unmarshall(this) as T
}
