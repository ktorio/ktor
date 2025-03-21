/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import test.server.TestServerService

tasks.withType<AbstractTestTask>().configureEach {
    val testServerService = TestServerService.registerIfAbsent(project)
    usesService(testServerService)
    // Trigger server start if it is not started yet
    doFirst("start test server") { testServerService.get() }
}
