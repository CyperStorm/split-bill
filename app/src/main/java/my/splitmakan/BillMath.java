package my.splitmakan;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class BillMath {
    private static final long MAX_SEN = 100_000_000;

    static String rm(long sen) {
        return String.format(Locale.US, "%sRM %,.2f", sen < 0 ? "-" : "",
                BigDecimal.valueOf(Math.abs(sen), 2));
    }

    static long money(String input, boolean signed) {
        String value = input.trim();
        String pattern = signed ? "[+-]?[0-9]{1,7}(\\.[0-9]{1,2})?" : "[0-9]{1,7}(\\.[0-9]{1,2})?";
        if (!value.matches(pattern)) throw new IllegalArgumentException();
        long sen = new BigDecimal(value).movePointRight(2).longValueExact();
        if (Math.abs(sen) > MAX_SEN) throw new IllegalArgumentException();
        return sen;
    }

    static long charge(String input, long subtotal) {
        String value = input.trim();
        if (value.isEmpty()) return 0;
        if (!value.endsWith("%")) return money(value, false);
        String percent = value.substring(0, value.length() - 1).trim();
        long basisPoints = money(percent, false);
        if (basisPoints > 10_000) throw new IllegalArgumentException();
        return BigDecimal.valueOf(subtotal).multiply(BigDecimal.valueOf(basisPoints, 4))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    // Largest remainder in whole sen. Ties follow the order diners were added.
    static long[] allocate(long amount, long[] weights) {
        long sum = 0;
        for (long weight : weights) sum += weight;
        long[] shares = new long[weights.length];
        if (amount == 0) return shares;
        if (sum <= 0) throw new IllegalArgumentException();
        long[] remainder = new long[weights.length];
        List<Integer> order = new ArrayList<>();
        long remaining = Math.abs(amount);
        for (int i = 0; i < weights.length; i++) {
            BigInteger[] parts = BigInteger.valueOf(Math.abs(amount)).multiply(BigInteger.valueOf(weights[i]))
                    .divideAndRemainder(BigInteger.valueOf(sum));
            shares[i] = parts[0].longValueExact();
            remainder[i] = parts[1].longValueExact();
            remaining -= shares[i];
            order.add(i);
        }
        order.sort(Comparator.<Integer>comparingLong(i -> remainder[i]).reversed());
        for (int i = 0; i < remaining; i++) shares[order.get(i)]++;
        if (amount < 0) for (int i = 0; i < shares.length; i++) shares[i] = -shares[i];
        return shares;
    }
}
