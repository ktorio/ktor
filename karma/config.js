/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

config.set({
    client: {
        captureConsole: true,
        mocha: {
            // Disable timeout as we use individual timeouts for tests
            timeout: 0
        }
    }
});
