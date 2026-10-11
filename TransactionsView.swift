import SwiftUI

struct TransactionsView: View {
    @EnvironmentObject private var api: APIClient
    @State private var wallets: [Wallet] = []
    @State private var transactions: [FinanceTransaction] = []
    @State private var type = "ALL"
    @State private var showingAdd = false
    @State private var error: String?
    var body: some View {
        NavigationStack {
            List {
                Picker("Тип", selection: $type) { Text("Все").tag("ALL"); Text("Доходы").tag("INCOME"); Text("Расходы").tag("EXPENSE") }.pickerStyle(.segmented).listRowBackground(MFTheme.background)
                if let error { Text(error).foregroundStyle(.red).listRowBackground(MFTheme.background) }
                ForEach(transactions) { tx in TransactionRow(transaction: tx).listRowBackground(MFTheme.card).swipeActions { Button(role: .destructive) { Task { await remove(tx) } } label: { Label("Удалить", systemImage: "trash") } } }
            }.scrollContentBackground(.hidden).background(MFTheme.background).navigationTitle("Операции").toolbar { ToolbarItem(placement: .topBarTrailing) { Button { showingAdd = true } label: { Image(systemName: "plus.circle.fill").font(.title2) } } }.task { await load() }.onChange(of: type) { _, _ in Task { await load() } }.refreshable { await load() }.sheet(isPresented: $showingAdd) { AddTransactionView(wallets: wallets) { await load() } }
        }
    }
    private func load() async { do { async let w: [Wallet] = api.get("api/wallets"); let path = type == "ALL" ? "api/transactions?limit=100" : "api/transactions?limit=100&type=\(type)"; async let t: [FinanceTransaction] = api.get(path); wallets = try await w; transactions = try await t; error = nil } catch { self.error = error.localizedDescription } }
    private func remove(_ tx: FinanceTransaction) async { do { let _: EmptyResponse = try await api.delete("api/transactions/\(tx.id)"); await load() } catch { self.error = error.localizedDescription } }
}

struct AddTransactionView: View {
    @EnvironmentObject private var api: APIClient
    @Environment(\.dismiss) private var dismiss
    let wallets: [Wallet]
    var onSaved: () async -> Void
    @State private var walletID: Int64 = 0
    @State private var type = "EXPENSE"
    @State private var amount = ""
    @State private var category = "Food"
    @State private var note = ""
    @State private var error: String?
    @State private var saving = false
    var body: some View { NavigationStack { Form { Section("Операция") { Picker("Тип", selection: $type) { Text("Расход").tag("EXPENSE"); Text("Доход").tag("INCOME") }; Picker("Кошелёк", selection: $walletID) { ForEach(wallets) { w in Text("\(w.name) · \(w.currency)").tag(w.id) } }; AmountField(title: "Сумма", text: $amount); TextField("Категория", text: $category); TextField("Комментарий", text: $note) }; if let error { Text(error).foregroundStyle(.red) } }.navigationTitle("Новая операция").toolbar { ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Сохранить") { Task { await save() } }.disabled(saving || wallets.isEmpty) } }.onAppear { if walletID == 0 { walletID = wallets.first?.id ?? 0 } } } }
    private func save() async { guard let minor = MoneyFormat.minorUnits(amount), minor > 0, !category.trimmingCharacters(in: .whitespaces).isEmpty else { error = "Укажи сумму больше нуля и категорию."; return }; saving = true; defer { saving = false }; let formatter = ISO8601DateFormatter(); do { let _: FinanceTransaction = try await api.post("api/transactions", body: CreateTransaction(walletId: walletID, type: type, amountMinor: minor, category: category, description: note, occurredAt: formatter.string(from: Date()))); await onSaved(); dismiss() } catch { self.error = error.localizedDescription } }
}
