package com.moneyflow.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** Goal tracking and idempotent recurring-payment execution endpoints. */
@RestController
@Validated
@RequestMapping("/api")
class PlanningController {
    private final JdbcTemplate db;
    PlanningController(JdbcTemplate db) { this.db = db; }

    private long uid(Authentication auth) { return ((Number) auth.getPrincipal()).longValue(); }
    private Map<String,Object> one(String sql, Object... args) {
        List<Map<String,Object>> rows = db.queryForList(sql, args);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
        return rows.get(0);
    }
    private String currency(String raw) {
        String value = raw == null ? "EUR" : raw.trim().toUpperCase(Locale.ROOT);
        try {
            java.util.Currency parsed = java.util.Currency.getInstance(value);
            if (parsed.getDefaultFractionDigits() != 2)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only currencies with two minor-unit digits are supported");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency must be a valid ISO 4217 code");
        }
        return value;
    }

    record GoalCreate(@NotBlank @Size(max=100) String name,
                      @Pattern(regexp="[A-Za-z]{3}") String currency,
                      @Positive long targetMinor, @FutureOrPresent LocalDate targetDate) {}
    record GoalContribution(@Positive long walletId, @Positive long amountMinor,
                            @Size(max=240) String note) {}

    @GetMapping("/goals")
    List<Map<String,Object>> goals(Authentication auth) {
        return db.queryForList("""
            SELECT g.id,g.name,g.currency,g.target_minor AS \"targetMinor\",
                   g.saved_minor AS \"savedMinor\",g.target_date AS \"targetDate\",
                   CASE WHEN g.target_minor=0 THEN 100
                        ELSE LEAST(100,ROUND(g.saved_minor::numeric*100/g.target_minor,1)) END AS \"progressPercent\",
                   GREATEST(g.target_minor-g.saved_minor,0) AS \"remainingMinor\",
                   g.created_at AS \"createdAt\",g.updated_at AS \"updatedAt\"
            FROM savings_goal g WHERE g.user_id=? ORDER BY (g.saved_minor>=g.target_minor),g.target_date NULLS LAST,g.created_at DESC
            """, uid(auth));
    }

    @PostMapping("/goals") @Transactional
    Map<String,Object> createGoal(@Valid @RequestBody GoalCreate req, Authentication auth) {
        String cur = currency(req.currency());
        Long id = db.queryForObject("INSERT INTO savings_goal(user_id,name,currency,target_minor,target_date) VALUES (?,?,?,?,?) RETURNING id",
                Long.class, uid(auth), req.name().trim(), cur, req.targetMinor(), req.targetDate());
        return one("SELECT id,name,currency,target_minor AS \"targetMinor\",saved_minor AS \"savedMinor\",target_date AS \"targetDate\",0 AS \"progressPercent\",target_minor AS \"remainingMinor\" FROM savings_goal WHERE id=? AND user_id=?", id, uid(auth));
    }

    @PostMapping("/goals/{goalId}/contributions") @Transactional
    Map<String,Object> contribute(@PathVariable @Positive long goalId,
                                  @Valid @RequestBody GoalContribution req, Authentication auth) {
        long user = uid(auth);
        // Lock the goal first so concurrent contributions cannot exceed the target.
        Map<String,Object> goal = one("SELECT id,currency,target_minor AS \"targetMinor\",saved_minor AS \"savedMinor\" FROM savings_goal WHERE id=? AND user_id=? FOR UPDATE", goalId, user);
        Map<String,Object> wallet = one("SELECT id,currency FROM wallet WHERE id=? AND user_id=?", req.walletId(), user);
        if (!Objects.equals(goal.get("currency"), wallet.get("currency")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wallet and savings goal currencies must match");
        long saved = ((Number)goal.get("savedMinor")).longValue();
        long target = ((Number)goal.get("targetMinor")).longValue();
        if (req.amountMinor() > target - saved)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Contribution exceeds the remaining goal amount");
        int debited = db.update("UPDATE wallet SET balance_minor=balance_minor-? WHERE id=? AND user_id=? AND balance_minor>=?",
                req.amountMinor(), req.walletId(), user, req.amountMinor());
        if (debited != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient wallet balance");
        db.update("UPDATE savings_goal SET saved_minor=saved_minor+?,updated_at=now() WHERE id=? AND user_id=?", req.amountMinor(), goalId, user);
        Long id = db.queryForObject("INSERT INTO savings_goal_contribution(user_id,goal_id,wallet_id,amount_minor,note) VALUES (?,?,?,?,?) RETURNING id",
                Long.class, user, goalId, req.walletId(), req.amountMinor(), Objects.toString(req.note(), ""));
        return one("SELECT c.id,c.goal_id AS \"goalId\",c.wallet_id AS \"walletId\",c.amount_minor AS \"amountMinor\",c.note,c.created_at AS \"createdAt\",g.saved_minor AS \"savedMinor\",g.target_minor AS \"targetMinor\" FROM savings_goal_contribution c JOIN savings_goal g ON g.id=c.goal_id WHERE c.id=? AND c.user_id=?", id, user);
    }

    @GetMapping("/goals/{goalId}/contributions")
    List<Map<String,Object>> contributions(@PathVariable @Positive long goalId,
                                           @RequestParam(defaultValue="50") @Min(1) @Max(200) int limit,
                                           Authentication auth) {
        long user = uid(auth);
        one("SELECT id FROM savings_goal WHERE id=? AND user_id=?", goalId, user);
        return db.queryForList("SELECT c.id,c.goal_id AS \"goalId\",c.wallet_id AS \"walletId\",w.name AS \"walletName\",c.amount_minor AS \"amountMinor\",c.note,c.created_at AS \"createdAt\" FROM savings_goal_contribution c JOIN wallet w ON w.id=c.wallet_id WHERE c.user_id=? AND c.goal_id=? ORDER BY c.created_at DESC,c.id DESC LIMIT ?", user, goalId, limit);
    }

    record RecurringCreate(@Positive long walletId, @NotNull RecurringType type,
                           @Positive long amountMinor, @NotBlank @Size(max=80) String category,
                           @Size(max=240) String description, @NotNull Frequency frequency,
                           @NotNull LocalDate nextRunAt) {}
    enum RecurringType { INCOME, EXPENSE }
    enum Frequency { WEEKLY, MONTHLY, YEARLY }

    @GetMapping("/recurring")
    List<Map<String,Object>> recurring(Authentication auth) {
        return db.queryForList("SELECT r.id,r.wallet_id AS \"walletId\",w.name AS \"walletName\",w.currency,r.type,r.amount_minor AS \"amountMinor\",r.category,r.description,r.frequency,r.next_run_at AS \"nextRunAt\",r.active,r.last_run_at AS \"lastRunAt\",r.created_at AS \"createdAt\" FROM recurring_rule r JOIN wallet w ON w.id=r.wallet_id WHERE r.user_id=? ORDER BY r.active DESC,r.next_run_at,r.id", uid(auth));
    }

    @PostMapping("/recurring") @Transactional
    Map<String,Object> createRecurring(@Valid @RequestBody RecurringCreate req, Authentication auth) {
        long user = uid(auth);
        one("SELECT id FROM wallet WHERE id=? AND user_id=?", req.walletId(), user);
        Long id = db.queryForObject("INSERT INTO recurring_rule(user_id,wallet_id,type,amount_minor,category,description,frequency,next_run_at) VALUES (?,?,?,?,?,?,?,?) RETURNING id",
                Long.class, user, req.walletId(), req.type().name(), req.amountMinor(), req.category().trim(), Objects.toString(req.description(), ""), req.frequency().name(), req.nextRunAt());
        return one("SELECT id,wallet_id AS \"walletId\",type,amount_minor AS \"amountMinor\",category,description,frequency,next_run_at AS \"nextRunAt\",active FROM recurring_rule WHERE id=? AND user_id=?", id, user);
    }

    record ActiveChange(@NotNull Boolean active) {}
    @PatchMapping("/recurring/{ruleId}") @Transactional
    Map<String,Object> changeRecurring(@PathVariable @Positive long ruleId,
                                       @Valid @RequestBody ActiveChange req, Authentication auth) {
        int changed = db.update("UPDATE recurring_rule SET active=? WHERE id=? AND user_id=?", req.active(), ruleId, uid(auth));
        if (changed != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring rule not found");
        return one("SELECT id,active,next_run_at AS \"nextRunAt\",frequency FROM recurring_rule WHERE id=? AND user_id=?", ruleId, uid(auth));
    }

    /** Runs only the due occurrence. Row locking plus UNIQUE(rule_id, scheduled_date) prevents duplicate execution. */
    @PostMapping("/recurring/{ruleId}/run") @Transactional
    Map<String,Object> runRecurring(@PathVariable @Positive long ruleId, Authentication auth) {
        long user = uid(auth);
        Map<String,Object> rule = one("SELECT id,wallet_id AS \"walletId\",type,amount_minor AS \"amountMinor\",category,description,frequency,next_run_at AS \"nextRunAt\",active FROM recurring_rule WHERE id=? AND user_id=? FOR UPDATE", ruleId, user);
        if (!Boolean.TRUE.equals(rule.get("active"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Recurring rule is paused");
        Object scheduledRaw = rule.get("nextRunAt");
        LocalDate scheduled = scheduledRaw instanceof LocalDate d ? d : ((java.sql.Date) scheduledRaw).toLocalDate();
        if (scheduled.isAfter(LocalDate.now())) throw new ResponseStatusException(HttpStatus.CONFLICT, "This recurring operation is not due yet");
        long walletId = ((Number)rule.get("walletId")).longValue();
        long amount = ((Number)rule.get("amountMinor")).longValue();
        String type = Objects.toString(rule.get("type"));
        int updated;
        if ("EXPENSE".equals(type)) {
            updated = db.update("UPDATE wallet SET balance_minor=balance_minor-? WHERE id=? AND user_id=? AND balance_minor>=?", amount, walletId, user, amount);
            if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient wallet balance for recurring expense");
        } else {
            updated = db.update("UPDATE wallet SET balance_minor=balance_minor+? WHERE id=? AND user_id=? AND balance_minor<=?", amount, walletId, user, Long.MAX_VALUE - amount);
            if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Wallet balance would exceed the supported limit");
        }
        Long txId = db.queryForObject("INSERT INTO finance_transaction(user_id,wallet_id,type,amount_minor,category,description,occurred_at) VALUES (?,?,?,?,?,?,?) RETURNING id",
                Long.class, user, walletId, type, amount, rule.get("category"), rule.get("description"), scheduled.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        db.update("INSERT INTO recurring_execution(user_id,rule_id,scheduled_date,transaction_id) VALUES (?,?,?,?)", user, ruleId, scheduled, txId);
        LocalDate next = nextDate(scheduled, Objects.toString(rule.get("frequency")));
        db.update("UPDATE recurring_rule SET next_run_at=?,last_run_at=now() WHERE id=? AND user_id=?", next, ruleId, user);
        return Map.of("ruleId", ruleId, "transactionId", txId, "executedDate", scheduled, "nextRunAt", next, "amountMinor", amount, "type", type);
    }

    private LocalDate nextDate(LocalDate current, String frequency) {
        return switch (frequency) {
            case "WEEKLY" -> current.plusWeeks(1);
            case "MONTHLY" -> {
                YearMonth nextMonth = YearMonth.from(current).plusMonths(1);
                yield current.getDayOfMonth() == YearMonth.from(current).lengthOfMonth()
                        ? nextMonth.atEndOfMonth()
                        : nextMonth.atDay(Math.min(current.getDayOfMonth(), nextMonth.lengthOfMonth()));
            }
            case "YEARLY" -> {
                int year = current.getYear() + 1;
                yield LocalDate.of(year, current.getMonth(), Math.min(current.getDayOfMonth(),
                        YearMonth.of(year, current.getMonth()).lengthOfMonth()));
            }
            default -> throw new IllegalStateException("Unsupported recurring frequency");
        };
    }

    @GetMapping("/recurring/{ruleId}/history")
    List<Map<String,Object>> recurringHistory(@PathVariable @Positive long ruleId,
                                               @RequestParam(defaultValue="50") @Min(1) @Max(200) int limit,
                                               Authentication auth) {
        long user = uid(auth);
        one("SELECT id FROM recurring_rule WHERE id=? AND user_id=?", ruleId, user);
        return db.queryForList("SELECT e.id,e.rule_id AS \"ruleId\",e.scheduled_date AS \"scheduledDate\",e.transaction_id AS \"transactionId\",e.executed_at AS \"executedAt\",t.amount_minor AS \"amountMinor\",t.type,t.category FROM recurring_execution e JOIN finance_transaction t ON t.id=e.transaction_id WHERE e.user_id=? AND e.rule_id=? ORDER BY e.scheduled_date DESC LIMIT ?", user, ruleId, limit);
    }
}
