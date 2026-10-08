import SwiftUI

struct SleepPulseRootView: View {
    @ObservedObject var store: TrackingStore
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        TabView {
            DashboardView(store: store).tabItem { Label("Home", systemImage: "moon.stars.fill") }
            HistoryView(store: store).tabItem { Label("History", systemImage: "clock.fill") }
        }
        .tint(CalmNightTheme.accent)
        .preferredColorScheme(.dark)
        .onChange(of: scenePhase) { _, phase in store.sceneChanged(phase) }
    }
}
