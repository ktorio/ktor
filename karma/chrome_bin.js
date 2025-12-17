/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

config.set({
    "browsers": ["MyChromeHeadless"],
    "customLaunchers": {
        "MyChromeHeadless": {
            base: "ChromeHeadless",
            flags: [
                "--allow-failed-policy-fetch-for-test",
                "--allow-external-pages",
                "--no-sandbox",
                "--disable-web-security",
                "--disable-setuid-sandbox",
                "--enable-logging",
                "--v=1",
                "--use-fake-device-for-media-stream",
                "--use-fake-ui-for-media-stream"
            ]
        }
    },
    "client": {
        captureConsole: true,
        "mocha": {
            // Disable timeout as we use individual timeouts for tests
            timeout: 0
        }
    }
});

// CHROME_BIN might be already defined, otherwise use puppeteer to get the path
if (!process.env.CHROME_BIN) {
    process.env.CHROME_BIN = require('puppeteer').executablePath();
}
