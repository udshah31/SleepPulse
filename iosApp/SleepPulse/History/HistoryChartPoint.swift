import Foundation

struct HistoryChartPoint: Equatable, Identifiable {
    let night: SavedNight
    let segment: Int
    var id: Int32 { night.id }
    var day: Double { Double(id) }
    var hours: Double { Double(night.totalMinutes) / 60 }
    var durationAccessibility: String {
        "\(night.dateText), \(night.totalMinutes == 0 ? "less than 1 minute" : "\(night.totalMinutes) minutes") recorded"
    }

    static func make(nights: [SavedNight]) -> [HistoryChartPoint] {
        let window = Array(nights.prefix(14).reversed())
        var segment = 0
        return window.enumerated().map { index, night in
            if index > 0, night.id - window[index - 1].id != 1 { segment += 1 }
            return HistoryChartPoint(night: night, segment: segment)
        }
    }
}
