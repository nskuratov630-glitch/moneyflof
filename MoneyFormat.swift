import Foundation

enum MoneyFormat {
    static func string(_ minor: Int64, currency: String = "EUR") -> String {
        let amount = Decimal(minor) / Decimal(100)
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencyCode = currency
        formatter.maximumFractionDigits = 2
        formatter.minimumFractionDigits = 2
        return formatter.string(from: amount as NSDecimalNumber) ?? "\(currency) \(NSDecimalNumber(decimal: amount))"
    }
    static func minorUnits(_ text: String) -> Int64? {
        let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")
        guard let value = Decimal(string: cleaned), value >= 0 else { return nil }
        var scaled = value * 100
        var rounded = Decimal()
        NSDecimalRound(&rounded, &scaled, 0, .plain)
        return NSDecimalNumber(decimal: rounded).int64Value
    }
}
