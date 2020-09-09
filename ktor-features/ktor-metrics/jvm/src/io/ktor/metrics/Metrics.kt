/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.metrics

import io.ktor.metrics.dropwizard.*

/**
 * Dropwizard metrics feature. Use [DropwizardMetrics] or instead.
 */
@Suppress("unused")
@Deprecated(message = "Use DropwizardMetrics or MicrometerMetrics instead.",
    replaceWith = ReplaceWith(
        expression = "DropwizardMetrics",
        imports = arrayOf("io.ktor.metrics.dropwizard.DropwizardMetrics")),
    level = DeprecationLevel.ERROR)
public typealias Metrics = DropwizardMetrics

