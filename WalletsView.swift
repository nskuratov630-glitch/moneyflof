import SwiftUI

struct WalletsView: View {
    @EnvironmentObject private var api: APIClient
    @State private var wallets: [Wallet] = []
    @State private var showingAdd = false
    @State private var error: String?
    var body: some View {
        NavigationStack {
            List {
                if let error { Text(error).foregroundStyle(.red) }
                ForEach(wallets) { wallet in HStack(spacing: 12) { Image(systemName: "creditcard.fill").foregroundStyle(MFTheme.accent); VStack(alignment: .leading) { Text(wallet.name).font(.headline); Text(wallet.currency).font(.caption).foregroundStyle(.secondary) }; Spacer(); Text(MoneyFormat.string(wallet.balanceMinor, currency: wallet.currency)).font(.headline).monospacedDigit() }.listRowBackground(MFTheme.card) }
            }.scrollContentBackground(.hidden).background(MFTheme.background).navigationTitle("Кошельки").toolbar { ToolbarItem(placement: .topBarTrailing) { Button { showingAdd = true } label: { Image(systemName: "plus.circle.fill").font(.title2) } } }.task { await load() }.refreshable { await load() }.sheet(isPresented: $showingAdd) { AddWalletView { await load() } }
        }
    }
    private func load() async { do { wallets = try await api.get("api/wallets"); error = nil } catch { self.error = error.localizedDescription } }
}

struct AddWalletView: View {
    @EnvironmentObject private var api: APIClient
    @Environment(\.dismiss) private var dismiss
    var onSaved: () async -> Void
    @State private var name = ""
    @State private var currency = "EUR"
    @State private var balance = "0.00"
    @State private var error: String?
    @State private var saving = false
    var body: some View { NavigationStack { Form { Section("Новый кошелёк") { TextField("Название", text: $name); Picker("Валюта", selection: $currency) { ForEach(["EUR", "USD", "GBP", "PLN", "DKK", "CHF", "SEK", "NOK"], id: \.self) { Text($0).tag($0) } }; AmountField(title: "Начальный баланс", text: $balance) }; if let error { Text(error).foregroundStyle(.red) } }.navigationTitle("Добавить кошелёк").toolbar { ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Сохранить") { Task { await save() } }.disabled(saving || name.trimmingCharacters(in: .whitespaces).isEmpty) } } } }
    private func save() async { guard let minor = MoneyFormat.minorUnits(balance) else { error = "Введи корректную сумму."; return }; saving = true; defer { saving = false }; do { let _: Wallet = try await api.post("api/wallets", body: CreateWallet(name: name, currency: currency, initialBalanceMinor: minor)); await onSaved(); dismiss() } catch { self.error = error.localizedDescription } }
}
