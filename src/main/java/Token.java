import java.util.List;

public class Token {
    public enum TokenType {
        CHAR,
        DOT,
        DIGIT,
        WORD,
        POSITIVE_GROUP,
        NEGATIVE_GROUP,
        BACKREF,
        GROUP,
        ALTERNATION
    }

    public enum Quantifier { ONE, ZERO_OR_ONE, ONE_OR_MORE }

    public TokenType type;
    public String text;
    public int backrefIndex;
    public List<Token> groupTokens;
    public List<List<Token>> alternatives;
    public Quantifier quantifier = Quantifier.ONE;

    public boolean capturing = false;
    public int groupIndex = -1;

    public Token(TokenType t, String txt) { this.type = t; this.text = txt; }
    public Token(int refIdx) { this.type = TokenType.BACKREF; this.backrefIndex = refIdx; }
    public Token(List<Token> groupTokens, TokenType t) { this.type = t; this.groupTokens = groupTokens; }
    public Token(List<List<Token>> alternatives) { this.type = TokenType.ALTERNATION; this.alternatives = alternatives; }

    public int matchOnce(String input, int i, Captures caps) {
        switch (type) {
            case CHAR:
                if (i < input.length() && input.charAt(i) == text.charAt(0)) return i + 1;
                return -1;
            case DOT:
                if (i < input.length()) return i + 1;
                return -1;
            case DIGIT:
                if (i < input.length() && Character.isDigit(input.charAt(i))) return i + 1;
                return -1;
            case WORD:
                if (i < input.length() && isWord(input.charAt(i))) return i + 1;
                return -1;
            case POSITIVE_GROUP:
                if (i < input.length()) {
                    char c = input.charAt(i);
                    if (inCharClass(c, text)) return i + 1;
                }
                return -1;
            case NEGATIVE_GROUP:
                if (i < input.length()) {
                    char c = input.charAt(i);
                    if (!inCharClass(c, text)) return i + 1;
                }
                return -1;
            case BACKREF: {
            	List<Token> tokens = caps.getGroupTokens(backrefIndex);
                String resolved = tokens.isEmpty()
                    ? caps.getGroup(input, backrefIndex)
                    : caps.resolveGroup(input, backrefIndex, tokens);
                
                System.out.println("Resolving \\" + backrefIndex + " to: '" + resolved + "'");
                
                if (resolved == null) return -1;
                int len = resolved.length();
                if (i + len <= input.length() && input.startsWith(resolved, i)) {
                    return i + len;
                }
                return -1;
            }
           default:
                return -1;
        }
    }

    private boolean isWord(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private boolean inCharClass(char c, String cls) {
        for (int k = 0; k < cls.length(); k++) {
            char a = cls.charAt(k);
            if (k + 2 < cls.length() && cls.charAt(k + 1) == '-') {
                char b = cls.charAt(k + 2);
                if (a <= c && c <= b) return true;
                k += 2;
            } else {
                if (c == a) return true;
            }
        }
        return false;
    }
}