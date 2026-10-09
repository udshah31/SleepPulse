import Foundation

enum HealthKitSleepNormalizer {
    static func episodes(from samples: [HealthKitSleepSample]) -> [HealthKitSleepEpisode] {
        Dictionary(grouping: samples, by: \.sourceIdentifier)
            .values
            .flatMap(normalizeSource)
            .sorted { lhs, rhs in
                if lhs.start != rhs.start { return lhs.start > rhs.start }
                if lhs.end != rhs.end { return lhs.end > rhs.end }
                return lhs.id < rhs.id
            }
    }

    private struct NormalizedSample {
        let sample: HealthKitSleepSample
        let start: Date
        let end: Date
    }

    private static func normalizeSource(_ samples: [HealthKitSleepSample]) -> [HealthKitSleepEpisode] {
        let ordered = samples
            .map { sample in
                NormalizedSample(
                    sample: sample,
                    start: min(sample.start, sample.end),
                    end: max(sample.start, sample.end)
                )
            }
            .sorted { lhs, rhs in
                if lhs.start != rhs.start { return lhs.start < rhs.start }
                if lhs.end != rhs.end { return lhs.end < rhs.end }
                return lhs.sample.id < rhs.sample.id
            }

        var groups: [[NormalizedSample]] = []
        var current: [NormalizedSample] = []
        var currentEnd: Date?

        for value in ordered {
            if let end = currentEnd, value.start > end {
                groups.append(current)
                current = []
                currentEnd = nil
            }
            current.append(value)
            if let end = currentEnd {
                if value.end > end { currentEnd = value.end }
            } else {
                currentEnd = value.end
            }
        }
        if !current.isEmpty { groups.append(current) }

        return groups.map(makeEpisode)
    }

    private static func makeEpisode(from samples: [NormalizedSample]) -> HealthKitSleepEpisode {
        let sourceIdentifier = samples[0].sample.sourceIdentifier
        let sourceName = samples
            .map { $0.sample.sourceName }
            .filter { !$0.isEmpty }
            .sorted()
            .first ?? sourceIdentifier
        let inBedSamples = samples.filter { $0.sample.stage == .inBed }
        let outerSamples = inBedSamples.isEmpty ? samples : inBedSamples
        let start = outerSamples.map(\.start).min()!
        let end = outerSamples.map(\.end).max()!

        let coreMinutes = minutes(for: .core, in: samples)
        let deepMinutes = minutes(for: .deep, in: samples)
        let remMinutes = minutes(for: .rem, in: samples)
        // Union all explicit asleep intervals before flooring; categories may overlap.
        let asleepMinutes = minutes(for: [.core, .deep, .rem, .asleepUnspecified], in: samples)

        return HealthKitSleepEpisode(
            id: identifier(sourceIdentifier: sourceIdentifier, start: start, end: end),
            sourceIdentifier: sourceIdentifier,
            sourceName: sourceName,
            start: start,
            end: end,
            inBedMinutes: minutes(for: .inBed, in: samples),
            asleepMinutes: asleepMinutes,
            awakeMinutes: minutes(for: .awake, in: samples),
            coreMinutes: coreMinutes,
            deepMinutes: deepMinutes,
            remMinutes: remMinutes
        )
    }

    private static func minutes(
        for stage: HealthKitSleepStage,
        in samples: [NormalizedSample]
    ) -> Int? {
        minutes(for: [stage], in: samples)
    }

    private static func minutes(
        for stages: [HealthKitSleepStage],
        in samples: [NormalizedSample]
    ) -> Int? {
        let intervals = samples
            .filter { stages.contains($0.sample.stage) }
            .map { ($0.start, $0.end) }
            .sorted { $0.0 < $1.0 }
        guard !intervals.isEmpty else { return nil }

        var totalSeconds = 0.0
        var unionStart = intervals[0].0
        var unionEnd = intervals[0].1
        for (start, end) in intervals.dropFirst() {
            if start > unionEnd {
                totalSeconds += max(0, unionEnd.timeIntervalSince(unionStart))
                unionStart = start
                unionEnd = end
            } else if end > unionEnd {
                unionEnd = end
            }
        }
        totalSeconds += max(0, unionEnd.timeIntervalSince(unionStart))
        return max(0, Int(totalSeconds / 60))
    }

    private static func identifier(sourceIdentifier: String, start: Date, end: Date) -> String {
        let startBits = start.timeIntervalSinceReferenceDate.bitPattern
        let endBits = end.timeIntervalSinceReferenceDate.bitPattern
        return "\(sourceIdentifier)|\(startBits)|\(endBits)"
    }
}
