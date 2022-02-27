/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import org.slf4j.*

@Suppress("FunctionName")
public actual fun KtorSimpleLogger(name: String): Logger = LoggerFactory.getLogger(name)
