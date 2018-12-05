package io.ktor.network.selector

import io.ktor.util.*
import java.io.*
import java.nio.channels.*
import java.nio.channels.spi.*

/**
 * Selector manager is a service that manages NIO selectors and selection threads
 */
interface SelectorManager {
    /**
     * NIO selector provider
     */
    @KtorExperimentalAPI
    val provider: SelectorProvider

    /**
     * Notifies the selector that selectable has been closed.
     */
    fun notifyClosed(s: Selectable)

    /**
     * Suspends until [interest] is selected for [selectable]
     * May cause manager to allocate and run selector instance if not yet created.
     *
     * Only one selection is allowed per [interest] per [selectable] but you can
     * select for different interests for the same selectable simultaneously.
     * In other words you can select for read and write at the same time but should never
     * try to read twice for the same selectable.
     */
    suspend fun select(selectable: Selectable, interest: SelectInterest)

    companion object
}

/**
 * Select interest kind
 * @property flag to be set in NIO selector
 */
@Suppress("KDocMissingDocumentation")
@KtorExperimentalAPI
enum class SelectInterest(val flag: Int) {
    READ(SelectionKey.OP_READ),
    WRITE(SelectionKey.OP_WRITE),
    ACCEPT(SelectionKey.OP_ACCEPT),
    CONNECT(SelectionKey.OP_CONNECT);

    companion object {
        @InternalAPI
        val AllInterests: Array<SelectInterest> = values()

        /**
         * interest's flags in enum entry order
         */
        @InternalAPI
        val flags: IntArray = values().map { it.flag }.toIntArray()

        @InternalAPI
        val size: Int = values().size
    }
}

/**
 * Creates a NIO entity via [create] and calls [setup] on it. If any exception happens then the entity will be closed
 * and an exception will be propagated.
 */
inline fun <C : Closeable, R> SelectorManager.buildOrClose(create: SelectorProvider.() -> C, setup: C.() -> R): R {
    while (true) {
        val result = create(provider)

        try {
            return setup(result)
        } catch (t: Throwable) {
            result.close()
            throw t
        }
    }
}
