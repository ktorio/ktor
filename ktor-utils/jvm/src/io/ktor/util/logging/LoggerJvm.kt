// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

@AllowDifferentMembersInActual
public actual typealias Logger = org.slf4j.Logger

public actual val Logger.isTraceEnabled: Boolean get() = isTraceEnabled
