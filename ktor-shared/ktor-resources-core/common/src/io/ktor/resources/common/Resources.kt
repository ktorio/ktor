/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.resources.common

import io.ktor.resources.*
import io.ktor.resources.serialisation.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*

/**
 * Resources feature instance.
 */
public class Resources(configuration: Configuration) {

    /**
     * Instance of format to (de)serialize resources instances
     */
    public val resourcesFormat: ResourcesFormat = ResourcesFormat(configuration.serializersModule)

    /**
     * Configuration for Resources feature instance.
     */
    @OptIn(ExperimentalSerializationApi::class)
    public class Configuration {

        /**
         * [SerializersModule] to use for (de)serialization of Resource instances.
         */
        public var serializersModule: SerializersModule = EmptySerializersModule
    }
}
