/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.velocity

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import org.apache.velocity.app.*
import org.apache.velocity.tools.*
import org.apache.velocity.tools.config.*

private val ENGINE_CONFIG_KEY = "ENGINE_CONFIG"

public fun EasyFactoryConfiguration.engine(configure: VelocityEngine.() -> Unit) {
    data(ENGINE_CONFIG_KEY, configure)
}

/**
 * VelocityTools ktor feature. Populates model with standard Velocity tools.
 */
public class VelocityTools private constructor(private val toolManager: ToolManager) {

    /**
     * A companion object for installing feature
     */
    public companion object Feature :
        ApplicationFeature<ApplicationCallPipeline, EasyFactoryConfiguration, VelocityTools> {

        override val key: AttributeKey<VelocityTools> = AttributeKey<VelocityTools>("velocityTools")

        override fun install(
            pipeline: ApplicationCallPipeline,
            config: EasyFactoryConfiguration.() -> Unit
        ): VelocityTools {
            val factoryConfig = EasyFactoryConfiguration().apply(config)
            val engineConfig = factoryConfig.getData(ENGINE_CONFIG_KEY)
                ?.also { factoryConfig.removeData(it) }
                ?.value as (VelocityEngine.() -> Unit)? ?: {}
            val engine = VelocityEngine().apply(engineConfig)
            val toolManager = ToolManager().apply {
                configure(factoryConfig)
                velocityEngine = engine
            }
            val feature = VelocityTools(toolManager)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is VelocityContent) {
                    val response = feature.process(value)
                    proceedWith(response)
                }
            }
            return feature
        }
    }

    internal fun process(content: VelocityContent): OutgoingContent {
        return velocityOutgoingContent(
            toolManager.velocityEngine.getTemplate(content.template),
            toolManager.createContext().also { it.putAll(content.model) },
            content.etag,
            content.contentType
        )
    }
}
