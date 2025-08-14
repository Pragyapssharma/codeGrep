import java.util.*;

public class Token {
    enum TokenType {
        DIGIT, WORD, CHAR, POSITIVE_GROUP, NEGATIVE_GROUP, DOT, ALTERNATION, GROUP, BACKREF
    }

    enum Quantifier {
        ONE, ONE_OR_MORE, ZERO_OR_ONE
    }

    final TokenType type;
    final String value;
    final List<List<Token>> alternatives;
    final List<Token> groupTokens;
    Quantifier quantifier = Quantifier.ONE;
    
    boolean capturing = false; // only meaningful for the first capturing GROUP
    int groupIndex = 0;        // 1 for the first capturing group, else 0
    int refIndex = 0;          // for BACKREF (\1)

    public Token(TokenType type, String value) {
        this.type = type;
        this.value = value;
        this.alternatives = null;
        this.groupTokens = null;
    }

    public Token(List<List<Token>> alternatives) {
        this.type = TokenType.ALTERNATION;
        this.value = null;
        this.alternatives = alternatives;
        this.groupTokens = null;
    }
    
    public Token(List<Token> groupTokens, TokenType type) {
        this.type = type;
        this.value = null;
        this.alternatives = null;
        this.groupTokens = groupTokens;
    }
    
    public Token(int backrefIndex) {
        this.type = TokenType.BACKREF;
        this.value = null;
        this.alternatives = null;
        this.groupTokens = null;
        this.refIndex = backrefIndex;
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
            case BACKREF:
            case ALTERNATION:
            case GROUP:
                throw new UnsupportedOperationException("Alternation should be handled at a higher level");
            default:
                return false;
        }
    }
    
    public int matchOnce(String input, int pos, Captures caps) {
        switch (type) {
            case DIGIT:
            case WORD:
            case CHAR:
            case POSITIVE_GROUP:
            case NEGATIVE_GROUP:
            case DOT:
                if (pos < input.length() && matches(input.charAt(pos))) {
                    return pos + 1;
                }
                return -1;
            case BACKREF:
                if (refIndex != 1 || !caps.isSet()) return -1;
                int len = caps.length();
                if (pos + len > input.length()) return -1;
                for (int k = 0; k < len; k++) {
                    if (input.charAt(pos + k) != input.charAt(caps.start + k)) {
                        return -1;
                    }
                }
                return pos + len;
            case ALTERNATION:
            case GROUP:
                throw new UnsupportedOperationException("Handled at a higher level");
            default:
                return -1;
        }
    }

    public boolean isCharLike() {
        return type == TokenType.DIGIT || type == TokenType.WORD
            || type == TokenType.CHAR || type == TokenType.POSITIVE_GROUP
            || type == TokenType.NEGATIVE_GROUP || type == TokenType.DOT;
    }
    
}