package com.moneyflow.api;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PriceCalculatorTest {
    @Test void computesDiscountTaxAndFeeInMinorUnits() {
        Map<String, Object> result = PriceCalculator.calculate(10_000L, new BigDecimal("2"),
                new BigDecimal("10"), 500L, new BigDecimal("20"), 100L);
        assertEquals(20_000L, result.get("subtotalMinor"));
        assertEquals(2_500L, result.get("discountMinor"));
        assertEquals(3_500L, result.get("taxMinor"));
        assertEquals(100L, result.get("feeMinor"));
        assertEquals(21_100L, result.get("totalMinor"));
        assertEquals(new BigDecimal("12.50"), result.get("savedPercent"));
    }

    @Test void capsCombinedDiscountAtSubtotal() {
        Map<String, Object> result = PriceCalculator.calculate(100L, BigDecimal.ONE,
                new BigDecimal("100"), 999L, BigDecimal.ZERO, 0L);
        assertEquals(100L, result.get("discountMinor"));
        assertEquals(0L, result.get("totalMinor"));
    }

    @Test void rejectsNegativeFeeAndOutOfRangePercent() {
        assertThrows(ResponseStatusException.class, () -> PriceCalculator.calculate(100L, BigDecimal.ONE,
                BigDecimal.ZERO, 0L, BigDecimal.ZERO, -1L));
        assertThrows(ResponseStatusException.class, () -> PriceCalculator.calculate(100L, BigDecimal.ONE,
                new BigDecimal("101"), 0L, BigDecimal.ZERO, 0L));
    }

    @Test void rejectsOverflowInsteadOfReturningCorruptedAmount() {
        assertThrows(ResponseStatusException.class, () -> PriceCalculator.calculate(Long.MAX_VALUE,
                new BigDecimal("100000"), BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0L));
    }
}
