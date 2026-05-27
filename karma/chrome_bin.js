/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
                "--use-mock-keychain",
                "--password-store=basic",
                "--enable-logging",
                "--v=1",
                "--use-fake-device-for-media-stream",
                "--use-fake-ui-for-media-stream"
            ]
        }
    }
});

// CHROME_BIN might be already defined, otherwise use puppeteer to get the path
if (!process.env.CHROME_BIN) {
    const path = require('path');
    const os = require('os');
    const puppeteer = require('puppeteer');
    const { Browser, computeExecutablePath } = require('@puppeteer/browsers');

    process.env.CHROME_BIN = computeExecutablePath({
        browser: Browser.CHROME,
        buildId: puppeteer.PUPPETEER_REVISIONS.chrome,
        cacheDir: process.env.PUPPETEER_CACHE_DIR || path.join(os.homedir(), '.cache', 'puppeteer')
    });
}
