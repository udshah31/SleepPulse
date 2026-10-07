import SwiftUI

enum CalmNightTheme {
    static let background = Color(red: 11 / 255, green: 15 / 255, blue: 34 / 255)
    static let backgroundEnd = Color(red: 17 / 255, green: 22 / 255, blue: 51 / 255)
    static let surface = Color(red: 21 / 255, green: 27 / 255, blue: 61 / 255)
    static let accent = Color(red: 124 / 255, green: 139 / 255, blue: 255 / 255)
    static let recovery = Color(red: 95 / 255, green: 217 / 255, blue: 138 / 255)
    static let textPrimary = Color(red: 228 / 255, green: 230 / 255, blue: 245 / 255)
    static let textSecondary = Color(red: 139 / 255, green: 147 / 255, blue: 196 / 255)
    static let textTertiary = Color(red: 92 / 255, green: 100 / 255, blue: 137 / 255)
}

struct CalmNightCard<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(CalmNightTheme.surface.opacity(0.92), in: RoundedRectangle(cornerRadius: 24))
    }
}
