import Foundation

struct SessionResponse: Decodable { let token: String; let userId: Int64; let email: String }
struct Wallet: Codable, Identifiable { let id: Int64; let name: String; let currency: String; let balanceMinor: Int64; var createdAt: String? }
struct FinanceTransaction: Codable, Identifiable { let id: Int64; let walletId: Int64; var walletName: String?; var currency: String?; let type: String; let amountMinor: Int64; let category: String; var description: String?; let occurredAt: String }
struct Goal: Codable, Identifiable { let id: Int64; let name: String; let currency: String; let targetMinor: Int64; let savedMinor: Int64; var targetDate: String? }
struct RecurringRule: Codable, Identifiable { let id: Int64; let walletId: Int64; var walletName: String?; var currency: String?; let type: String; let amountMinor: Int64; let category: String; var description: String?; let frequency: String; let nextRunAt: String; let active: Bool }
struct Dashboard: Decodable { var balancesByCurrency: [String: Int64]?; var monthlyByCurrency: [String: MonthlyTotals]? }
struct MonthlyTotals: Decodable { var incomeMinor: Int64?; var expenseMinor: Int64? }
struct APIError: Decodable { var message: String?; var error: String? }
struct EmptyResponse: Decodable { var deleted: Bool?; var message: String? }

struct CreateWallet: Encodable { let name: String; let currency: String; let initialBalanceMinor: Int64 }
struct CreateTransaction: Encodable { let walletId: Int64; let type: String; let amountMinor: Int64; let category: String; let description: String; let occurredAt: String }
struct CreateGoal: Encodable { let name: String; let currency: String; let targetMinor: Int64; let targetDate: String? }
struct GoalContribution: Encodable { let walletId: Int64; let amountMinor: Int64; let note: String }
struct CreateRecurring: Encodable { let walletId: Int64; let type: String; let amountMinor: Int64; let category: String; let description: String; let frequency: String; let nextRunAt: String }
struct PriceRequest: Encodable { let unitPriceMinor: Int64; let quantity: Int; let discountPercent: Double; let fixedDiscountMinor: Int64; let taxPercent: Double; let feeMinor: Int64 }
struct PriceResult: Decodable { let subtotalMinor: Int64; let discountMinor: Int64; let taxMinor: Int64; let feeMinor: Int64; let totalMinor: Int64; let savedPercent: Double }
