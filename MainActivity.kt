package com.moneyflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.NumberFormat
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

private val Ink = Color(0xFF20283A)
private val Muted = Color(0xFF687386)
private val Canvas = Color(0xFFF5F6FA)
private val Accent = Color(0xFF5067A5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = Session(this)
        val api = ApiFactory.create(session)
        setContent { MoneyFlowApp(session, api) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyFlowApp(session: Session, api: MoneyFlowApi) {
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(!session.token.isNullOrBlank()) }
    var registerMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(session.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }
    var wallets by remember { mutableStateOf<List<Wallet>>(emptyList()) }
    var transactions by remember { mutableStateOf<List<TransactionItem>>(emptyList()) }
    var budgets by remember { mutableStateOf<List<Budget>>(emptyList()) }
    var goals by remember { mutableStateOf<List<SavingsGoal>>(emptyList()) }

    suspend fun refresh() {
        dashboard = api.dashboard()
        wallets = api.wallets()
        transactions = api.transactions(100)
        budgets = api.budgets()
        goals = api.goals()
    }
    fun runTask(block: suspend () -> Unit) {
        scope.launch {
            loading = true; message = ""
            try { block() } catch (e: Exception) { message = readableError(e) } finally { loading = false }
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Canvas, surface = Color.White, onBackground = Ink, onSurface = Ink)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
            if (!loggedIn) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("MoneyFlow", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(8.dp)); Text("Твои деньги — под контролем", color = Muted, fontSize = 16.sp)
                    Spacer(Modifier.height(30.dp))
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(if (registerMode) "Создать аккаунт" else "Войти", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(email, { email = it }, label = { Text("Электронная почта") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(password, { password = it }, label = { Text("Пароль (минимум 10 символов)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                            Button(onClick = {
                                if (!email.contains("@") || password.length < 10) { message = "Введите корректную почту и пароль от 10 символов"; return@Button }
                                runTask {
                                    val result = if (registerMode) api.register(Credentials(email.trim(), password)) else api.login(Credentials(email.trim(), password))
                                    session.token = result.token; session.email = result.email; loggedIn = true; refresh()
                                }
                            }, enabled = !loading, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (loading) "Подождите…" else if (registerMode) "Зарегистрироваться" else "Войти") }
                            TextButton(onClick = { registerMode = !registerMode; message = "" }, modifier = Modifier.fillMaxWidth()) { Text(if (registerMode) "Уже есть аккаунт? Войти" else "Создать новый аккаунт") }
                        }
                    }
                    Text("Данные хранятся на твоём сервере. Не используй простой пароль.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                }
            } else {
                Scaffold(containerColor = Canvas, topBar = {
                    TopAppBar(title = { Column { Text("MoneyFlow", fontWeight = FontWeight.Bold); Text(session.email.orEmpty(), fontSize = 12.sp, color = Muted) } }, actions = {
                        IconButton(onClick = { session.token = null; loggedIn = false; dashboard = null }) { Icon(Icons.Default.Logout, "Выйти") }
                    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas))
                }, bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(selectedTab == 0, { selectedTab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Обзор") })
                        NavigationBarItem(selectedTab == 1, { selectedTab = 1 }, icon = { Icon(Icons.Default.AccountBalanceWallet, null) }, label = { Text("Кошельки") })
                        NavigationBarItem(selectedTab == 2, { selectedTab = 2 }, icon = { Icon(Icons.Default.History, null) }, label = { Text("Операции") })
                        NavigationBarItem(selectedTab == 3, { selectedTab = 3 }, icon = { Icon(Icons.Default.Calculate, null) }, label = { Text("Калькулятор") })
                        NavigationBarItem(selectedTab == 4, { selectedTab = 4 }, icon = { Icon(Icons.Default.Savings, null) }, label = { Text("Бюджеты") })
                        NavigationBarItem(selectedTab == 5, { selectedTab = 5 }, icon = { Icon(Icons.Default.Savings, null) }, label = { Text("Цели") })
                    }
                }) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize()) {
                        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        when (selectedTab) {
                            0 -> DashboardScreen(dashboard, transactions, onRefresh = { runTask { refresh() } })
                            1 -> WalletsScreen(wallets, onCreate = { name, balance -> runTask { api.createWallet(WalletCreate(name, "EUR", balance)); refresh() } }, onTransfer = { from, to, amount, desc -> runTask { api.transfer(TransferRequest(from, to, amount, desc)); refresh() } }, onRefresh = { runTask { refresh() } })
                            2 -> TransactionsScreen(wallets, transactions, onCreate = { wallet, type, amount, category, desc -> runTask { api.createTransaction(TransactionCreate(wallet, type, amount, category, desc)); refresh() } }, onRefresh = { runTask { refresh() } })
                            3 -> CalculatorScreen(onCalculate = { req, callback -> runTask { callback(api.calculatePrice(req)) } })
                            4 -> BudgetsScreen(budgets, onCreate = { name, category, limit, month -> runTask { api.createBudget(BudgetCreate(name, category, limit, month)); refresh() } }, onRefresh = { runTask { refresh() } })
                            5 -> GoalsScreen(goals, wallets, onCreate = { name, target -> runTask { api.createGoal(SavingsGoalCreate(name, "EUR", target)); refresh() } }, onContribute = { goalId, walletId, amount, note -> runTask { api.contributeToGoal(goalId, GoalContribution(walletId, amount, note)); refresh() } }, onRefresh = { runTask { refresh() } })
                        }
                    }
                }
                LaunchedEffect(Unit) { runTask { refresh() } }
            }
        }
    }
}

@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun DashboardScreen(data: Dashboard?, transactions: List<TransactionItem>, onRefresh: () -> Unit) {
    PageColumn {
        Text("Финансовый обзор", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Ink), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Text("Баланс в EUR", color = Color.LightGray)
                Text(money(data?.balanceMinor ?: 0), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Доходы EUR за месяц", color = Color.LightGray, fontSize = 12.sp); Text(money(data?.incomeThisMonthMinor ?: 0), color = Color(0xFF9FE3BD), fontWeight = FontWeight.SemiBold) }
                    Column { Text("Расходы EUR за месяц", color = Color.LightGray, fontSize = 12.sp); Text(money(data?.expenseThisMonthMinor ?: 0), color = Color(0xFFFFB3A8), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        data?.balancesByCurrency?.filter { it.currency != "EUR" }?.forEach { balance ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Баланс ${balance.currency}", color = Muted)
                    Text(money(balance.balanceMinor, balance.currency), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Последние операции", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onRefresh) { Text("Обновить") }
        }
        if (transactions.isEmpty()) EmptyCard("Пока нет операций", "Добавь доход или расход во вкладке «Операции».")
        transactions.take(5).forEach { TransactionRow(it) }
    }
}

@Composable
private fun WalletsScreen(wallets: List<Wallet>, onCreate: (String, Long) -> Unit, onTransfer: (Long, Long, Long, String) -> Unit, onRefresh: () -> Unit) {
    var name by remember { mutableStateOf("") }; var balance by remember { mutableStateOf("0") }
    var fromId by remember(wallets) { mutableLongStateOf(wallets.firstOrNull()?.id ?: 0L) }
    var toId by remember(wallets) { mutableLongStateOf(wallets.getOrNull(1)?.id ?: 0L) }
    var transferAmount by remember { mutableStateOf("") }; var transferDescription by remember { mutableStateOf("Перевод между кошельками") }
    var fromMenu by remember { mutableStateOf(false) }; var toMenu by remember { mutableStateOf(false) }
    PageColumn {
        Text("Кошельки", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        wallets.forEach { w -> Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFE9EDF8), modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AccountBalanceWallet, null, tint = Accent) } }
                Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(w.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp); Text(w.currency, color = Muted, fontSize = 12.sp) }
                Text(money(w.balanceMinor, w.currency), fontWeight = FontWeight.Bold)
            }
        } }
        Text("Добавить кошелёк", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(name, { name = it }, label = { Text("Название (например, Наличные)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(balance, { balance = it }, label = { Text("Начальный баланс, EUR") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { val cents = parseMinor(balance); if (name.isNotBlank() && cents != null && cents >= 0) { onCreate(name.trim(), cents); name = ""; balance = "0" } }, modifier = Modifier.fillMaxWidth()) { Text("Создать кошелёк") }
        Text("Перевод между кошельками", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { fromMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = wallets.size >= 2) { Text("Из: ${wallets.firstOrNull { it.id == fromId }?.name ?: "Кошелёк"}") }
            DropdownMenu(expanded = fromMenu, onDismissRequest = { fromMenu = false }) { wallets.forEach { w -> DropdownMenuItem(text = { Text(w.name) }, onClick = { fromId = w.id; fromMenu = false }) } }
        }
        Box {
            OutlinedButton(onClick = { toMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = wallets.size >= 2) { Text("В: ${wallets.firstOrNull { it.id == toId }?.name ?: "Кошелёк"}") }
            DropdownMenu(expanded = toMenu, onDismissRequest = { toMenu = false }) { wallets.forEach { w -> DropdownMenuItem(text = { Text(w.name) }, onClick = { toId = w.id; toMenu = false }) } }
        }
        OutlinedTextField(transferAmount, { transferAmount = it }, label = { Text("Сумма перевода, EUR") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(transferDescription, { transferDescription = it }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { val cents = parseMinor(transferAmount); if (fromId > 0 && toId > 0 && fromId != toId && cents != null && cents > 0) { onTransfer(fromId, toId, cents, transferDescription.trim()); transferAmount = "" } }, enabled = wallets.size >= 2, modifier = Modifier.fillMaxWidth()) { Text("Перевести") }
        Text("Переводы доступны между кошельками одной валюты. Сервер проверяет баланс и выполняет перевод атомарно.", color = Muted, fontSize = 12.sp)
        TextButton(onClick = onRefresh) { Text("Обновить список") }
    }
}

@Composable
private fun TransactionsScreen(wallets: List<Wallet>, transactions: List<TransactionItem>, onCreate: (Long, String, Long, String, String) -> Unit, onRefresh: () -> Unit) {
    var selectedWallet by remember(wallets) { mutableLongStateOf(wallets.firstOrNull()?.id ?: 0L) }
    var walletMenu by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("EXPENSE") }; var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }; var description by remember { mutableStateOf("") }
    PageColumn {
        Text("Операции", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Новая операция", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(onClick = { walletMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = wallets.isNotEmpty()) { Text(wallets.firstOrNull { it.id == selectedWallet }?.name ?: "Выбрать кошелёк") }
                    DropdownMenu(expanded = walletMenu, onDismissRequest = { walletMenu = false }) { wallets.forEach { w -> DropdownMenuItem(text = { Text(w.name) }, onClick = { selectedWallet = w.id; walletMenu = false }) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "EXPENSE", onClick = { type = "EXPENSE" }, label = { Text("Расход") })
                    FilterChip(selected = type == "INCOME", onClick = { type = "INCOME" }, label = { Text("Доход") })
                }
                OutlinedTextField(amount, { amount = it }, label = { Text("Сумма, EUR") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Категория") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Описание (необязательно)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { val cents = parseMinor(amount); if (selectedWallet > 0 && cents != null && cents > 0 && category.isNotBlank()) { onCreate(selectedWallet, type, cents, category.trim(), description.trim()); amount = ""; description = "" } }, enabled = wallets.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Сохранить операцию") }
                Text("Расходы не могут превышать доступный баланс кошелька.", color = Muted, fontSize = 12.sp)
            }
        }
        Text("История", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (transactions.isEmpty()) EmptyCard("История пуста", "Создай первую операцию.") else transactions.forEach { TransactionRow(it) }
        TextButton(onClick = onRefresh) { Text("Обновить историю") }
    }
}

@Composable
private fun GoalsScreen(goals: List<SavingsGoal>, wallets: List<Wallet>, onCreate: (String, Long) -> Unit,
                       onContribute: (Long, Long, Long, String) -> Unit, onRefresh: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var contribution by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("Пополнение цели") }
    var selectedGoalId by remember(goals) { mutableLongStateOf(goals.firstOrNull()?.id ?: 0L) }
    var selectedWalletId by remember(wallets) { mutableLongStateOf(wallets.firstOrNull { it.currency == "EUR" }?.id ?: 0L) }
    var goalMenu by remember { mutableStateOf(false) }
    var walletMenu by remember { mutableStateOf(false) }
    PageColumn {
        Text("Финансовые цели", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Откладывай деньги на крупные покупки и резерв. Взнос уменьшает баланс выбранного кошелька и увеличивает прогресс цели.", color = Muted, fontSize = 13.sp)
        if (goals.isEmpty()) EmptyCard("Целей пока нет", "Создай цель — например, резерв на 3 месяца расходов.")
        goals.forEach { goal ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("${goal.progressPercent.toInt().coerceIn(0, 100)}%", color = Accent, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(progress = { (goal.savedMinor.toDouble() / goal.targetMinor.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(money(goal.savedMinor, goal.currency), fontWeight = FontWeight.SemiBold)
                        Text("из ${money(goal.targetMinor, goal.currency)}", color = Muted)
                    }
                    Text(if (goal.remainingMinor > 0) "Осталось: ${money(goal.remainingMinor, goal.currency)}" else "Цель достигнута 🎉", color = if (goal.remainingMinor > 0) Muted else Color(0xFF287A50), fontSize = 12.sp)
                }
            }
        }
        Text("Создать цель", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(name, { name = it }, label = { Text("Название цели") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(target, { target = it }, label = { Text("Целевая сумма, EUR") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { val minor = parseMinor(target); if (name.isNotBlank() && minor != null && minor > 0) { onCreate(name.trim(), minor); name = ""; target = "" } }, modifier = Modifier.fillMaxWidth()) { Text("Создать цель") }
        Divider()
        Text("Пополнить цель", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { goalMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = goals.isNotEmpty()) { Text(goals.firstOrNull { it.id == selectedGoalId }?.name ?: "Выбрать цель") }
            DropdownMenu(expanded = goalMenu, onDismissRequest = { goalMenu = false }) { goals.forEach { goal -> DropdownMenuItem(text = { Text(goal.name) }, onClick = { selectedGoalId = goal.id; goalMenu = false; selectedWalletId = wallets.firstOrNull { it.currency == goal.currency }?.id ?: 0L }) } }
        }
        Box {
            OutlinedButton(onClick = { walletMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = wallets.any { it.currency == goals.firstOrNull { g -> g.id == selectedGoalId }?.currency }) { Text(wallets.firstOrNull { it.id == selectedWalletId }?.name ?: "Выбрать кошелёк подходящей валюты") }
            DropdownMenu(expanded = walletMenu, onDismissRequest = { walletMenu = false }) { wallets.filter { it.currency == goals.firstOrNull { g -> g.id == selectedGoalId }?.currency }.forEach { wallet -> DropdownMenuItem(text = { Text("${wallet.name} · ${wallet.currency}") }, onClick = { selectedWalletId = wallet.id; walletMenu = false }) } }
        }
        OutlinedTextField(contribution, { contribution = it }, label = { Text("Сумма пополнения, EUR") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(note, { note = it }, label = { Text("Заметка") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { val amount = parseMinor(contribution); if (selectedGoalId > 0 && selectedWalletId > 0 && amount != null && amount > 0) { onContribute(selectedGoalId, selectedWalletId, amount, note.trim()); contribution = "" } }, enabled = goals.isNotEmpty() && wallets.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Перевести в накопления") }
        Text("Сервер проверяет валюту, остаток кошелька и то, чтобы взнос не превышал оставшуюся сумму цели.", color = Muted, fontSize = 12.sp)
        TextButton(onClick = onRefresh) { Text("Обновить цели") }
    }
}

@Composable
private fun TransactionRow(t: TransactionItem) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = if (t.type == "INCOME") Color(0xFFE1F4EA) else Color(0xFFFFEAE6), modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Text(if (t.type == "INCOME") "+" else "−", color = if (t.type == "INCOME") Color(0xFF287A50) else Color(0xFFB24D3E), fontSize = 23.sp, fontWeight = FontWeight.Bold) } }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(t.category, fontWeight = FontWeight.SemiBold)
                Text(t.description.ifBlank { t.walletName ?: t.type.lowercase().replaceFirstChar { it.uppercase() } }, color = Muted, fontSize = 12.sp)
            }
            Text((if (t.type == "INCOME") "+" else "−") + money(t.amountMinor, t.currency), color = if (t.type == "INCOME") Color(0xFF287A50) else Ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CalculatorScreen(onCalculate: (PriceRequest, (PriceResult) -> Unit) -> Unit) {
    var price by remember { mutableStateOf("100") }; var quantity by remember { mutableStateOf("1") }
    var discount by remember { mutableStateOf("10") }; var fixedDiscount by remember { mutableStateOf("0") }
    var tax by remember { mutableStateOf("0") }; var fee by remember { mutableStateOf("0") }
    var result by remember { mutableStateOf<PriceResult?>(null) }; var error by remember { mutableStateOf("") }
    PageColumn {
        Text("Калькулятор цены", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Скидка, налог, комиссия и итоговая стоимость", color = Muted)
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(price, { price = it }, label = { Text("Цена за единицу, EUR") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Количество") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(discount, { discount = it }, label = { Text("Скидка, %") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(fixedDiscount, { fixedDiscount = it }, label = { Text("Дополнительная скидка, EUR") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(tax, { tax = it }, label = { Text("Налог, %") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(fee, { fee = it }, label = { Text("Комиссия, EUR") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    val p = price.toDoubleOrNull(); val q = quantity.toDoubleOrNull(); val d = discount.toDoubleOrNull(); val fd = fixedDiscount.toDoubleOrNull(); val tx = tax.toDoubleOrNull(); val f = fee.toDoubleOrNull()
                    if (p == null || q == null || d == null || fd == null || tx == null || f == null || p <= 0 || q <= 0 || d !in 0.0..100.0 || tx !in 0.0..100.0 || fd < 0 || f < 0) { error = "Проверь значения: цена и количество > 0, проценты 0–100."; result = null }
                    else { error = ""; onCalculate(PriceRequest(parseMinor(price) ?: 0, q, d, parseMinor(fixedDiscount) ?: 0, tx, parseMinor(fee) ?: 0)) { result = it } }
                }, modifier = Modifier.fillMaxWidth()) { Text("Рассчитать") }
            }
        }
        result?.let { r ->
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Ink), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Итоговая стоимость", color = Color.LightGray)
                    Text(money(r.totalMinor), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    HorizontalDivider(color = Color.DarkGray)
                    SummaryLine("Стоимость до скидки", r.subtotalMinor)
                    SummaryLine("Скидка", -r.discountMinor, Color(0xFF9FE3BD))
                    SummaryLine("Налог", r.taxMinor)
                    SummaryLine("Комиссия", r.feeMinor)
                    Text("Экономия: ${"%.2f".format(Locale.US, r.savedPercent)}%", color = Color(0xFF9FE3BD), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, amount: Long, color: Color = Color.White) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Color.LightGray); Text(money(amount), color = color, fontWeight = FontWeight.Medium) }
}

@Composable
private fun BudgetsScreen(budgets: List<Budget>, onCreate: (String, String, Long, String) -> Unit, onRefresh: () -> Unit) {
    var name by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Food") }; var limit by remember { mutableStateOf("300") }
    var month by remember { mutableStateOf(java.time.YearMonth.now().toString()) }
    PageColumn {
        Text("Бюджеты", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        if (budgets.isEmpty()) EmptyCard("Бюджетов пока нет", "Задай лимит на месяц и следи за расходами.")
        budgets.forEach { b ->
            val progress = (b.spentMinor.toDouble() / b.limitMinor.toDouble()).coerceIn(0.0, 1.0)
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(b.name, fontWeight = FontWeight.SemiBold); Text(b.month, color = Muted, fontSize = 12.sp) }
                    Text("${money(b.spentMinor, b.currency)} из ${money(b.limitMinor, b.currency)}", fontWeight = FontWeight.Medium)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = if (progress >= 1) Color(0xFFB24D3E) else Accent)
                    Text(if (b.spentMinor > b.limitMinor) "Превышение: ${money(b.spentMinor - b.limitMinor, b.currency)}" else "Осталось: ${money(b.limitMinor - b.spentMinor, b.currency)}", color = if (progress >= 1) Color(0xFFB24D3E) else Muted, fontSize = 12.sp)
                }
            }
        }
        Text("Новый бюджет", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(name, { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(category, { category = it }, label = { Text("Категория (должна совпадать с операциями)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(limit, { limit = it }, label = { Text("Лимит, EUR") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(month, { month = it }, label = { Text("Месяц YYYY-MM") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { val cents = parseMinor(limit); if (name.isNotBlank() && category.isNotBlank() && cents != null && cents > 0 && month.matches(Regex("\\d{4}-(0[1-9]|1[0-2])"))) { onCreate(name.trim(), category.trim(), cents, month); name = "" } }, modifier = Modifier.fillMaxWidth()) { Text("Создать бюджет") }
        TextButton(onClick = onRefresh) { Text("Обновить бюджеты") }
    }
}

@Composable
private fun EmptyCard(title: String, detail: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(4.dp)); Text(detail, color = Muted, fontSize = 13.sp) }
    }
}

private fun parseMinor(value: String): Long? = try {
    value.trim().replace(',', '.').toBigDecimalOrNull()?.multiply(java.math.BigDecimal(100))?.setScale(0, java.math.RoundingMode.HALF_UP)?.longValueExact()
} catch (_: ArithmeticException) { null }
private fun money(minor: Long, currencyCode: String = "EUR"): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)
    try { formatter.currency = Currency.getInstance(currencyCode) } catch (_: IllegalArgumentException) { formatter.currency = Currency.getInstance("EUR") }
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.format(BigDecimal.valueOf(minor, 2))
}
private fun readableError(e: Exception): String = when (e) {
    is HttpException -> when (e.code()) {
        400 -> "Некорректные данные. Проверь поля и суммы."
        401 -> "Сессия истекла или данные для входа неверны. Войди снова."
        404 -> "Запись не найдена. Обнови данные."
        409 -> "Операция невозможна: проверь баланс или дубликаты."
        else -> "Ошибка сервера (${e.code()}). Попробуй ещё раз."
    }
    else -> "Не удалось подключиться к серверу. Проверь соединение и адрес API."
}
