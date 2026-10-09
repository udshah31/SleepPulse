import Foundation
import XCTest
@testable import SleepPulse

final class HealthKitSleepNormalizerTests: XCTestCase {
    func testOverlappingSameSourceSamplesUseInBedBoundsAndKnownStages() {
        let samples = [
            sample("bed", source: "watch", stage: .inBed, start: "2026-10-07T22:00:00Z", end: "2026-10-08T06:00:00Z"),
            sample("core", source: "watch", stage: .core, start: "2026-10-07T22:30:00Z", end: "2026-10-08T01:00:00Z"),
            sample("deep", source: "watch", stage: .deep, start: "2026-10-08T01:00:00Z", end: "2026-10-08T02:00:00Z"),
            sample("rem", source: "watch", stage: .rem, start: "2026-10-08T02:00:00Z", end: "2026-10-08T03:00:00Z"),
            sample("awake", source: "watch", stage: .awake, start: "2026-10-08T03:00:00Z", end: "2026-10-08T03:15:00Z")
        ]

        let episodes = HealthKitSleepNormalizer.episodes(from: samples)

        XCTAssertEqual(episodes.count, 1)
        XCTAssertEqual(episodes[0].start, date("2026-10-07T22:00:00Z"))
        XCTAssertEqual(episodes[0].end, date("2026-10-08T06:00:00Z"))
        XCTAssertEqual(episodes[0].inBedMinutes, 480)
        XCTAssertEqual(episodes[0].coreMinutes, 150)
        XCTAssertEqual(episodes[0].deepMinutes, 60)
        XCTAssertEqual(episodes[0].remMinutes, 60)
        XCTAssertEqual(episodes[0].awakeMinutes, 15)
        XCTAssertEqual(episodes[0].asleepMinutes, 270)
    }

    func testStageOnlySamplesDefineEpisodeBoundsWhenInBedIsAbsent() {
        let samples = [
            sample("core", source: "watch", stage: .core, start: "2026-10-07T23:10:00Z", end: "2026-10-08T01:00:00Z"),
            sample("awake", source: "watch", stage: .awake, start: "2026-10-08T01:00:00Z", end: "2026-10-08T01:20:00Z")
        ]

        let episodes = HealthKitSleepNormalizer.episodes(from: samples)
        XCTAssertEqual(episodes.count, 1)
        let episode = try! XCTUnwrap(episodes.first)

        XCTAssertEqual(episode.start, date("2026-10-07T23:10:00Z"))
        XCTAssertEqual(episode.end, date("2026-10-08T01:20:00Z"))
        XCTAssertNil(episode.inBedMinutes)
        XCTAssertEqual(episode.coreMinutes, 110)
        XCTAssertEqual(episode.awakeMinutes, 20)
        XCTAssertEqual(episode.asleepMinutes, 110)
    }

    func testOverlappingSamplesForOneStageUseTheIntervalUnion() {
        let samples = [
            sample("core-a", source: "watch", stage: .core, start: "2026-10-07T22:00:00Z", end: "2026-10-07T23:00:00Z"),
            sample("core-b", source: "watch", stage: .core, start: "2026-10-07T22:30:00Z", end: "2026-10-07T23:30:00Z"),
            sample("deep", source: "watch", stage: .deep, start: "2026-10-07T23:30:00Z", end: "2026-10-08T00:00:00Z")
        ]

        let episode = try! XCTUnwrap(HealthKitSleepNormalizer.episodes(from: samples).first)

        XCTAssertEqual(episode.coreMinutes, 90)
        XCTAssertEqual(episode.deepMinutes, 30)
        XCTAssertEqual(episode.asleepMinutes, 120)
    }

    func testPositiveGapSplitsSameSourceEpisodes() {
        let samples = [
            sample("first", source: "watch", stage: .core, start: "2026-10-07T22:00:00Z", end: "2026-10-07T23:00:00Z"),
            sample("second", source: "watch", stage: .deep, start: "2026-10-07T23:00:01Z", end: "2026-10-08T00:00:00Z")
        ]

        let episodes = HealthKitSleepNormalizer.episodes(from: samples)

        XCTAssertEqual(episodes.count, 2)
        XCTAssertEqual(episodes[0].start, date("2026-10-07T23:00:01Z"))
        XCTAssertEqual(episodes[1].start, date("2026-10-07T22:00:00Z"))
    }

    func testOverlappingTimesFromDifferentSourcesRemainSeparate() {
        let samples = [
            sample("watch", source: "watch", name: "Apple Watch", stage: .deep,
                   start: "2026-10-07T22:00:00Z", end: "2026-10-08T00:00:00Z"),
            sample("phone", source: "phone", name: "Sleep App", stage: .rem,
                   start: "2026-10-07T23:00:00Z", end: "2026-10-08T01:00:00Z")
        ]

        let episodes = HealthKitSleepNormalizer.episodes(from: samples)

        XCTAssertEqual(episodes.count, 2)
        XCTAssertEqual(Set(episodes.map(\.sourceIdentifier)), Set(["watch", "phone"]))
    }

    func testUnknownStagesDoNotContributeToKnownDurations() {
        let samples = [
            sample("unknown", source: "watch", stage: .unknown, start: "2026-10-07T22:00:00Z", end: "2026-10-07T23:00:00Z"),
            sample("unspecified", source: "watch", stage: .asleepUnspecified,
                   start: "2026-10-07T23:00:00Z", end: "2026-10-08T00:00:00Z")
        ]

        let episode = try! XCTUnwrap(HealthKitSleepNormalizer.episodes(from: samples).first)

        XCTAssertNil(episode.inBedMinutes)
        XCTAssertNil(episode.coreMinutes)
        XCTAssertNil(episode.deepMinutes)
        XCTAssertNil(episode.remMinutes)
        XCTAssertNil(episode.awakeMinutes)
        XCTAssertEqual(episode.asleepMinutes, 60)
    }

    func testZeroAndSubMinuteKnownDurationsAreRetainedAsZero() {
        let samples = [
            sample("zero", source: "watch", stage: .core, start: "2026-10-07T22:00:00Z", end: "2026-10-07T22:00:00Z"),
            sample("sub-minute", source: "watch", stage: .deep, start: "2026-10-07T22:00:00Z", end: "2026-10-07T22:00:59Z")
        ]

        let episode = try! XCTUnwrap(HealthKitSleepNormalizer.episodes(from: samples).first)

        XCTAssertEqual(episode.coreMinutes, 0)
        XCTAssertEqual(episode.deepMinutes, 0)
        XCTAssertEqual(episode.asleepMinutes, 0)
    }

    func testIDsAreStableNewestFirstAndIndependentOfDefaultTimezone() throws {
        let samples = [
            sample("old", source: "watch", stage: .core, start: "2026-10-07T22:00:00Z", end: "2026-10-07T23:00:00Z"),
            sample("new", source: "watch", stage: .deep, start: "2026-10-08T01:00:00Z", end: "2026-10-08T02:00:00Z")
        ]
        let originalTimezone = NSTimeZone.default
        defer { NSTimeZone.default = originalTimezone }

        NSTimeZone.default = TimeZone(secondsFromGMT: -8 * 60 * 60)!
        let first = HealthKitSleepNormalizer.episodes(from: samples)
        NSTimeZone.default = TimeZone(secondsFromGMT: 9 * 60 * 60)!
        let second = HealthKitSleepNormalizer.episodes(from: samples.reversed())

        XCTAssertEqual(first, second)
        XCTAssertEqual(first[0].start, date("2026-10-08T01:00:00Z"))
        XCTAssertEqual(first.map(\.start), [date("2026-10-08T01:00:00Z"), date("2026-10-07T22:00:00Z")])
        XCTAssertEqual(first.map(\.id), [
            "watch|1791421200000|1791424800000",
            "watch|1791410400000|1791414000000"
        ])
    }

    func testNativeValuesRoundTripThroughCodable() throws {
        let sample = HealthKitSleepSample(
            id: "core", sourceIdentifier: "watch", sourceName: "Apple Watch", stage: .core,
            start: date("2026-10-07T22:00:00Z"), end: date("2026-10-08T01:00:00Z")
        )
        let episode = HealthKitSleepEpisode(
            id: "watch|1|2", sourceIdentifier: "watch", sourceName: "Apple Watch",
            start: date("2026-10-07T22:00:00Z"), end: date("2026-10-08T06:00:00Z"),
            inBedMinutes: 480, asleepMinutes: 420, awakeMinutes: 15,
            coreMinutes: 240, deepMinutes: 90, remMinutes: 90
        )
        let cache = HealthKitSleepCache(
            formatVersion: 1, episodes: [episode], fetchedAt: date("2026-10-08T07:00:00Z"),
            windowStart: date("2026-09-08T07:00:00Z"), windowEnd: date("2026-10-08T07:00:00Z")
        )
        let state = HealthKitSleepState(
            phase: .loaded, episodes: [episode], fetchedAt: cache.fetchedAt,
            windowStart: cache.windowStart, windowEnd: cache.windowEnd, error: nil
        )

        let sampleData = try JSONEncoder().encode(sample)
        let cacheData = try JSONEncoder().encode(cache)
        let stateData = try JSONEncoder().encode(state)
        let decodedSample = try JSONDecoder().decode(HealthKitSleepSample.self, from: sampleData)
        let decodedCache = try JSONDecoder().decode(HealthKitSleepCache.self, from: cacheData)
        let decodedState = try JSONDecoder().decode(HealthKitSleepState.self, from: stateData)

        XCTAssertEqual(decodedSample, sample)
        XCTAssertEqual(decodedCache, cache)
        XCTAssertEqual(decodedState, state)
    }

    private func sample(
        _ id: String,
        source: String,
        name: String? = nil,
        stage: HealthKitSleepStage,
        start: String,
        end: String
    ) -> HealthKitSleepSample {
        HealthKitSleepSample(
            id: id, sourceIdentifier: source, sourceName: name ?? source,
            stage: stage, start: date(start), end: date(end)
        )
    }

    private func date(_ value: String) -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)!
    }
}
