/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.htmx

/**
 * Constants for HTMX events.
 *
 * @see [Official documentation](https://htmx.org/events/)
 */
public object HxEvents {
    /**
     * Send this event to an element to abort a request.
     */
    public const val Abort: String = "htmx:abort"

    /**
     * Triggered after an AJAX request has completed processing a successful response.
     */
    public const val AfterOnLoad: String = "htmx:afterOnLoad"

    /**
     * Triggered after htmx has initialized a node.
     */
    public const val AfterProcessNode: String = "htmx:afterProcessNode"

    /**
     * Triggered after an AJAX request has completed.
     */
    public const val AfterRequest: String = "htmx:afterRequest"

    /**
     * Triggered after the DOM has settled.
     */
    public const val AfterSettle: String = "htmx:afterSettle"

    /**
     * Triggered after new content has been swapped in.
     */
    public const val AfterSwap: String = "htmx:afterSwap"

    /**
     * Triggered before htmx disables an element or removes it from the DOM.
     */
    public const val BeforeCleanupElement: String = "htmx:beforeCleanupElement"

    /**
     * Triggered before any response processing occurs.
     */
    public const val BeforeOnLoad: String = "htmx:beforeOnLoad"

    /**
     * Triggered before htmx initializes a node.
     */
    public const val BeforeProcessNode: String = "htmx:beforeProcessNode"

    /**
     * Triggered before an AJAX request is made.
     */
    public const val BeforeRequest: String = "htmx:beforeRequest"

    /**
     * Triggered before a swap is done, allows you to configure the swap.
     */
    public const val BeforeSwap: String = "htmx:beforeSwap"

    /**
     * Triggered just before an AJAX request is sent.
     */
    public const val BeforeSend: String = "htmx:beforeSend"

    /**
     * Triggered before the request, allows you to customize parameters, headers.
     */
    public const val ConfigRequest: String = "htmx:configRequest"

    /**
     * Triggered after a trigger occurs on an element, allows you to cancel (or delay) issuing the AJAX request.
     */
    public const val Confirm: String = "htmx:confirm"

    /**
     * Triggered on an error during cache writing.
     */
    public const val HistoryCacheError: String = "htmx:historyCacheError"

    /**
     * Triggered on a cache miss in the history subsystem.
     */
    public const val HistoryCacheMiss: String = "htmx:historyCacheMiss"

    /**
     * Triggered on an unsuccessful remote retrieval.
     */
    public const val HistoryCacheMissError: String = "htmx:historyCacheMissError"

    /**
     * Triggered on a successful remote retrieval.
     */
    public const val HistoryCacheMissLoad: String = "htmx:historyCacheMissLoad"

    /**
     * Triggered when htmx handles a history restoration action.
     */
    public const val HistoryRestore: String = "htmx:historyRestore"

    /**
     * Triggered before content is saved to the history cache.
     */
    public const val BeforeHistorySave: String = "htmx:beforeHistorySave"

    /**
     * Triggered when new content is added to the DOM.
     */
    public const val Load: String = "htmx:load"

    /**
     * Triggered when an element refers to a SSE event in its trigger, but no parent SSE source has been defined.
     */
    public const val NoSseSourceError: String = "htmx:noSSESourceError"

    /**
     * Triggered when an exception occurs during the onLoad handling in htmx.
     */
    public const val OnLoadError: String = "htmx:onLoadError"

    /**
     * Triggered after an out of band element has been swapped in.
     */
    public const val OobAfterSwap: String = "htmx:oobAfterSwap"

    /**
     * Triggered before an out of band element swap is done, allows you to configure the swap.
     */
    public const val OobBeforeSwap: String = "htmx:oobBeforeSwap"

    /**
     * Triggered when an out of band element does not have a matching ID in the current DOM.
     */
    public const val OobErrorNoTarget: String = "htmx:oobErrorNoTarget"

    /**
     * Triggered after a prompt is shown.
     */
    public const val Prompt: String = "htmx:prompt"

    /**
     * Triggered after a URL is pushed into history.
     */
    public const val PushedIntoHistory: String = "htmx:pushedIntoHistory"

    /**
     * Triggered when an HTTP response error (non-200 or 300 response code) occurs.
     */
    public const val ResponseError: String = "htmx:responseError"

    /**
     * Triggered when a network error prevents an HTTP request from happening.
     */
    public const val SendError: String = "htmx:sendError"

    /**
     * Triggered when an error occurs with a SSE source.
     */
    public const val SseError: String = "htmx:sseError"

    /**
     * Triggered when a SSE source is opened.
     */
    public const val SseOpen: String = "htmx:sseOpen"

    /**
     * Triggered when an error occurs during the swap phase.
     */
    public const val SwapError: String = "htmx:swapError"

    /**
     * Triggered when an invalid target is specified.
     */
    public const val TargetError: String = "htmx:targetError"

    /**
     * Triggered when a request timeout occurs.
     */
    public const val Timeout: String = "htmx:timeout"

    /**
     * Triggered before an element is validated.
     */
    public const val ValidationValidate: String = "htmx:validation:validate"

    /**
     * Triggered when an element fails validation.
     */
    public const val ValidationFailed: String = "htmx:validation:failed"

    /**
     * Triggered when a request is halted due to validation errors.
     */
    public const val ValidationHalted: String = "htmx:validation:halted"

    /**
     * Triggered when an AJAX request aborts.
     */
    public const val XhrAbort: String = "htmx:xhr:abort"

    /**
     * Triggered when an AJAX request ends.
     */
    public const val XhrLoadend: String = "htmx:xhr:loadend"

    /**
     * Triggered when an AJAX request starts.
     */
    public const val XhrLoadstart: String = "htmx:xhr:loadstart"

    /**
     * Triggered periodically during an AJAX request that supports progress events.
     */
    public const val XhrProgress: String = "htmx:xhr:progress"
}
