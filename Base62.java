public class Base62 {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length(); // 62

    private Base62() {}

    /** Number -> base62 text, e.g. 1000 -> "g8". */
    public static String encode(long number) {
        if (number < 0) {
            throw new IllegalArgumentException("Number must not be negative");
        }
        if (number == 0) {
            return "0";
        }
        StringBuilder result = new StringBuilder();
        while (number > 0) {
            result.append(ALPHABET.charAt((int) (number % BASE)));
            number /= BASE;
        }
        return result.reverse().toString();
    }

    /** base62 text -> number, the exact reverse of encode(). */
    public static long decode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Code must not be empty");
        }
        long result = 0;
        for (char c : code.toCharArray()) {
            int digit = ALPHABET.indexOf(c);
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid base62 character: " + c);
            }
            result = result * BASE + digit;
        }
        return result;
    }
}
