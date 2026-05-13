package io.ktor.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

class KtorCommandLineProcessor : CommandLineProcessor {
    companion object {
        private const val ENABLED = "openApiEnabled"
        private const val CODE_INFERENCE = "openApiCodeInference"
        private const val ONLY_COMMENTED = "openApiOnlyCommented"
        private const val DEBUG = "openApiDebug"
        private const val LOG_DIR = "openApiLogDir"

        const val PLUGIN_ID = "io.ktor.ktor-compiler-plugin"

        val OPENAPI_ENABLED_KEY = CompilerConfigurationKey<String>(ENABLED)
        val OPENAPI_DEBUG_KEY = CompilerConfigurationKey<String>(DEBUG)
        val OPENAPI_CODE_INFERENCE_KEY = CompilerConfigurationKey<String>(CODE_INFERENCE)
        val OPENAPI_ONLY_COMMENTED_KEY = CompilerConfigurationKey<String>(ONLY_COMMENTED)
        val OPENAPI_LOG_DIR = CompilerConfigurationKey<String>(LOG_DIR)

        val OPENAPI_ENABLED_OPTION = CliOption(
            ENABLED,
            "<boolean>",
            "Enables the OpenAPI generation",
            required = false
        )

        val OPENAPI_DEBUG_OPTION = CliOption(
            DEBUG,
            "<boolean>",
            "Enables logging",
            required = false
        )

        val OPENAPI_CODE_INFERENCE_OPTION = CliOption(
            CODE_INFERENCE,
            "<boolean>",
            "Enables code inference for OpenAPI (experimental)",
            required = false
        )

        val OPENAPI_ONLY_COMMENTED_OPTION = CliOption(
            ONLY_COMMENTED,
            "<boolean>",
            "Only process routing calls that have a preceding comment (KDoc or line comment)",
            required = false
        )

        val OPENAPI_LOG_DIR_OPTION = CliOption(
            LOG_DIR,
            "<string>",
            "Where the debug log file will appear; required for logging",
            required = false
        )
    }

    override val pluginId: String get() = PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> get() = listOf(
        OPENAPI_ENABLED_OPTION,
        OPENAPI_DEBUG_OPTION,
        OPENAPI_CODE_INFERENCE_OPTION,
        OPENAPI_ONLY_COMMENTED_OPTION,
        OPENAPI_LOG_DIR_OPTION,
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option) {
            OPENAPI_ENABLED_OPTION -> configuration.put(OPENAPI_ENABLED_KEY, value)
            OPENAPI_DEBUG_OPTION -> configuration.put(OPENAPI_DEBUG_KEY, value)
            OPENAPI_CODE_INFERENCE_OPTION -> configuration.put(OPENAPI_CODE_INFERENCE_KEY, value)
            OPENAPI_ONLY_COMMENTED_OPTION -> configuration.put(OPENAPI_ONLY_COMMENTED_KEY, value)
            OPENAPI_LOG_DIR_OPTION -> configuration.put(OPENAPI_LOG_DIR, value)
            else -> error("Unexpected option: ${option.optionName}")
        }
    }
}