package com.moneyflow.app

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// Emulator -> host machine. For a physical phone, replace with http://YOUR_LAN_IP:8080/.
private const val BASE_URL = "http://10.0.2.2:8080/"

class Session(context: Context) {
    private val prefs = context.getSharedPreferences("moneyflow_session", Context.MODE_PRIVATE)
    var token: String?
        get() = prefs.getString("token", null)
        set(value) { prefs.edit().putString("token", value).apply() }
    var email: String?
        get() = prefs.getString("email", null)
        set(value) { prefs.edit().putString("email", value).apply() }
}

data class Credentials(val email: String, val password: String)
data class AuthResponse(val token: String, val userId: Long, val email: String)
data class Wallet(val id: Long, val name: String, val currency: String, val balanceMinor: Long)
data class WalletCreate(val name: String, val currency: String = "EUR", val initialBalanceMinor: Long = 0)
data class TransactionItem(val id: Long, val walletId: Long? = null, val walletName: String? = null, val currency: String = "EUR", val type: String, val amountMinor: Long, val category: String, val description: String, val occurredAt: String)
data class TransactionCreate(val walletId: Long, val type: String, val amountMinor: Long, val category: String, val description: String, val occurredAt: String? = null)
data class CurrencyBalance(val currency: String, val balanceMinor: Long)
data class MonthlyCurrencySummary(val currency: String, val incomeMinor: Long, val expenseMinor: Long)
data class Dashboard(val balanceMinor: Long, val incomeThisMonthMinor: Long, val expenseThisMonthMinor: Long, val recentTransactions: List<TransactionItem>, val balancesByCurrency: List<CurrencyBalance> = emptyList(), val monthlyByCurrency: List<MonthlyCurrencySummary> = emptyList())
data class PriceRequest(val unitPriceMinor: Long, val quantity: Double, val discountPercent: Double, val fixedDiscountMinor: Long, val taxPercent: Double, val feeMinor: Long)
data class PriceResult(val subtotalMinor: Long, val discountMinor: Long, val taxMinor: Long, val feeMinor: Long, val totalMinor: Long, val savedPercent: Double)
data class TransferRequest(val fromWalletId: Long, val toWalletId: Long, val amountMinor: Long, val description: String)
data class Budget(val id: Long, val name: String, val category: String, val limitMinor: Long, val month: String, val spentMinor: Long = 0, val currency: String = "EUR")
data class BudgetCreate(val name: String, val category: String, val limitMinor: Long, val month: String, val currency: String = "EUR")
data class TransferItem(val id: Long, val fromWalletId: Long, val fromWalletName: String, val toWalletId: Long, val toWalletName: String, val amountMinor: Long, val currency: String, val description: String, val createdAt: String)
data class SavingsGoal(val id: Long, val name: String, val currency: String, val targetMinor: Long, val savedMinor: Long, val targetDate: String? = null, val progressPercent: Double = 0.0, val remainingMinor: Long = 0)
data class SavingsGoalCreate(val name: String, val currency: String = "EUR", val targetMinor: Long, val targetDate: String? = null)
data class GoalContribution(val walletId: Long, val amountMinor: Long, val note: String = "")
data class GoalContributionResult(val id: Long, val goalId: Long, val walletId: Long, val amountMinor: Long, val note: String, val savedMinor: Long, val targetMinor: Long)

interface MoneyFlowApi {
    @POST("api/auth/register") suspend fun register(@Body body: Credentials): AuthResponse
    @POST("api/auth/login") suspend fun login(@Body body: Credentials): AuthResponse
    @GET("api/dashboard") suspend fun dashboard(): Dashboard
    @GET("api/wallets") suspend fun wallets(): List<Wallet>
    @POST("api/wallets") suspend fun createWallet(@Body body: WalletCreate): Wallet
    @GET("api/transactions") suspend fun transactions(@Query("limit") limit: Int = 100): List<TransactionItem>
    @POST("api/transactions") suspend fun createTransaction(@Body body: TransactionCreate): TransactionItem
    @POST("api/transfers") suspend fun transfer(@Body body: TransferRequest): Map<String, Any>
    @GET("api/transfers") suspend fun transfers(@Query("limit") limit: Int = 100): List<TransferItem>
    @GET("api/budgets") suspend fun budgets(): List<Budget>
    @POST("api/budgets") suspend fun createBudget(@Body body: BudgetCreate): Budget
    @POST("api/calculator/price") suspend fun calculatePrice(@Body body: PriceRequest): PriceResult
    @GET("api/goals") suspend fun goals(): List<SavingsGoal>
    @POST("api/goals") suspend fun createGoal(@Body body: SavingsGoalCreate): SavingsGoal
    @POST("api/goals/{goalId}/contributions") suspend fun contributeToGoal(@Path("goalId") goalId: Long, @Body body: GoalContribution): GoalContributionResult
}

object ApiFactory {
    fun create(session: Session): MoneyFlowApi {
        val auth = Interceptor { chain ->
            val builder = chain.request().newBuilder()
            session.token?.let { builder.header("Authorization", "Bearer $it") }
            chain.proceed(builder.build())
        }
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(auth).addInterceptor(logger)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
        return Retrofit.Builder().baseUrl(BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(MoneyFlowApi::class.java)
    }
}
