/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Time given to `runTest` to report its own timeout before the watchdog fails the test. */
private val WATCHDOG_GRACE_PERIOD = 10.seconds

/** Returns the watchdog delay, or `null` if it doesn't fit into `setTimeout` (a signed 32-bit integer). */
internal fun watchdogDelayMillis(timeout: Duration): Int? {
    val delay = (timeout + WATCHDOG_GRACE_PERIOD).inWholeMilliseconds
    return if (delay > Int.MAX_VALUE) null else delay.toInt()
}

internal fun watchdogMessage(timeout: Duration): String =
    "Test didn't complete within $timeout + $WATCHDOG_GRACE_PERIOD. " +
        "runTest couldn't enforce its timeout, so something blocks the test machinery."

/**
 * Races `promise` against a `setTimeout` timer.
 * When the timer fires, prints Node.js active handles (if available) and rejects with an Error.
 * Expects `promise`, `delayMillis` and `message` in scope.
 */
internal const val WATCHDOG_SCRIPT = """
new Promise(function (resolve, reject) {
    var timer = setTimeout(function () {
        var details = '';
        if (typeof process !== 'undefined' && typeof process._getActiveHandles === 'function') {
            details = '\nActive handles:\n' + process._getActiveHandles().map(function (handle) {
                var name = handle && handle.constructor ? handle.constructor.name : typeof handle;
                if (handle && handle.remoteAddress !== undefined) {
                    name += ' ' + handle.localAddress + ':' + handle.localPort +
                        ' -> ' + handle.remoteAddress + ':' + handle.remotePort;
                } else if (handle && handle.listening === true) {
                    try { name += ' listening on ' + JSON.stringify(handle.address()); } catch (e) {}
                }
                return '  ' + name;
            }).join('\n');
        }
        var error = new Error(message + details);
        console.error(error.message);
        reject(error);
    }, delayMillis);
    promise.then(
        function (value) { clearTimeout(timer); resolve(value); },
        function (error) { clearTimeout(timer); reject(error); }
    );
})
"""
