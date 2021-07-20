/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.pool.*
import kotlin.test.*

class SingleInstancePoolTest {

    @Test
    fun testFreezeBeforeProduce() {
        val pool = object : SingleInstancePool<Int>() {
            override fun produceInstance(): Int = 42

            override fun disposeInstance(instance: Int) {}
        }

        pool.makeShared()
        pool.borrow()
    }
}
