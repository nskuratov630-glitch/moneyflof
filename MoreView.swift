import SwiftUI

struct MoreView: View {
    @EnvironmentObject private var api: APIClient
    @State private var showingSettings = false
    @State private var showingRecurring = false
    @State private var showingCalculator = false
    @State private var showingReport = false
    var body: some View {
        NavigationStack { List {
            Section("Инструменты") {
                Button { showingRecurring = true } label: { Label("Регулярные операции", systemImage: "calendar.badge.clock") }
                Button { showingCalculator = true } label: { Label("Калькулятор цены", systemImage: "function") }
                Button { showingReport = true } label: { Label("Отчёт за месяц", systemImage: "chart.pie.fill") }
            }
            Section("Приложение") {
                Button { showingSettings = true } label: { Label("Адрес сервера", systemImage: "server.rack") }
                LabeledContent("Версия API", value: "Spring Boot REST")
                Button(role: .destructive) { api.logout() } label: { Label("Выйти из аккаунта", systemImage: "rectangle.portrait.and.arrow.right") }
            }
            Section { Text("MoneyFlow — личный учёт финансов. Все суммы берутся с сервера; приложение не подключается к банкам и не выполняет реальные платежи.").font(.caption).foregroundStyle(.secondary) }
        }.scrollContentBackground(.hidden).background(MFTheme.background).navigationTitle("Ещё")
            .sheet(isPresented: $showingSettings) { APISettingsView() }
            .sheet(isPresented: $showingRecurring) { RecurringView() }
            .sheet(isPresented: $showingCalculator) { PriceCalculatorView() }
            .sheet(isPresented: $showingReport) { MonthlyReportView() }
        }
    }
}

struct MonthlyReportView: View {
    @EnvironmentObject private var api: APIClient
    @State private var month = String(Date.now.formatted(.iso8601.year().month().date).prefix(7))
    @State private var rows: [ReportRow] = []
    @State private var error: String?
    var body: some View { NavigationStack { VStack(alignment: .leading, spacing: 16) { TextField("YYYY-MM", text: $month).textInputAutocapitalization(.never).padding(12).background(MFTheme.card, in: RoundedRectangle(cornerRadius: 12)); Button("Загрузить отчёт") { Task { await load() } }.buttonStyle(.borderedProminent); if let error { Text(error).foregroundStyle(.red) }; ForEach(rows) { row in MFCard { HStack { Text(row.currency).font(.headline); Spacer() }; HStack { VStack(alignment: .leading) { Text("Доходы").font(.caption).foregroundStyle(MFTheme.muted); Text(MoneyFormat.string(row.incomeMinor ?? 0, currency: row.currency)).foregroundStyle(MFTheme.accent) }; Spacer(); VStack(alignment: .trailing) { Text("Расходы").font(.caption).foregroundStyle(MFTheme.muted); Text(MoneyFormat.string(row.expenseMinor ?? 0, currency: row.currency)).foregroundStyle(.orange) } } } }; Spacer() }.padding(16).background(MFTheme.background).navigationTitle("Отчёт за месяц").task { await load() } } }
    private func load() async { do { let response: MonthlyReportResponse = try await api.get("api/reports/monthly?month=\(month)"); rows = response.totalsByCurrency?.map { ReportRow(currency: $0.currency, incomeMinor: $0.incomeMinor, expenseMinor: $0.expenseMinor) }.sorted { $0.currency < $1.currency } ?? []; error = nil } catch { self.error = error.localizedDescription } }
}
private struct MonthlyReportResponse: Decodable { let totalsByCurrency: [CurrencyReport]? }
private struct CurrencyReport: Decodable { let currency: String; let incomeMinor: Int64?; let expenseMinor: Int64? }
private struct ReportRow: Identifiable { var id: String { currency }; let currency: String; let incomeMinor: Int64?; let expenseMinor: Int64? }
