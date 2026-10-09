import Foundation

/// Root-owned storage selection. Nil paths retain each store's normal user location.
struct AppStorageConfiguration {
    private let directory: URL?
    var databasePath: String? { directory?.appendingPathComponent("sleeppulse-simulated.db").path }
    var healthKitCacheURL: URL? { directory?.appendingPathComponent("healthkit-sleep-cache.json") }

    init(environment: [String: String] = ProcessInfo.processInfo.environment,
         applicationSupport: () throws -> URL = {
             try FileManager.default.url(for: .applicationSupportDirectory,
                 in: .userDomainMask, appropriateFor: nil, create: true)
         }) throws {
        #if DEBUG
        guard let value = environment["SLEEPPULSE_UI_TEST_STORAGE_ID"] else {
            directory = nil
            return
        }
        guard let id = UUID(uuidString: value), value.lowercased() == id.uuidString.lowercased() else {
            throw ConfigurationError.invalidStorageID
        }
        let selected = try applicationSupport().appendingPathComponent("SleepPulse/UITests", isDirectory: true)
            .appendingPathComponent(id.uuidString, isDirectory: true)
        // Create only the selected namespace. Existing contents survive reconstruction.
        try FileManager.default.createDirectory(at: selected, withIntermediateDirectories: true)
        directory = selected
        #else
        directory = nil
        #endif
    }

    private enum ConfigurationError: Error { case invalidStorageID }
}
