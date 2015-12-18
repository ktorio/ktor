package org.jetbrains.ktor.application

interface ApplicationConfig {
    val classLoader: ClassLoader
    val environment: String
    val port: Int
    val async: Boolean
    val log: ApplicationLog

    fun getString(configuration: String): String
    fun getStringListOrEmpty(configuration: String): List<String>
}

fun applicationConfig(builder: ApplicationConfigBuilder.() -> Unit): ApplicationConfig = ApplicationConfigBuilder().apply(builder)

class ApplicationConfigBuilder : ApplicationConfig {
    override fun getString(configuration: String): String = throw UnsupportedOperationException()
    override fun getStringListOrEmpty(configuration: String): List<String> = throw UnsupportedOperationException()

    public override var classLoader: ClassLoader = ApplicationConfigBuilder::class.java.classLoader
    override var log: ApplicationLog = SLF4JApplicationLog("embedded")
    override var environment: String = "development"
    override var port: Int = 80
    override var async: Boolean = false
}