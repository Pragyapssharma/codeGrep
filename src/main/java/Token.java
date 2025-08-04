public class Token {
    enum TokenType {
        DIGIT, WORD, CHAR, POSITIVE_GROUP, NEGATIVE_GROUP
    }

    enum Quantifier {
        ONE, ONE_OR_MORE, ZERO_OR_ONE
    }

    TokenType type;
    String value;
    Quantifier quantifier;

    public Token(TokenType type, String value) {
        this(type, value, Quantifier.ONE);
    }

    public Token(TokenType type, String value, Quantifier quantifier) {
        this.type = type;
        this.value = value;
        this.quantifier = quantifier;
    }

    public boolean matches(char c) {
        switch (type) {
            case DIGIT:
                return Character.isDigit(c);
            case WORD:
                return Character.isLetterOrDigit(c) || c == '_';
            case CHAR:
                return c == value.charAt(0);
            case POSITIVE_GROUP:
                return value.indexOf(c) != -1;
            case NEGATIVE_GROUP:
                return value.indexOf(c) == -1;
            default:
                return false;
        }
    }
}