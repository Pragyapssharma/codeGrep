import java.util.*;

public class RegexMatcher {
    private List<Token> tokens;
    private final boolean anchored;
    private final boolean anchoredEnd;

    public RegexMatcher(String pattern) {
        if (pattern.startsWith("^")) {
            this.anchored = true;
            pattern = pattern.substring(1);
        } else {
            this.anchored = false;
        }

        if (pattern.endsWith("$")) {
            this.anchoredEnd = true;
            pattern = pattern.substring(0, pattern.length() - 1);
        } else {
            this.anchoredEnd = false;
        }

        this.tokens = tokenize(pattern);
    }

    public boolean matches(String input) {
        if (anchored) {
            return matchesAt(input, 0);
        } else {
            for (int i = 0; i <= input.length(); i++) {
                if (matchesAt(input, i)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean matchesAt(String input, int start) {
        return matchesRemaining(input, start, 0);
    }

    private boolean matchesRemaining(String input, int i, int j) {
        while (j < tokens.size()) {
            Token token = tokens.get(j);

            if (token.type == Token.TokenType.ALTERNATION) {
                for (List<Token> alt : token.alternatives) {
                    List<Token> savedTokens = this.tokens;
                    List<Token> combined = new ArrayList<>(alt);
                    combined.addAll(tokens.subList(j + 1, tokens.size()));
                    this.tokens = combined;
                    if (matchesRemaining(input, i, 0)) {
                        this.tokens = savedTokens;
                        return true;
                    }
                    this.tokens = savedTokens;
                }
                return false;
            }

            if (token.quantifier == Token.Quantifier.ONE) {
                if (i >= input.length() || !token.matches(input.charAt(i))) return false;
                i++;
                j++;
            } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                int begin = i;
                int count = 0;

                while (i < input.length() && token.matches(input.charAt(i))) {
                    i++;
                    count++;
                }

                if (count == 0) return false;

                for (int k = begin + 1; k <= i; k++) {
                    if (matchesRemaining(input, k, j + 1)) {
                        return true;
                    }
                }

                return false;
            } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                if (matchesRemaining(input, i, j + 1)) return true;

                if (i < input.length() && token.matches(input.charAt(i))) {
                    if (matchesRemaining(input, i + 1, j + 1)) return true;
                }

                return false;
            }
        }

        return !anchoredEnd || (i == input.length());
    }

    private List<Token> tokenize(String pattern) {
        List<Token> tokens = new ArrayList<>();
        for (int i = 0; i < pattern.length();) {
            char c = pattern.charAt(i);
            Token token;

            if (c == '(') {
                int end = findClosingParen(pattern, i);
                String group = pattern.substring(i + 1, end);
                List<List<Token>> alternatives = new ArrayList<>();
                for (String part : group.split("\\|")) {
                    alternatives.add(tokenize(part));
                }
                token = new Token(alternatives);
                i = end + 1;
            } else if (c == '\\' && i + 1 < pattern.length()) {
                char next = pattern.charAt(i + 1);
                if (next == 'd') {
                    token = new Token(Token.TokenType.DIGIT, "");
                } else if (next == 'w') {
                    token = new Token(Token.TokenType.WORD, "");
                } else {
                    token = new Token(Token.TokenType.CHAR, String.valueOf(next));
                }
                i += 2;
            } else if (c == '[') {
                int end = pattern.indexOf(']', i);
                if (end == -1) throw new RuntimeException("Unclosed [");
                String group = pattern.substring(i + 1, end);
                if (group.startsWith("^")) {
                    token = new Token(Token.TokenType.NEGATIVE_GROUP, group.substring(1));
                } else {
                    token = new Token(Token.TokenType.POSITIVE_GROUP, group);
                }
                i = end + 1;
            } else if (c == '.') {
                token = new Token(Token.TokenType.DOT, "");
                i++;
            } else {
                token = new Token(Token.TokenType.CHAR, String.valueOf(c));
                i++;
            }

            if (i < pattern.length()) {
                char next = pattern.charAt(i);
                if (next == '+') {
                    token.quantifier = Token.Quantifier.ONE_OR_MORE;
                    i++;
                } else if (next == '?') {
                    token.quantifier = Token.Quantifier.ZERO_OR_ONE;
                    i++;
                }
            }

            tokens.add(token);
        }
        return tokens;
    }

    private int findClosingParen(String pattern, int start) {
        int depth = 0;
        for (int i = start; i < pattern.length(); i++) {
            if (pattern.charAt(i) == '(') depth++;
            else if (pattern.charAt(i) == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        throw new RuntimeException("Unclosed (");
    }
}