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
                "--v=1"
            ]
        }
    },
    "client": {
        captureConsole: true,
        "mocha": {
            timeout: 10000
        }
    }
});

process.env.CHROME_BIN = require('puppeteer').executablePath();
