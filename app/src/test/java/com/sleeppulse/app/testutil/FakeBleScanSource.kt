package com.sleeppulse.app.testutil

import com.sleeppulse.app.data.source.BleScanSource
import com.sleeppulse.app.data.source.ScannedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class FakeBleScanSource : BleScanSource {
    val resultsFlow = MutableSharedFlow<List<ScannedDevice>>(extraBufferCapacity = 10)
    var errorToThrow: Throwable? = null

    var scanCallCount = 0
        private set

    override fun scan(): Flow<List<ScannedDevice>> {
        scanCallCount++
        return flow {
            errorToThrow?.let { throw it }
            emitAll(resultsFlow)
        }
    }
}
