/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import kotlin.test.Test
import kotlin.test.assertFailsWith

// This test is separated because usually permissions are granted globally and setting it to false
// could cause undefined behaviour with race conditions
class WebRTCPermissionTest {
    @Test
    fun testPermissionsNotGranted() = runTestWithPermissions(audio = false, video = false, realTime = false) {
        assertFailsWith<WebRTCMedia.PermissionException> {
            createTestWebRTCClient().createAudioTrack(WebRTCMedia.AudioTrackConstraints())
        }
    }
}
