/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import platform.Foundation.*

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal fun trustAnyCertificate(
    challenge: NSURLAuthenticationChallenge,
    completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
) {
    val serverTrust = challenge.protectionSpace.serverTrust
    if (serverTrust != null && challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust) {
        val credential = NSURLCredential.credentialForTrust(serverTrust)
        completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
    } else {
        completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
    }
}
