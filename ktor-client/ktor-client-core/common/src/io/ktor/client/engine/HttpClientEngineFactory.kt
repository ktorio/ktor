/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

/**
 * A factory for creating instances of [HttpClientEngine] with a specific configuration type [T].
 *
 * This interface defines how to produce custom HTTP client engines that implement [HttpClientEngine].
 * Each engine is initialized with a configuration object of type [T], which extends [HttpClientEngineConfig].
 *
 * Factories implementing this interface are commonly passed to the [io.ktor.client.HttpClient] constructor to specify the
 * underlying engine that will handle HTTP requests. This allows users to seamlessly plug in different
 * engines based on their requirements, such as for performance, platform compatibility, or protocol support.
 *
 * @param T The type of [HttpClientEngineConfig] used to configure the engine.
 *
 * Example:
 * ```kotlin
 * object MyCustomEngine: HttpClientEngineFactory<MyEngineConfig> {
 *    // ...
 * }
 *
 * val client = HttpClient(MyCustomEngine) {
 *     engine {
 *         timeout = 10_000
 *         customSetting = "example"
 *     }
 * }
 * ```
 */
public interface HttpClientEngineFactory<out T : HttpClientEngineConfig> {
    /**
     * Creates or retrieves an instance of [HttpClientEngine], applying optional configurations.
     *
     * This method is responsible for deciding whether to create a new engine instance or reuse
     * an existing one, based on the factory's internal logic and the configuration provided in
     * the [block]. This allows for efficient resource management by avoiding unnecessary engine
     * instantiation when possible.
     *
     * The [block] parameter enables users to customize the engine's [T] configuration during creation.
     * If no block is provided, the factory should apply default configurations or reuse an engine
     * with the default settings.
     *
     * Typically, this method is invoked internally by the [io.ktor.client.HttpClient] constructor when the factory
     * is passed to it. Users can, however, call it directly to explicitly control engine instantiation.
     *
     * @param block A lambda that applies additional configurations to the engine's [T] object.
     * @return An [HttpClientEngine] instance, which may be newly created or reused.
     *
     * Example with explicit engine creation:
     * ```kotlin
     * val engine = MyCustomEngineFactory.create {
     *     timeout = 5_000
     *     customHeader = "example"
     * }
     * ```
     *
     * Example when used with `HttpClient`:
     * ```kotlin
     * val client = HttpClient(MyCustomEngineFactory) {
     *     engine {
     *         timeout = 5_000
     *         customHeader = "example"
     *     }
     * }
     * ```
     *
     * **Contracts for Implementors**:
     * - If reusing an existing engine, ensure its configuration matches the one provided in [block].
     * - Ensure thread safety when managing a shared engine instance across multiple [create] calls.
     * - Provide meaningful default configurations when no block is provided.
     * - Properly release resources of reused engines when the [io.ktor.client.HttpClient]] or engine is closed.
     */
    public fun create(block: T.() -> Unit = {}): HttpClientEngine
}
