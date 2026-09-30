package com.sleeppulse.app.wear

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.ConnectionResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearDataClientTest {

    private fun apiError(code: Int) = ApiException(Status(code))

    @Test
    fun `a missing wearable api stops further sends`() {
        assertTrue(WearDataClient.isWearApiMissing(apiError(CommonStatusCodes.API_NOT_CONNECTED)))
        assertTrue(WearDataClient.isWearApiMissing(apiError(ConnectionResult.API_UNAVAILABLE)))
    }

    @Test
    fun `other failures are not mistaken for a missing api`() {
        assertFalse(WearDataClient.isWearApiMissing(apiError(CommonStatusCodes.NETWORK_ERROR)))
        assertFalse(WearDataClient.isWearApiMissing(apiError(CommonStatusCodes.TIMEOUT)))
        assertFalse(WearDataClient.isWearApiMissing(IllegalStateException("boom")))
    }
}
