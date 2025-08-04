import java.util.*;

public class Token {
    enum TokenType {
        DIGIT, WORD, CHAR, POSITIVE_GROUP, NEGATIVE_GROUP, DOT, ALTERNATION, GROUP
    }

    enum Quantifier {
        ONE, ONE_OR_MORE, ZERO_OR_ONE
    }

    final TokenType type;
    final String value;
    final List<List<Token>> alternatives;
    Quantifier quantifier = Quantifier.ONE;
    List<Token> groupTokens;

    public Token(TokenType type, String value) {
        this.type = type;
        this.value = value;
        this.alternatives = null;
    }

    public Token(List<List<Token>> alternatives) {
        this.type = TokenType.ALTERNATION;
        this.value = null;
        this.alternatives = alternatives;
    }
    
    public Token(List<Token> groupTokens, TokenType type) {
        this.type = type;
        this.value = null;
        this.alternatives = null;
        this.groupTokens = groupTokens;
    }

    public boolean matches(char c) {
        switch (type) {
            case DIGIT:
                return Character.isDigit(c);
            case WORD:
                return Character.isLetterOrDigit(c) || c == '_';
            case CHAR:
                return value.charAt(0) == c;
            case POSITIVE_GROUP:
                return value.indexOf(c) >= 0;
            case NEGATIVE_GROUP:
                return value.indexOf(c) == -1;
            case DOT:
                return true;
            case ALTERNATION:
                throw new UnsupportedOperationException("Alternation should be handled at a higher level");
            default:
                return false;
        }
    }
}