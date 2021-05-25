// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.selector

import io.ktor.util.*

/**
 * A selectable entity with selectable NIO [channel], [interestedOps] subscriptions
 */
public actual interface Selectable
