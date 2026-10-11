import SwiftUI

struct GoalsView: View {
    @EnvironmentObject private var api: APIClient
    @State private var goals: [Goal] = []
    @State private var wallets: [Wallet] = []
    @State private var showingAdd = false
    @State private var contributionGoal: Goal?
    @State private var error: String?
    var body: some View {
        NavigationStack {
            ScrollView { VStack(alignment: .leading, spacing: 14) {
                if let error { Text(error).foregroundStyle(.red) }
                if goals.isEmpty { MFCard { Image(systemName: "scope").font(.largeTitle).foregroundStyle(MFTheme.accent); Text("Копи на важное").font(.title3.bold()); Text("Создай цель и отслеживай прогресс накоплений.").foregroundStyle(MFTheme.muted) } }
                ForEach(goals) { goal in
                    MFCard {
                        HStack { Image(systemName: "flag.fill").foregroundStyle(MFTheme.accent); VStack(alignment: .leading) { Text(goal.name).font(.headline); Text(goal.currency).font(.caption).foregroundStyle(MFTheme.muted) }; Spacer(); Text("\(min(100, Int((Double(goal.savedMinor) / Double(max(goal.targetMinor, 1))) * 100)))%").font(.headline).foregroundStyle(MFTheme.accent) }
                        ProgressView(value: min(1, Double(goal.savedMinor) / Double(max(goal.targetMinor, 1)))).tint(MFTheme.accent)
                        HStack { Text(MoneyFormat.string(goal.savedMinor, currency: goal.currency)); Spacer(); Text("из \(MoneyFormat.string(goal.targetMinor, currency: goal.currency))").foregroundStyle(MFTheme.muted) }.font(.caption.monospacedDigit())
                        Button { contributionGoal = goal } label: { Label("Пополнить цель", systemImage: "plus.circle.fill").frame(maxWidth: .infinity) }.buttonStyle(.borderedProminent)
                    }
                }
            }.padding(16) }.background(MFTheme.background).navigationTitle("Цели").toolbar { ToolbarItem(placement: .topBarTrailing) { Button { showingAdd = true } label: { Image(systemName: "plus.circle.fill").font(.title2) } } }.task { await load() }.refreshable { await load() }.sheet(isPresented: $showingAdd) { AddGoalView { await load() } }.sheet(item: $contributionGoal) { goal in ContributeGoalView(goal: goal, wallets: wallets) { await load() } }
        }
    }
    private func load() async { do { async let g: [Goal] = api.get("api/goals"); async let w: [Wallet] = api.get("api/wallets"); goals = try await g; wallets = try await w; error = nil } catch { self.error = error.localizedDescription } }
}

struct AddGoalView: View {
    @EnvironmentObject private var api: APIClient
    @Environment(\.dismiss) private var dismiss
    var onSaved: () async -> Void
    @State private var name = ""
    @State private var currency = "EUR"
    @State private var target = ""
    @State private var date = Calendar.current.date(byAdding: .year, value: 1, to: Date()) ?? Date()
    @State private var error: String?
    var body: some View { NavigationStack { Form { TextField("Название цели", text: $name); Picker("Валюта", selection: $currency) { ForEach(["EUR", "USD", "GBP", "PLN", "DKK"], id: \.self) { Text($0).tag($0) } }; AmountField(title: "Нужная сумма", text: $target); DatePicker("Желаемая дата", selection: $date, displayedComponents: .date); if let error { Text(error).foregroundStyle(.red) } }.navigationTitle("Новая цель").toolbar { ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Создать") { Task { await save() } } } } } }
    private func save() async { guard let minor = MoneyFormat.minorUnits(target), minor > 0, !name.isEmpty else { error = "Укажи название и сумму цели."; return }; let df = DateFormatter(); df.dateFormat = "yyyy-MM-dd"; do { let _: Goal = try await api.post("api/goals", body: CreateGoal(name: name, currency: currency, targetMinor: minor, targetDate: df.string(from: date))); await onSaved(); dismiss() } catch { self.error = error.localizedDescription } }
}

struct ContributeGoalView: View {
    @EnvironmentObject private var api: APIClient
    @Environment(\.dismiss) private var dismiss
    let goal: Goal; let wallets: [Wallet]; var onSaved: () async -> Void
    @State private var walletID: Int64 = 0; @State private var amount = ""; @State private var note = ""; @State private var error: String?
    private var compatible: [Wallet] { wallets.filter { $0.currency == goal.currency } }
    var body: some View { NavigationStack { Form { Section("Пополнение") { Text("Цель: \(goal.name)"); Picker("Списать с кошелька", selection: $walletID) { ForEach(compatible) { Text($0.name).tag($0.id) } }; AmountField(title: "Сумма · \(goal.currency)", text: $amount); TextField("Заметка", text: $note) }; if let error { Text(error).foregroundStyle(.red) } }.navigationTitle("Пополнить").toolbar { ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Внести") { Task { await save() } }.disabled(compatible.isEmpty) } }.onAppear { walletID = compatible.first?.id ?? 0 } } }
    private func save() async { guard let minor = MoneyFormat.minorUnits(amount), minor > 0 else { error = "Укажи корректную сумму."; return }; do { let _: EmptyResponse = try await api.post("api/goals/\(goal.id)/contributions", body: GoalContribution(walletId: walletID, amountMinor: minor, note: note)); await onSaved(); dismiss() } catch { self.error = error.localizedDescription } }
}
