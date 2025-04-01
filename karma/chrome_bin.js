/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

const puppeteer = require('puppeteer');

config.set({
    browsers: ['MyChromeHeadless'],
    customLaunchers: {
        MyChromeHeadless: {
            base: 'ChromeHeadless',
            flags: [
                '--allow-failed-policy-fetch-for-test',
                '--allow-external-pages',
                '--allow-file-access',
                '--allow-file-access-from-files',
                '--use-fake-device-for-media-stream',
                '--use-fake-ui-for-media-stream',
                '--no-sandbox',
                //'--use-file-for-fake-audio-capture=/Users/oleh.pantus/Documents/jetbrains/ktor/karma/test.wav',
                '--use-file-for-fake-video-capture=/Users/oleh.pantus/Documents/jetbrains/ktor/karma/output.mjpeg',
                '--disable-web-security',
                '--disable-setuid-sandbox',
                '--enable-logging',
                '--v=1'
            ]
        }
    },
    client: {
        captureConsole: true,
        mocha: {
            // Disable timeout as we use individual timeouts for tests
            timeout: 0
        }
    }
});

process.env.CHROME_BIN = puppeteer.executablePath();
