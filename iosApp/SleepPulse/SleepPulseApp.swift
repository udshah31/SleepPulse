import SwiftUI

@main
struct SleepPulseApp: App {
    @StateObject private var store = TrackingStore()

    var body: some Scene {
        WindowGroup {
            SleepPulseRootView(store: store)
        }
    }
}
