package com.moneyflow.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@Validated
@RequestMapping("/api")
class FinanceController {
    private final JdbcTemplate db;
    FinanceController(JdbcTemplate db) { this.db=db; }
    private long uid(Authentication a) { return ((Number)a.getPrincipal()).longValue(); }
    private Map<String,Object> one(String sql,Object... args) { var rows=db.queryForList(sql,args); if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Record not found"); return rows.get(0); }
    private void ownsWallet(long user,long wallet) { if(db.queryForObject("SELECT count(*) FROM wallet WHERE id=? AND user_id=?",Integer.class,wallet,user)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Wallet not found"); }

    record WalletCreate(@NotBlank @Size(max=80) String name, @Pattern(regexp="[A-Za-z]{3}") String currency, @Min(0) long initialBalanceMinor) {}
    @GetMapping("/wallets") List<Map<String,Object>> wallets(Authentication a) { return db.queryForList("SELECT id,name,currency,balance_minor AS \"balanceMinor\",created_at AS \"createdAt\" FROM wallet WHERE user_id=? ORDER BY id",uid(a)); }
    @PostMapping("/wallets") @Transactional Map<String,Object> createWallet(@Valid @RequestBody WalletCreate c,Authentication a) {
        String cur=c.currency()==null?"EUR":c.currency().trim().toUpperCase(Locale.ROOT);
        try {
            java.util.Currency parsed=java.util.Currency.getInstance(cur);
            if(parsed.getDefaultFractionDigits()!=2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This version supports only currencies with two minor-unit digits");
        } catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Currency must be a valid ISO 4217 code"); }
        if (c.name().trim().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Wallet name cannot be blank");
        Long id=db.queryForObject("INSERT INTO wallet(user_id,name,currency,balance_minor) VALUES (?,?,?,?) RETURNING id",Long.class,uid(a),c.name().trim(),cur,c.initialBalanceMinor());
        return one("SELECT id,name,currency,balance_minor AS \"balanceMinor\",created_at AS \"createdAt\" FROM wallet WHERE id=? AND user_id=?",id,uid(a));
    }

    record TxCreate(@Positive long walletId,@NotNull TxType type,@Positive long amountMinor,@NotBlank @Size(max=80) String category,@Size(max=240) String description,Instant occurredAt) {}
    enum TxType { INCOME, EXPENSE }
    @GetMapping("/transactions") List<Map<String,Object>> transactions(
        @RequestParam(defaultValue="50") @Min(1) @Max(200) int limit,
        @RequestParam(required=false) String type,
        @RequestParam(required=false) String category,
        @RequestParam(required=false) Long walletId,
        @RequestParam(required=false) String from,
        @RequestParam(required=false) String to,
        Authentication a) {
        long user=uid(a);
        TxType parsedType=null;
        if(type!=null && !type.isBlank()) {
            try { parsedType=TxType.valueOf(type.trim().toUpperCase(Locale.ROOT)); }
            catch(IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"type must be INCOME or EXPENSE"); }
        }
        LocalDate fromDate=parseDate(from,"from"); LocalDate toDate=parseDate(to,"to");
        if(fromDate!=null && toDate!=null && fromDate.isAfter(toDate)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"from must not be after to");
        StringBuilder sql=new StringBuilder("SELECT t.id,t.wallet_id AS \"walletId\",w.name AS \"walletName\",w.currency,t.type,t.amount_minor AS \"amountMinor\",t.category,t.description,t.occurred_at AS \"occurredAt\" FROM finance_transaction t JOIN wallet w ON w.id=t.wallet_id WHERE t.user_id=?");
        List<Object> args=new ArrayList<>(); args.add(user);
        if(parsedType!=null) { sql.append(" AND t.type=?"); args.add(parsedType.name()); }
        String cleanCategory=blankToNull(category); if(cleanCategory!=null) { sql.append(" AND lower(t.category)=lower(?)"); args.add(cleanCategory); }
        if(walletId!=null) { if(walletId<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"walletId must be positive"); sql.append(" AND t.wallet_id=?"); args.add(walletId); }
        if(fromDate!=null) { sql.append(" AND t.occurred_at>=?"); args.add(fromDate.atStartOfDay().toInstant(ZoneOffset.UTC)); }
        if(toDate!=null) { sql.append(" AND t.occurred_at<?"); args.add(toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)); }
        sql.append(" ORDER BY t.occurred_at DESC,t.id DESC LIMIT ?"); args.add(limit);
        return db.queryForList(sql.toString(),args.toArray());
    }
    private String blankToNull(String value) { return value==null || value.isBlank()?null:value.trim(); }
    private LocalDate parseDate(String value,String field) {
        if(value==null || value.isBlank()) return null;
        try { return LocalDate.parse(value); }
        catch(java.time.format.DateTimeParseException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,field+" must use YYYY-MM-DD format"); }
    }
    @PostMapping("/transactions") @Transactional Map<String,Object> createTx(@Valid @RequestBody TxCreate c,Authentication a) {
        long user=uid(a); ownsWallet(user,c.walletId());
        if(c.type()==TxType.EXPENSE) {
            int updated=db.update("UPDATE wallet SET balance_minor=balance_minor-? WHERE id=? AND user_id=? AND balance_minor>=?",c.amountMinor(),c.walletId(),user,c.amountMinor());
            if(updated==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient wallet balance");
        } else {
            int updated=db.update("UPDATE wallet SET balance_minor=balance_minor+? WHERE id=? AND user_id=? AND balance_minor<=?",c.amountMinor(),c.walletId(),user,Long.MAX_VALUE-c.amountMinor());
            if(updated==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Wallet balance would exceed the supported limit");
        }
        Long id=db.queryForObject("INSERT INTO finance_transaction(user_id,wallet_id,type,amount_minor,category,description,occurred_at) VALUES (?,?,?,?,?,?,?) RETURNING id",Long.class,user,c.walletId(),c.type().name(),c.amountMinor(),c.category().trim(),Objects.toString(c.description(),""),c.occurredAt()==null?Instant.now():c.occurredAt());
        return one("SELECT id,wallet_id AS \"walletId\",type,amount_minor AS \"amountMinor\",category,description,occurred_at AS \"occurredAt\" FROM finance_transaction WHERE id=? AND user_id=?",id,user);
    }

    /** Deletes a manual transaction and reverses its wallet effect atomically. Recurring-generated
     * transactions are protected because their schedule history is an audit trail. */
    @DeleteMapping("/transactions/{transactionId}") @Transactional
    Map<String,Object> deleteTransaction(@PathVariable @Positive long transactionId, Authentication a) {
        long user=uid(a);
        Map<String,Object> tx=one("SELECT id,wallet_id AS \"walletId\",type,amount_minor AS \"amountMinor\" FROM finance_transaction WHERE id=? AND user_id=? FOR UPDATE",transactionId,user);
        Integer generated=db.queryForObject("SELECT count(*) FROM recurring_execution WHERE transaction_id=?",Integer.class,transactionId);
        if(generated!=null && generated>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Recurring transaction history cannot be deleted");
        long walletId=((Number)tx.get("walletId")).longValue();
        long amount=((Number)tx.get("amountMinor")).longValue();
        String type=Objects.toString(tx.get("type"));
        int changed;
        if("EXPENSE".equals(type)) {
            changed=db.update("UPDATE wallet SET balance_minor=balance_minor+? WHERE id=? AND user_id=? AND balance_minor<=?",amount,walletId,user,Long.MAX_VALUE-amount);
            if(changed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Cannot reverse expense because wallet balance would overflow");
        } else {
            changed=db.update("UPDATE wallet SET balance_minor=balance_minor-? WHERE id=? AND user_id=? AND balance_minor>=?",amount,walletId,user,amount);
            if(changed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Cannot reverse income because the wallet no longer has enough funds");
        }
        db.update("DELETE FROM finance_transaction WHERE id=? AND user_id=?",transactionId,user);
        return Map.of("deleted",true,"transactionId",transactionId,"reversedAmountMinor",amount,"type",type);
    }

    record Transfer(@Positive long fromWalletId,@Positive long toWalletId,@Positive long amountMinor,@Size(max=240) String description) {}
    @GetMapping("/transfers") List<Map<String,Object>> transfers(@RequestParam(defaultValue="50") @Min(1) @Max(200) int limit, Authentication a) {
        return db.queryForList("SELECT t.id,t.from_wallet_id AS \"fromWalletId\",fw.name AS \"fromWalletName\",t.to_wallet_id AS \"toWalletId\",tw.name AS \"toWalletName\",t.amount_minor AS \"amountMinor\",fw.currency,t.description,t.created_at AS \"createdAt\" FROM wallet_transfer t JOIN wallet fw ON fw.id=t.from_wallet_id JOIN wallet tw ON tw.id=t.to_wallet_id WHERE t.user_id=? ORDER BY t.created_at DESC,t.id DESC LIMIT ?",uid(a),limit);
    }

    @PostMapping("/transfers") @Transactional Map<String,Object> transfer(@Valid @RequestBody Transfer c,Authentication a) {
        long user=uid(a); if(c.fromWalletId()==c.toWalletId()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose two different wallets");
        ownsWallet(user,c.fromWalletId()); ownsWallet(user,c.toWalletId());
        String fromCurrency=(String)one("SELECT currency FROM wallet WHERE id=? AND user_id=?",c.fromWalletId(),user).get("currency");
        String toCurrency=(String)one("SELECT currency FROM wallet WHERE id=? AND user_id=?",c.toWalletId(),user).get("currency");
        if(!fromCurrency.equals(toCurrency)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Transfers between different currencies require an exchange rate and are disabled");
        if(db.update("UPDATE wallet SET balance_minor=balance_minor-? WHERE id=? AND user_id=? AND balance_minor>=?",c.amountMinor(),c.fromWalletId(),user,c.amountMinor())==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient wallet balance");
        int credited=db.update("UPDATE wallet SET balance_minor=balance_minor+? WHERE id=? AND user_id=? AND balance_minor<=?",c.amountMinor(),c.toWalletId(),user,Long.MAX_VALUE-c.amountMinor());
        if(credited==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Destination wallet balance would exceed the supported limit");
        Long id=db.queryForObject("INSERT INTO wallet_transfer(user_id,from_wallet_id,to_wallet_id,amount_minor,description) VALUES (?,?,?,?,?) RETURNING id",Long.class,user,c.fromWalletId(),c.toWalletId(),c.amountMinor(),Objects.toString(c.description(),""));
        return one("SELECT id,from_wallet_id AS \"fromWalletId\",to_wallet_id AS \"toWalletId\",amount_minor AS \"amountMinor\",description,created_at AS \"createdAt\" FROM wallet_transfer WHERE id=? AND user_id=?",id,user);
    }

    record BudgetCreate(@NotBlank @Size(max=80) String name,@NotBlank @Size(max=80) String category,@Positive long limitMinor,@NotBlank @Pattern(regexp="\\d{4}-(0[1-9]|1[0-2])") String month,@Pattern(regexp="[A-Za-z]{3}") String currency) {}
    @GetMapping("/budgets") List<Map<String,Object>> budgets(Authentication a) {
        return db.queryForList("SELECT b.id,b.name,b.category,b.currency,b.limit_minor AS \"limitMinor\",b.month,COALESCE(sum(t.amount_minor),0) AS \"spentMinor\" FROM budget b LEFT JOIN wallet w ON w.user_id=b.user_id AND w.currency=b.currency LEFT JOIN finance_transaction t ON t.user_id=b.user_id AND t.wallet_id=w.id AND t.type='EXPENSE' AND t.category=b.category AND to_char(t.occurred_at AT TIME ZONE 'UTC','YYYY-MM')=b.month WHERE b.user_id=? GROUP BY b.id ORDER BY b.month DESC,b.id",uid(a));
    }
    @PostMapping("/budgets") Map<String,Object> createBudget(@Valid @RequestBody BudgetCreate c,Authentication a) {
        String cur=c.currency()==null?"EUR":c.currency().trim().toUpperCase(Locale.ROOT);
        try { if(java.util.Currency.getInstance(cur).getDefaultFractionDigits()!=2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This version supports only currencies with two minor-unit digits"); }
        catch(IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Currency must be a valid ISO 4217 code"); }
        try { Long id=db.queryForObject("INSERT INTO budget(user_id,name,category,limit_minor,month,currency) VALUES (?,?,?,?,?,?) RETURNING id",Long.class,uid(a),c.name().trim(),c.category().trim(),c.limitMinor(),c.month(),cur); return one("SELECT id,name,category,currency,limit_minor AS \"limitMinor\",month FROM budget WHERE id=? AND user_id=?",id,uid(a)); }
        catch(org.springframework.dao.DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"A budget for this category, month and currency already exists"); }
    }

    record PriceRequest(@Positive long unitPriceMinor,@NotNull @DecimalMin("0.0001") @DecimalMax("100000") BigDecimal quantity,@DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent,@Min(0) long fixedDiscountMinor,@DecimalMin("0") @DecimalMax("100") BigDecimal taxPercent,@Min(0) long feeMinor) {}
    @PostMapping("/calculator/price") Map<String,Object> calculate(@Valid @RequestBody PriceRequest r, Authentication a) {
        return PriceCalculator.calculate(r.unitPriceMinor(), r.quantity(), r.discountPercent(),
                r.fixedDiscountMinor(), r.taxPercent(), r.feeMinor());
    }

    @GetMapping(value="/export/transactions.csv", produces="text/csv")
    ResponseEntity<String> exportTransactions(Authentication a) {
        List<Map<String,Object>> rows=db.queryForList("SELECT t.id,w.name AS wallet,t.type,t.amount_minor AS amount_minor,w.currency,t.category,t.description,t.occurred_at AS occurred_at FROM finance_transaction t JOIN wallet w ON w.id=t.wallet_id WHERE t.user_id=? ORDER BY t.occurred_at DESC,t.id DESC",uid(a));
        StringBuilder csv=new StringBuilder("id,wallet,type,amount_minor,currency,category,description,occurred_at\r\n");
        for(Map<String,Object> row:rows) {
            csv.append(row.get("id")).append(',').append(csvCell(row.get("wallet"))).append(',')
                .append(csvCell(row.get("type"))).append(',').append(row.get("amount_minor")).append(',')
                .append(csvCell(row.get("currency"))).append(',').append(csvCell(row.get("category"))).append(',')
                .append(csvCell(row.get("description"))).append(',').append(csvCell(row.get("occurred_at"))).append("\r\n");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=moneyflow-transactions.csv")
            .header(HttpHeaders.CACHE_CONTROL,"no-store").body(csv.toString());
    }
    private String csvCell(Object value) {
        String text=Objects.toString(value, "");
        int first=0;
        while(first<text.length() && Character.isWhitespace(text.charAt(first))) first++;
        if(first<text.length() && "=+-@".indexOf(text.charAt(first))>=0) text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }

    @GetMapping("/dashboard") Map<String,Object> dashboard(Authentication a) {
        long user=uid(a);
        List<Map<String,Object>> balances=db.queryForList("SELECT currency,COALESCE(sum(balance_minor),0) AS \"balanceMinor\" FROM wallet WHERE user_id=? GROUP BY currency ORDER BY currency",user);
        List<Map<String,Object>> monthly=db.queryForList("SELECT w.currency,COALESCE(sum(t.amount_minor) FILTER (WHERE t.type='INCOME'),0) AS \"incomeMinor\",COALESCE(sum(t.amount_minor) FILTER (WHERE t.type='EXPENSE'),0) AS \"expenseMinor\" FROM finance_transaction t JOIN wallet w ON w.id=t.wallet_id WHERE t.user_id=? AND t.occurred_at>=date_trunc('month',now()) AND t.occurred_at<date_trunc('month',now())+INTERVAL '1 month' GROUP BY w.currency ORDER BY w.currency",user);
        Map<String,Object> eur=balances.stream().filter(x->"EUR".equals(x.get("currency"))).findFirst().orElse(Map.of("balanceMinor",0L));
        Map<String,Object> eurMonth=monthly.stream().filter(x->"EUR".equals(x.get("currency"))).findFirst().orElse(Map.of("incomeMinor",0L,"expenseMinor",0L));
        var recent=db.queryForList("SELECT t.id,t.wallet_id AS \"walletId\",w.name AS \"walletName\",w.currency,t.type,t.amount_minor AS \"amountMinor\",t.category,t.description,t.occurred_at AS \"occurredAt\" FROM finance_transaction t JOIN wallet w ON w.id=t.wallet_id WHERE t.user_id=? ORDER BY t.occurred_at DESC,t.id DESC LIMIT 5",user);
        return Map.of("balanceMinor",eur.get("balanceMinor"),"incomeThisMonthMinor",eurMonth.get("incomeMinor"),"expenseThisMonthMinor",eurMonth.get("expenseMinor"),"balancesByCurrency",balances,"monthlyByCurrency",monthly,"recentTransactions",recent);
    }

    @GetMapping("/reports/monthly") Map<String,Object> monthlyReport(@RequestParam(required=false) String month, Authentication a) {
        YearMonth ym;
        try { ym=month==null || month.isBlank()?YearMonth.now(ZoneOffset.UTC):YearMonth.parse(month); }
        catch(java.time.DateTimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"month must use YYYY-MM format"); }
        Instant start=ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end=ym.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long user=uid(a);
        List<Map<String,Object>> totalsByCurrency=db.queryForList("SELECT w.currency,COALESCE(sum(t.amount_minor) FILTER (WHERE t.type='INCOME'),0) AS \"incomeMinor\",COALESCE(sum(t.amount_minor) FILTER (WHERE t.type='EXPENSE'),0) AS \"expenseMinor\",count(t.id) AS \"transactionCount\" FROM wallet w LEFT JOIN finance_transaction t ON t.wallet_id=w.id AND t.user_id=w.user_id AND t.occurred_at>=? AND t.occurred_at<? WHERE w.user_id=? GROUP BY w.currency ORDER BY w.currency",start,end,user);
        List<Map<String,Object>> categories=db.queryForList("SELECT w.currency,t.category,COALESCE(sum(t.amount_minor),0) AS \"amountMinor\",count(*) AS \"count\" FROM finance_transaction t JOIN wallet w ON w.id=t.wallet_id WHERE t.user_id=? AND t.type='EXPENSE' AND t.occurred_at>=? AND t.occurred_at<? GROUP BY w.currency,t.category ORDER BY w.currency,sum(t.amount_minor) DESC,t.category",user,start,end);
        Map<String,Object> eur=totalsByCurrency.stream().filter(x->"EUR".equals(x.get("currency"))).findFirst().orElse(Map.of("incomeMinor",0L,"expenseMinor",0L,"transactionCount",0L));
        long income=((Number)eur.get("incomeMinor")).longValue(), expense=((Number)eur.get("expenseMinor")).longValue();
        long transactionCount=totalsByCurrency.stream().mapToLong(x->((Number)x.get("transactionCount")).longValue()).sum();
        return Map.of("month",ym.toString(),"incomeMinor",income,"expenseMinor",expense,"netMinor",income-expense,"transactionCount",transactionCount,"totalsByCurrency",totalsByCurrency,"expensesByCategory",categories);
    }

    @GetMapping("/categories") List<Map<String,Object>> categories(Authentication a) {
        return db.queryForList("SELECT w.currency,t.category, count(*) AS \"transactionCount\", COALESCE(sum(t.amount_minor) FILTER (WHERE t.type='EXPENSE'),0) AS \"expenseMinor\", COALESCE(sum(t.amount_minor) FILTER (WHERE t.type='INCOME'),0) AS \"incomeMinor\" FROM finance_transaction t JOIN wallet w ON w.id=t.wallet_id WHERE t.user_id=? GROUP BY w.currency,t.category ORDER BY w.currency,lower(t.category)",uid(a));
    }
}
