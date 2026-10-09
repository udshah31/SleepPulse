import SwiftUI

@main
struct SleepPulseApp: App {
    @StateObject private var store: TrackingStore
    @StateObject private var healthKitStore: HealthKitSleepStore

    init() {
        let storage: AppStorageConfiguration
        do { storage = try AppStorageConfiguration() }
        catch { fatalError("Cannot select app storage: \(error)") } // Never fall back to user data on a bad override.
        _store = StateObject(wrappedValue: TrackingStore(databasePath: storage.databasePath))
        _healthKitStore = StateObject(wrappedValue: HealthKitSleepStore(client: LiveHealthKitClient(),
            cache: HealthKitCacheStore(url: storage.healthKitCacheURL)))
    }

    var body: some Scene {
        WindowGroup {
            SleepPulseRootView(store: store, healthKitStore: healthKitStore)
        }
    }
}
