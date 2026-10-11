import SwiftUI

enum MFTheme {
    static let background = Color(red: 0.035, green: 0.055, blue: 0.09)
    static let card = Color(red: 0.075, green: 0.105, blue: 0.15)
    static let accent = Color(red: 0.12, green: 0.86, blue: 0.62)
    static let blue = Color(red: 0.28, green: 0.60, blue: 1.0)
    static let muted = Color.white.opacity(0.62)
}
struct MFCard<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View { VStack(alignment: .leading, spacing: 12) { content }.padding(16).frame(maxWidth: .infinity, alignment: .leading).background(MFTheme.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous)).overlay(RoundedRectangle(cornerRadius: 20).stroke(.white.opacity(0.06), lineWidth: 1)) }
}
struct AmountField: View {
    let title: String
    @Binding var text: String
    var body: some View { VStack(alignment: .leading, spacing: 7) { Text(title).font(.caption).foregroundStyle(MFTheme.muted); TextField("0.00", text: $text).keyboardType(.decimalPad).padding(13).background(.white.opacity(0.055), in: RoundedRectangle(cornerRadius: 12)) } }
}
