/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.util.*

/**
 * A selectable entity with selectable NIO [channel], [interestedOps] subscriptions
 */
@KtorExperimentalAPI
public actual interface Selectable
