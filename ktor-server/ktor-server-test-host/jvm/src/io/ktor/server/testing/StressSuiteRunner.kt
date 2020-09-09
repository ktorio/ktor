/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import org.junit.runner.*
import org.junit.runner.manipulation.*
import org.junit.runner.notification.*
import org.junit.runners.*

public class StressSuiteRunner(klass: Class<*>) : Runner(), Filterable, Sortable {
    private val delegate = JUnit4(klass)

    override fun run(notifier: RunNotifier?) {
        if (System.getProperty("enable.stress.tests") != null) {
            delegate.run(notifier)
        } else {
            delegate.description.children?.forEach { child ->
                notifier?.fireTestIgnored(child)
            }
        }
    }

    override fun getDescription(): Description = delegate.description

    override fun filter(filter: Filter?) {
        delegate.filter(filter)
    }

    override fun sort(sorter: Sorter?) {
        delegate.sort(sorter)
    }
}
