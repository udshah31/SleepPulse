import Foundation

/// Calendar-only values are always rendered in UTC; device timezone cannot shift a saved date.
enum NightDateFormatting {
    static func text(_ isoDate: String) -> String {
        let parser = formatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.dateFormat = "yyyy-MM-dd"
        guard let date = parser.date(from: isoDate) else { return isoDate }
        let output = formatter()
        output.dateStyle = .medium
        return output.string(from: date)
    }

    static func axisLabel(epochDay: Int32) -> String {
        let output = formatter()
        output.setLocalizedDateFormatFromTemplate("MMM d")
        return output.string(from: Date(timeIntervalSince1970: Double(epochDay) * 86_400))
    }

    private static func formatter() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter
    }
}
