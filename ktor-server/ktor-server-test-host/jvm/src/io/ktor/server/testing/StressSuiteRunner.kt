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