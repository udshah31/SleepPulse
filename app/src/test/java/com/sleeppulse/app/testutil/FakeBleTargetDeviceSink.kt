package com.sleeppulse.app.testutil

import com.sleeppulse.app.data.source.BleTargetDeviceSink

class FakeBleTargetDeviceSink : BleTargetDeviceSink {
    var lastTargetAddress: String? = null
        private set

    override fun setTargetDevice(address: String) {
        lastTargetAddress = address
    }
}
