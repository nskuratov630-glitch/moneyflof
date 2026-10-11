import SwiftUI

struct RecurringView: View {
    @EnvironmentObject private var api: APIClient
    @State private var rules: [RecurringRule] = []
    @State private var wallets: [Wallet] = []
    @State private var showingAdd = false
    @State private var error: String?
    var body: some View { NavigationStack { List { if let error { Text(error).foregroundStyle(.red) }; ForEach(rules) { rule in VStack(alignment: .leading, spacing: 8) { HStack { Text(rule.category).font(.headline); Spacer(); Text(MoneyFormat.string(rule.amountMinor, currency: rule.currency ?? "EUR")).bold() }; Text("\(rule.type == "INCOME" ? "Доход" : "Расход") · \(rule.frequency) · \(rule.nextRunAt)").font(.caption).foregroundStyle(.secondary); HStack { Text(rule.active ? "Активно" : "На паузе").font(.caption).foregroundStyle(rule.active ? MFTheme.accent : .orange); Spacer(); Button(rule.active ? "Пауза" : "Продолжить") { Task { await toggle(rule) } }.buttonStyle(.bordered) ; Button("Выполнить") { Task { await run(rule) } }.buttonStyle(.borderedProminent).disabled(!rule.active) } }.padding(.vertical, 6).listRowBackground(MFTheme.card) } }.scrollContentBackground(.hidden).background(MFTheme.background).navigationTitle("Регулярные").toolbar { ToolbarItem(placement: .topBarTrailing) { Button { showingAdd = true } label: { Image(systemName: "plus.circle.fill").font(.title2) } } }.task { await load() }.refreshable { await load() }.sheet(isPresented: $showingAdd) { AddRecurringView(wallets: wallets) { await load() } } } }
    private func load() async { do { async let r: [RecurringRule] = api.get("api/recurring"); async let w: [Wallet] = api.get("api/wallets"); rules = try await r; wallets = try await w; error = nil } catch { self.error = error.localizedDescription } }
    private func toggle(_ rule: RecurringRule) async { do { let _: EmptyResponse = try await api.patch("api/recurring/\(rule.id)", body: ActiveBody(active: !rule.active)); await load() } catch { self.error = error.localizedDescription } }
    private func run(_ rule: RecurringRule) async { do { let _: EmptyResponse = try await api.post("api/recurring/\(rule.id)/run", body: EmptyBody()); await load() } catch { self.error = error.localizedDescription } }
}
private struct ActiveBody: Encodable { let active: Bool }
private struct EmptyBody: Encodable {}

struct AddRecurringView: View {
    @EnvironmentObject private var api: APIClient
    @Environment(\.dismiss) private var dismiss
    let wallets: [Wallet]; var onSaved: () async -> Void
    @State private var walletID: Int64 = 0; @State private var type = "EXPENSE"; @State private var amount = ""; @State private var category = "Bills"; @State private var note = ""; @State private var frequency = "MONTHLY"; @State private var nextRun = Date(); @State private var error: String?
    var body: some View { NavigationStack { Form { Picker("Тип", selection: $type) { Text("Расход").tag("EXPENSE"); Text("Доход").tag("INCOME") }; Picker("Кошелёк", selection: $walletID) { ForEach(wallets) { Text($0.name).tag($0.id) } }; AmountField(title: "Сумма", text: $amount); TextField("Категория", text: $category); TextField("Описание", text: $note); Picker("Период", selection: $frequency) { Text("Каждую неделю").tag("WEEKLY"); Text("Каждый месяц").tag("MONTHLY"); Text("Каждый год").tag("YEARLY") }; DatePicker("Первая дата", selection: $nextRun, displayedComponents: .date); if let error { Text(error).foregroundStyle(.red) } }.navigationTitle("Регулярная операция").toolbar { ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Создать") { Task { await save() } }.disabled(wallets.isEmpty) } }.onAppear { walletID = wallets.first?.id ?? 0 } } }
    private func save() async { guard let minor = MoneyFormat.minorUnits(amount), minor > 0 else { error = "Укажи сумму больше нуля."; return }; let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"; do { let _: RecurringRule = try await api.post("api/recurring", body: CreateRecurring(walletId: walletID, type: type, amountMinor: minor, category: category, description: note, frequency: frequency, nextRunAt: df.string(from: nextRun))); await onSaved(); dismiss() } catch { self.error = error.localizedDescription } }
}
