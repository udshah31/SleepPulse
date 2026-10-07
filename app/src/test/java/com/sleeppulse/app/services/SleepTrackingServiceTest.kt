package com.sleeppulse.app.services

import android.content.pm.ServiceInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTrackingServiceTest {

    @Test
    fun `microphone type is claimed only when the mic permission is granted`() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH, SleepTrackingService.foregroundServiceTypes(micGranted = false))
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            SleepTrackingService.foregroundServiceTypes(micGranted = true),
        )
    }

    @Test
    fun `the health service type's prerequisite can't be denied by the user`() {
        // Android 14+ crashes startForeground(type=health) unless a prerequisite permission is held; the
        // runtime ones (ACTIVITY_RECOGNITION, health.*) can be denied, HIGH_SAMPLING_RATE_SENSORS can't.
        // Gradle runs unit tests with the module directory as the working directory.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("service no longer declares the health type?", "foregroundServiceType=\"health" in manifest)
        assertTrue(
            "HIGH_SAMPLING_RATE_SENSORS missing: Connect crashes when activity recognition is denied",
            "android.permission.HIGH_SAMPLING_RATE_SENSORS" in manifest,
        )
    }
}
