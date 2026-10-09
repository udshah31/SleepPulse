import Foundation

protocol HealthKitCacheStorage {
    func load() throws -> HealthKitSleepCache?
    /// Must either replace the entire snapshot or leave the previous file intact.
    func replace(with cache: HealthKitSleepCache) throws
}

final class HealthKitCacheStore: HealthKitCacheStorage {
    private let customURL: URL?
    private let write: (Data, URL, Data.WritingOptions) throws -> Void

    init(url: URL? = nil, write: @escaping (Data, URL, Data.WritingOptions) throws -> Void = {
        try $0.write(to: $1, options: $2)
    }) {
        customURL = url
        self.write = write
    }

    func load() throws -> HealthKitSleepCache? {
        let url = try cacheURL()
        let data: Data
        do {
            data = try Data(contentsOf: url)
        } catch CocoaError.fileReadNoSuchFile {
            return nil
        }
        let cache = try JSONDecoder().decode(HealthKitSleepCache.self, from: data)
        try requireCurrentVersion(cache)
        return cache
    }

    func replace(with cache: HealthKitSleepCache) throws {
        try requireCurrentVersion(cache)
        // Complete encoding before touching the destination. Data's atomic write stages a
        // temporary file and replaces only on success; never remove the old file first.
        let data = try JSONEncoder().encode(cache)
        let url = try cacheURL()
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try write(data, url, .atomic)
    }

    private func cacheURL() throws -> URL {
        if let customURL { return customURL }
        return try FileManager.default.url(for: .applicationSupportDirectory,
            in: .userDomainMask, appropriateFor: nil, create: true)
            .appendingPathComponent("SleepPulse", isDirectory: true)
            .appendingPathComponent("healthkit-sleep-cache.json")
    }

    private func requireCurrentVersion(_ cache: HealthKitSleepCache) throws {
        guard cache.formatVersion == HealthKitSleepCache.currentFormatVersion else {
            throw CacheError.unsupportedVersion
        }
    }

    private enum CacheError: LocalizedError {
        case unsupportedVersion
        var errorDescription: String? { "The saved Apple Health cache version is unsupported. Refresh to reload it." }
    }
}
