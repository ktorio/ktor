/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import org.junit.runner.*
import org.junit.runner.notification.*
import org.junit.runners.*

class StressSuiteRunner(klass: Class<*>) : Runner() {
    private val delegate = JUnit4(klass)

    override fun run(notifier: RunNotifier?) {
        if (System.getProperty("enable.stress.tests") != null) {
            delegate.run(notifier)
        }
    }

    override fun getDescription(): Description = delegate.description
}
