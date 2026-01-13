/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.io

import io.ktor.utils.io.InternalAPI

private const val IO_POOL_SIZE_PROPERTY = "kotlinx.io.pool.size.bytes"
private const val DEFAULT_POOL_SIZE_BYTES = "2097152"
private const val ANDROID_VM_NAME = "Dalvik"

/**
 * When using Android, we must assign "kotlinx.io.pool.size.bytes" so that kotlinx-io's segment pooling
 * cache is enabled.  Otherwise, kotlinx-io segments will not be reused and impact performance.
 *
 * The default for non-Android VMs is 4MB.
 *
 * @link https://developer.android.com/reference/java/lang/System#getProperties()
 */
@InternalAPI
public actual fun configurePlatform() {
    if (System.getProperty("java.vm.name") == ANDROID_VM_NAME && System.getProperty(IO_POOL_SIZE_PROPERTY) == null) {
        System.setProperty(IO_POOL_SIZE_PROPERTY, DEFAULT_POOL_SIZE_BYTES)
    }
}
