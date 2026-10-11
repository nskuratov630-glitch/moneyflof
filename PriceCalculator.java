package com.moneyflow.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/** Pure, deterministic price calculation. All monetary inputs/outputs use minor units. */
public final class PriceCalculator {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private PriceCalculator() {}

    public static Map<String, Object> calculate(long unitPriceMinor, BigDecimal quantity,
            BigDecimal discountPercent, long fixedDiscountMinor, BigDecimal taxPercent, long feeMinor) {
        if (unitPriceMinor <= 0) throw bad("unitPriceMinor must be positive");
        if (quantity == null || quantity.signum() <= 0 || quantity.compareTo(BigDecimal.valueOf(100_000)) > 0) throw bad("quantity must be greater than zero and at most 100000");
        if (fixedDiscountMinor < 0 || feeMinor < 0) throw bad("Discount and fee amounts cannot be negative");
        BigDecimal pct = Objects.requireNonNullElse(discountPercent, BigDecimal.ZERO);
        BigDecimal taxRate = Objects.requireNonNullElse(taxPercent, BigDecimal.ZERO);
        if (pct.signum() < 0 || pct.compareTo(HUNDRED) > 0 || taxRate.signum() < 0 || taxRate.compareTo(HUNDRED) > 0) throw bad("Percentages must be between 0 and 100");

        try {
            BigDecimal subtotal = BigDecimal.valueOf(unitPriceMinor).multiply(quantity).setScale(0, RoundingMode.HALF_UP);
            BigDecimal percentageDiscount = subtotal.multiply(pct).divide(HUNDRED, 12, RoundingMode.HALF_UP);
            BigDecimal discount = percentageDiscount.add(BigDecimal.valueOf(fixedDiscountMinor))
                    .min(subtotal).max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);
            BigDecimal taxable = subtotal.subtract(discount).max(BigDecimal.ZERO);
            BigDecimal tax = taxable.multiply(taxRate).divide(HUNDRED, 0, RoundingMode.HALF_UP);
            BigDecimal total = taxable.add(tax).add(BigDecimal.valueOf(feeMinor));
            long subtotalMinor = subtotal.longValueExact();
            long discountMinor = discount.longValueExact();
            long taxMinor = tax.longValueExact();
            long totalMinor = total.longValueExact();
            BigDecimal savedPercent = subtotal.signum() == 0 ? BigDecimal.ZERO : discount.multiply(HUNDRED).divide(subtotal, 2, RoundingMode.HALF_UP);
            return Map.of("subtotalMinor", subtotalMinor, "discountMinor", discountMinor,
                    "taxMinor", taxMinor, "feeMinor", feeMinor, "totalMinor", totalMinor,
                    "savedPercent", savedPercent);
        } catch (ArithmeticException ex) {
            throw bad("The calculated amount exceeds the supported monetary range");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
