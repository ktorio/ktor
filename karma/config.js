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
    },
    webpack: {
        // Workaround for Node.js built-in modules in browser tests.
        // ktor-client-tests depends on ktor-network, which uses @JsModule("node:net") for Node.js socket support.
        // Webpack cannot resolve the "node:" protocol in browser builds, so we stub it with an empty object.
        // The Node.js socket code paths are not executed in browser tests anyway.
        externals: {
            'node:net': '{}'
        }
    }
});
