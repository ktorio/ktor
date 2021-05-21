/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application.plugins.api

/**
 * Reports that [pluginName] is not installed into Ktor server but is required by some other plugin
 */
public class PluginNotInstalledException(private val pluginName: String) : Exception() {
    override val message: String
        get() = "Plugin $pluginName is not installed but required"
}

/**
 * Reports that current [subject] in HTTP pipeline is not of an expected binary data type
 */
public open class NoBinaryDataException(private val expectedTypeName: String, private val subject: Any?) : Exception() {
    override val message: String
        get() = "Expected $expectedTypeName type but ${subject?.javaClass?.name} found"
}

/**
 * Reports that current [subject] in HTTP pipeline is not of a type of OutgoingContent
 */
public class NoOutgoingContentException(private val subject: Any?) : NoBinaryDataException("OutgoingContent", subject)

/**
 * Reports that current [subject] in HTTP pipeline is not of a type of OngoingContent
 */
public class NoByteReadChannelException(private val subject: Any?) : NoBinaryDataException("ByteReadChannel", subject)
