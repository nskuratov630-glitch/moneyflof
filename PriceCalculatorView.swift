import SwiftUI

struct PriceCalculatorView: View {
    @EnvironmentObject private var api: APIClient
    @State private var price = "10.00"
    @State private var quantity = "1"
    @State private var discount = "0"
    @State private var fixedDiscount = "0.00"
    @State private var tax = "0"
    @State private var fee = "0.00"
    @State private var result: PriceResult?
    @State private var error: String?
    @State private var loading = false
    var body: some View {
        NavigationStack { ScrollView { VStack(spacing: 16) {
            MFCard { Text("Параметры цены").font(.title3.bold()); AmountField(title: "Цена за единицу", text: $price); HStack { VStack(alignment: .leading) { Text("Количество").font(.caption).foregroundStyle(MFTheme.muted); TextField("1", text: $quantity).keyboardType(.numberPad).padding(12).background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 12)) }; VStack(alignment: .leading) { Text("Скидка (%)").font(.caption).foregroundStyle(MFTheme.muted); TextField("0", text: $discount).keyboardType(.decimalPad).padding(12).background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 12)) } }; AmountField(title: "Дополнительная скидка", text: $fixedDiscount); HStack { VStack(alignment: .leading) { Text("Налог (%)").font(.caption).foregroundStyle(MFTheme.muted); TextField("0", text: $tax).keyboardType(.decimalPad).padding(12).background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 12)) }; AmountField(title: "Сбор", text: $fee) }; Button { Task { await calculate() } } label: { HStack { Spacer(); if loading { ProgressView() } else { Text("Рассчитать").bold() }; Spacer() }.padding(14).background(MFTheme.accent, in: RoundedRectangle(cornerRadius: 12)).foregroundStyle(.black) }.disabled(loading) }
            if let result { MFCard { Text("Итоговая стоимость").foregroundStyle(MFTheme.muted); Text(MoneyFormat.string(result.totalMinor)).font(.system(size: 34, weight: .bold, design: .rounded)).foregroundStyle(MFTheme.accent); line("Сумма до скидок", result.subtotalMinor); line("Скидка", -result.discountMinor); line("Налог", result.taxMinor); line("Сбор", result.feeMinor); Text("Экономия: \(result.savedPercent, specifier: "%.1f")%").foregroundStyle(MFTheme.accent) } }
            if let error { Text(error).foregroundStyle(.red).font(.footnote) }
        }.padding(16) }.background(MFTheme.background).navigationTitle("Калькулятор цены") }
    }
    private func line(_ label: String, _ amount: Int64) -> some View { HStack { Text(label).foregroundStyle(MFTheme.muted); Spacer(); Text(MoneyFormat.string(amount)).monospacedDigit() }.font(.subheadline) }
    private func calculate() async { guard let p = MoneyFormat.minorUnits(price), let q = Int(quantity), q > 0, let fixed = MoneyFormat.minorUnits(fixedDiscount), let f = MoneyFormat.minorUnits(fee), let d = Double(discount), let t = Double(tax), (0...100).contains(d), (0...100).contains(t) else { error = "Проверь цену, количество, скидку и налог."; return }; loading = true; defer { loading = false }; do { result = try await api.post("api/calculator/price", body: PriceRequest(unitPriceMinor: p, quantity: q, discountPercent: d, fixedDiscountMinor: fixed, taxPercent: t, feeMinor: f)); error = nil } catch { self.error = error.localizedDescription } }
}
