/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import test.server.TestServerService

val testServerLibraries = TestServerService.serverLibraries(project)

tasks.withType<AbstractTestTask>().configureEach {
    val testServerService = TestServerService.registerIfAbsent(project)
    usesService(testServerService)
    // Local copy: capturing the script property in the action would reference the script object
    val libraries = testServerLibraries
    inputs.files(libraries)
        .withPropertyName("testServerLibraries")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    doFirst("start test server") { testServerService.get().start(libraries.singleFile) }
}
