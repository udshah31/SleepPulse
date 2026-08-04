package com.sleeppulse.app.testutil

import com.sleeppulse.app.widget.WidgetRefresher

class FakeWidgetRefresher : WidgetRefresher {
    var refreshCallCount = 0
        private set

    override suspend fun refresh() {
        refreshCallCount++
    }
}
