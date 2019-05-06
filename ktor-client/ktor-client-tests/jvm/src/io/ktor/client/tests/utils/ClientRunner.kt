/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.util.*
import org.junit.internal.runners.model.*
import org.junit.runner.*
import org.junit.runner.manipulation.*
import org.junit.runner.notification.*
import java.lang.reflect.*
import java.util.*
import kotlin.test.*

@InternalAPI
class ClientRunner(private val testClass: Class<ClientLoader>) : Runner(), Filterable {
    private var _filter: Filter? = null

    override fun run(notifier: RunNotifier) {
        val engines: List<HttpClientEngineContainer> = HttpClientEngineContainer::class.java.let {
            ServiceLoader.load(it, it.classLoader).toList()
        }

        for (engine in engines) {
            val instance = testClass.getDeclaredConstructor().newInstance()
            instance.engine = engine

            for (method in testClass.methods) {
                if (!method.isAnnotationPresent(Test::class.java)) continue

                val description = Description.createTestDescription(testClass, "${method.name}($engine)")
                val filterDescription = Description.createTestDescription(testClass, method.name)

                val filter = _filter
                if (filter != null && !filter.shouldRun(filterDescription)) continue

                val testNotifier = EachTestNotifier(notifier, description)

                if (method.isAnnotationPresent(Ignore::class.java)) {
                    testNotifier.fireTestIgnored()
                    continue
                }

                testNotifier.fireTestStarted()
                try {
                    method.invoke(instance)
                } catch (cause: StoppedByUserException) {
                    throw cause
                } catch (origin: InvocationTargetException) {
                    testNotifier.addFailure(origin.cause)
                } finally {
                    testNotifier.fireTestFinished()
                }
            }
        }
    }

    override fun filter(filter: Filter) {
        _filter = filter
    }

    override fun getDescription(): Description {
        return Description.createSuiteDescription(testClass.canonicalName)
    }
}
