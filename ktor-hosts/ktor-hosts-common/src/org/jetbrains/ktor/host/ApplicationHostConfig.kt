package org.jetbrains.ktor.host

import java.util.*

interface ApplicationHostConfig {

    /**
     * Connectors that describers where and how server should listen
     */
    val connectors: List<HostConnectorConfig>

    /**
     * Specifies if the host should try to reload application automatically on change
     */
    val autoreload: Boolean

}

inline fun applicationHostConfig(builder: ApplicationHostConfigBuilder.() -> Unit) = ApplicationHostConfigBuilder().apply(builder)

class ApplicationHostConfigBuilder : ApplicationHostConfig {
    override val connectors = ArrayList<HostConnectorConfig>()

    override var autoreload: Boolean = false

    override fun toString(): String {
        return "ApplicationHostConfig($connectors, autoreload=$autoreload)"
    }
}
