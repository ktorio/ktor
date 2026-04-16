/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.io

import io.ktor.utils.io.*

private const val VM_NAME_PROPERTY = "java.vm.name"
private const val IO_POOL_SIZE_PROPERTY = "kotlinx.io.pool.size.bytes"
private const val DEFAULT_POOL_SIZE_BYTES = "2097152"
private const val ANDROID_VM_NAME = "Dalvik"
private const val MIN_PROCESS_MEMORY = 10_000_000

/**
 * When using Android, we must assign "kotlinx.io.pool.size.bytes" so that kotlinx-io's segment pooling
 * cache is enabled.  Otherwise, kotlinx-io segments will not be reused and impact performance.
 *
 * The default for non-Android VMs is 4MB.
 *
 * @link https://developer.android.com/reference/java/lang/System#getProperties()
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.io.configurePlatform)
 */
@InternalAPI
public actual fun configurePlatform() {
    val needsSegmentPoolConfig =
        System.getProperty(IO_POOL_SIZE_PROPERTY) == null &&
            System.getProperty(VM_NAME_PROPERTY) == ANDROID_VM_NAME &&
            Runtime.getRuntime().maxMemory() > MIN_PROCESS_MEMORY
    if (needsSegmentPoolConfig) {
        System.setProperty(IO_POOL_SIZE_PROPERTY, DEFAULT_POOL_SIZE_BYTES)
    }
}
