import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var api: APIClient
    @State private var wallets: [Wallet] = []
    @State private var transactions: [FinanceTransaction] = []
    @State private var error: String?
    @State private var loading = false
    private var grouped: [(String, Int64)] { Dictionary(grouping: wallets, by: \.currency).map { key, values in (key, values.reduce(Int64(0)) { $0 + $1.balanceMinor }) }.sorted { $0.0 < $1.0 } }
    var body: some View {
        NavigationStack {
            ScrollView { VStack(alignment: .leading, spacing: 18) {
                HStack { VStack(alignment: .leading, spacing: 4) { Text("MoneyFlow").font(.title.bold()); Text(Date.now.formatted(.dateTime.month(.wide).year())).foregroundStyle(MFTheme.muted) }; Spacer(); Button { Task { await load() } } label: { Image(systemName: "arrow.clockwise").padding(11).background(MFTheme.card, in: Circle()) } }
                MFCard {
                    Text("Общий баланс").foregroundStyle(MFTheme.muted)
                    if grouped.isEmpty { Text("—").font(.system(size: 34, weight: .bold, design: .rounded)) }
                    ForEach(grouped, id: \.0) { currency, amount in Text(MoneyFormat.string(amount, currency: currency)).font(.system(size: 30, weight: .bold, design: .rounded)).minimumScaleFactor(0.65).lineLimit(1) }
                    Text("По всем кошелькам · по валютам отдельно").font(.caption).foregroundStyle(MFTheme.muted)
                }.overlay(alignment: .topTrailing) { Image(systemName: "chart.line.uptrend.xyaxis").font(.title2).foregroundStyle(MFTheme.accent).padding(16) }
                if let error { Text(error).font(.footnote).foregroundStyle(.red) }
                HStack { Text("Мои кошельки").font(.title3.bold()); Spacer(); NavigationLink("Все", destination: WalletsView()) }
                if wallets.isEmpty { empty("creditcard", "Добавь первый кошелёк") }
                ForEach(wallets.prefix(3)) { wallet in walletCard(wallet) }
                HStack { Text("Последние операции").font(.title3.bold()); Spacer(); NavigationLink("Все", destination: TransactionsView()) }
                if transactions.isEmpty { empty("list.bullet.rectangle", "Операции пока не добавлены") }
                ForEach(transactions.prefix(5)) { tx in TransactionRow(transaction: tx) }
            }.padding(18) }
            .background(MFTheme.background).toolbar(.hidden, for: .navigationBar).refreshable { await load() }.task { await load() }
        }
    }
    private func load() async { loading = true; defer { loading = false }; do { async let w: [Wallet] = api.get("api/wallets"); async let t: [FinanceTransaction] = api.get("api/transactions?limit=20"); wallets = try await w; transactions = try await t; error = nil } catch { self.error = error.localizedDescription } }
    private func walletCard(_ wallet: Wallet) -> some View { MFCard { HStack { Image(systemName: "creditcard.fill").foregroundStyle(MFTheme.accent).font(.title2).frame(width: 44, height: 44).background(MFTheme.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 12)); VStack(alignment: .leading, spacing: 5) { Text(wallet.name).bold(); Text(wallet.currency).font(.caption).foregroundStyle(MFTheme.muted) }; Spacer(); Text(MoneyFormat.string(wallet.balanceMinor, currency: wallet.currency)).font(.headline).monospacedDigit() } } }
    private func empty(_ icon: String, _ text: String) -> some View { MFCard { Label(text, systemImage: icon).foregroundStyle(MFTheme.muted) } }
}

struct TransactionRow: View {
    let transaction: FinanceTransaction
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: transaction.type == "INCOME" ? "arrow.down.left" : "arrow.up.right").font(.headline).foregroundStyle(transaction.type == "INCOME" ? MFTheme.accent : .orange).frame(width: 42, height: 42).background((transaction.type == "INCOME" ? MFTheme.accent : Color.orange).opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 4) { Text(transaction.category).bold(); Text(transaction.description ?? transaction.walletName ?? "Операция").font(.caption).foregroundStyle(MFTheme.muted).lineLimit(1) }
            Spacer(minLength: 8)
            Text("\(transaction.type == "INCOME" ? "+" : "−")\(MoneyFormat.string(transaction.amountMinor, currency: transaction.currency ?? "EUR"))").font(.subheadline.bold()).foregroundStyle(transaction.type == "INCOME" ? MFTheme.accent : .primary).lineLimit(1).minimumScaleFactor(0.7)
        }.padding(.vertical, 5)
    }
}
