# MoneyFlow — Android + Spring Boot REST API + PostgreSQL

MoneyFlow is a personal-finance starter project with a native Android UI (Kotlin/Jetpack Compose), a stateless REST API (Java 21 / Spring Boot), and persistent PostgreSQL storage. It is designed for tracking user-entered finances; it does **not** move real money or connect to a bank.

## Features

- Email/password registration and login, BCrypt password hashing, signed JWT access tokens.
- Per-user wallet isolation, ISO currency code validation, non-negative balances.
- Income and expense transactions with server-side balance updates and insufficient-funds checks.
- Atomic same-currency wallet transfers; transfers between different currencies are intentionally rejected without an FX quote.
- Monthly budgets and category-based spending totals.
- Savings goals with contribution history, progress percentage and atomic wallet debits.
- Recurring income/expense rules (weekly, monthly, yearly), pause/resume controls, explicit due-run execution and idempotency/audit constraints.
- Safe deletion of manually created transactions with balance reversal; recurring-generated ledger entries are protected.
- Price calculator: quantity, percentage discount, fixed discount, tax, fee, rounding and overflow checks.
- Dashboard, transaction history filters, category summaries, monthly report and CSV export.
- Consistent JSON API errors, Bean Validation, PostgreSQL constraints and Flyway migrations.
- Docker Compose setup and a smoke-test script.
- Release Android manifest disallows cleartext HTTP; debug manifest allows HTTP for local development only.

## Requirements

- Docker Desktop / Docker Engine with Compose.
- Android Studio stable with Android SDK 35.
- JDK 21 if you want to run the backend outside Docker.
- `curl` and `python3` for the smoke test.

## 1. Configure local secrets

From the project root, create your local environment file:

```bash
cp .env.example .env
```

Edit `.env`. Use a long random database password and a random JWT secret (at least 32 bytes). Example secret generation on Linux/macOS:

```bash
openssl rand -base64 48
```

Do not commit `.env`. The Compose defaults are only for quick local development and are not suitable for a public server.

## 2. Start API and database

```bash
docker compose up --build -d
```

Check the service:

```bash
curl -fsS http://localhost:8080/actuator/health
```

Stop the services:

```bash
docker compose down
```

The named `moneyflow_pg` volume persists database data. `docker compose down -v` deletes that volume and all stored records; do not use `-v` unless you intentionally want to reset the database.

## 3. Open Android client

Open the `android` directory in Android Studio and allow Gradle sync. Start an Android emulator and run the `app` configuration. The debug build uses `http://10.0.2.2:8080/`, the Android emulator alias for the host machine.

For a physical phone, change `BASE_URL` in `android/app/src/main/java/com/moneyflow/app/Api.kt` to your computer's LAN address, for example `http://192.168.1.20:8080/`; allow the port in your local firewall and keep the phone and computer on the same trusted network. Do not expose the development HTTP endpoint to the public internet. Production/release builds should use HTTPS and a real TLS certificate.

## 4. Smoke test the API

With Docker running:

```bash
./scripts/smoke-test.sh
```

To test another local endpoint:

```bash
BASE_URL=http://192.168.1.20:8080 ./scripts/smoke-test.sh
```

The script creates a disposable test account, adds an income operation, checks calculator arithmetic, and verifies key authenticated endpoints. It does not clean up the account afterwards.

## API reference

All endpoints require `Authorization: Bearer <token>` except registration, login and the health endpoint. JSON requests use `Content-Type: application/json`. Amounts are integer minor units (for example, `1299` means EUR 12.99 for a two-decimal currency).

### Authentication

- `POST /api/auth/register` — `{"email":"you@example.com","password":"LongPassword123!"}`
- `POST /api/auth/login` — same body; returns `{ "token": "...", "userId": 1, "email": "..." }`

Passwords must contain 10–72 characters and at most 72 UTF-8 bytes because BCrypt has a 72-byte input limit. Access tokens expire after 12 hours by default.

### Wallets and operations

- `GET /api/wallets`
- `POST /api/wallets` — `{"name":"Cash","currency":"EUR","initialBalanceMinor":50000}`
- `GET /api/transactions?limit=50`
- Optional transaction filters: `type=INCOME|EXPENSE`, `category=Food`, `walletId=1`, `from=2026-10-01`, `to=2026-10-31`.
- `POST /api/transactions` — `{"walletId":1,"type":"EXPENSE","amountMinor":1299,"category":"Food","description":"Lunch","occurredAt":"2026-10-11T12:00:00Z"}`. `occurredAt` is optional.
- `GET /api/transfers?limit=50`
- `POST /api/transfers` — `{"fromWalletId":1,"toWalletId":2,"amountMinor":1000,"description":"Move savings"}`
- `GET /api/dashboard`

### Budgets, goals, recurring operations, reports and calculator

- `GET /api/budgets`
- `POST /api/budgets` — `{"name":"Food","category":"Food","limitMinor":30000,"month":"2026-10"}`
- `GET /api/categories`
- `DELETE /api/transactions/{id}` — atomically reverses a manual transaction's wallet effect before deleting its ledger row. Recurring-generated rows are protected; reversing an income fails if the wallet no longer contains enough funds.
- `GET /api/goals`
- `POST /api/goals` — `{"name":"Emergency fund","currency":"EUR","targetMinor":100000,"targetDate":"2027-12-31"}`
- `POST /api/goals/{id}/contributions` — `{"walletId":1,"amountMinor":2500,"note":"Monthly saving"}`; atomically debits a matching-currency wallet and increases goal progress, without misclassifying savings as a purchase expense.
- `GET /api/goals/{id}/contributions?limit=50`
- `GET /api/recurring`
- `POST /api/recurring` — `{"walletId":1,"type":"EXPENSE","amountMinor":1200,"category":"Internet","description":"Monthly bill","frequency":"MONTHLY","nextRunAt":"2026-10-15"}`
- `PATCH /api/recurring/{id}` — `{"active":false}` to pause or `true` to resume.
- `POST /api/recurring/{id}/run` — executes one due occurrence transactionally; it is not run automatically in the background. Run repeatedly to catch up old due dates, or pause the rule.
- `GET /api/recurring/{id}/history?limit=50`
- `GET /api/reports/monthly?month=2026-10` (month is optional and defaults to the current UTC month)
- `GET /api/export/transactions.csv`
- `POST /api/calculator/price` — `{"unitPriceMinor":10000,"quantity":2,"discountPercent":15,"fixedDiscountMinor":0,"taxPercent":0,"feeMinor":0}`

Price-calculator order: subtotal → percentage discount plus fixed discount (capped at subtotal) → tax on discounted subtotal → fee. Monetary rounding uses `HALF_UP`; overflow returns HTTP 400 rather than silently wrapping an integer.

Errors use JSON with `timestamp`, `status`, `error`, `message`, `details` (when relevant), and `path`. Typical statuses: 400 invalid input, 401 invalid/expired token, 404 record not found, 409 insufficient balance or duplicate budget, 500 unexpected server error.

## Data integrity and financial semantics

- PostgreSQL stores all wallet balances as integer minor units and rejects negative balances.
- A transaction updates the wallet and inserts its ledger row in one database transaction.
- A transfer updates both wallets and inserts its transfer record atomically. Wallets must belong to the authenticated user and have the same currency.
- Savings-goal contributions lock the goal row, check the remaining target and wallet currency, and update the wallet and goal in one transaction.
- Recurring operations are explicitly triggered through the API. A row lock and unique `(rule_id, scheduled_date)` constraint protect each scheduled occurrence against duplicate execution; each execution is written to an audit history.
- Monetary balance credits guard against signed 64-bit integer overflow. Transaction deletion is limited to manually created rows and reverses the original wallet impact in the same transaction.
- Database composite foreign keys also enforce wallet ownership consistency for ledger and transfer rows.
- No exchange rate is invented. Cross-currency transfers are not supported; dashboards and reports group balances and totals by currency instead of adding unlike currencies together.
- The initial balance supplied when creating a wallet initializes the balance; it is not currently represented as an income ledger row. Keep this distinction in mind when reconciling historic totals.
- Budget spending is scoped by both category and currency. Category matching is case-sensitive; use consistent category names. Wallet and budget currencies are limited to ISO currencies with two minor-unit digits in this version.

## Project layout

```text
moneyflow/
├── android/                         # Kotlin + Jetpack Compose app
│   └── app/src/main/java/com/moneyflow/app/
│       ├── Api.kt                   # Retrofit API models and session
│       └── MainActivity.kt          # Screens and app UI
├── backend/
│   ├── src/main/java/com/moneyflow/api/
│   │   ├── AuthController.java
│   │   ├── FinanceController.java
│   │   ├── PriceCalculator.java
│   │   ├── PlanningController.java
│   │   ├── ApiExceptionHandler.java
│   │   └── SecurityConfig.java
│   ├── src/main/resources/db/migration/
│   │   ├── V1__initial_schema.sql
│   │   ├── V2__integrity_indexes.sql
│   │   ├── V3__currency_aware_budgets.sql
│   │   └── V4__goals_and_recurring_rules.sql
│   └── src/test/java/com/moneyflow/api/PriceCalculatorTest.java
├── scripts/smoke-test.sh
├── docker-compose.yml
└── .env.example
```

## Important production hardening still required

This is a substantial development starter, not an audited banking product. Before public deployment, add HTTPS termination, strict CORS policy if a web client is added, API rate limiting, email verification, password reset, refresh-token rotation/revocation, encrypted Android token storage, backup/restore drills, monitoring, database least-privilege credentials, secret management, dependency scanning and a security review. Add audited correction workflows, account deletion policy and integration tests before public deployment. Transaction deletion is implemented for manual rows, with a transactional balance reversal; recurring-generated rows cannot be deleted through this endpoint. Configure backups before storing important personal records.

The Android source and backend tests are included, but the environment used to prepare this archive did not have Maven, Gradle or Docker available. Therefore the project has **not** been compiled or run end-to-end in this environment. Run the included smoke test after starting the server and execute `mvn test` in `backend/` (or build via Docker) to validate it in your development environment.

## iOS client (SwiftUI)

A native iPhone/iPad client is available in `ios/`. It shares the same Spring Boot API and PostgreSQL database as Android. Open `ios/README.md` for Xcode setup, API URL configuration, supported screens, and current build-validation limitations. Generate the Xcode project with XcodeGen (`brew install xcodegen`, then `cd ios && xcodegen generate`) and open `ios/MoneyFlow.xcodeproj` on macOS.
