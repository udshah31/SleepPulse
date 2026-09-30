package com.sleeppulse.app.services

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
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
}
