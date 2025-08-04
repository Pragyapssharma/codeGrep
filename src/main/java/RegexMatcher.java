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
        boolean result = matchesRemaining(input, start, 0);
        // If anchoredEnd is true, ensure full match to end of input
        if (result && anchoredEnd) {
            // matchesRemaining only returns true if all tokens matched to the end,
            // but in unanchored mode it might match partial suffix.
            // So if anchoredEnd is set, force i == input.length()
            // This is handled in matchesRemaining by returning i == input.length(),
            // so no extra check here needed.
        }
        return result;
    }

    private boolean matchesRemaining(String input, int i, int j) {
        while (j < tokens.size()) {
            Token token = tokens.get(j);

            System.out.println("Matching token: " + token.type + " at input pos: " + i);
            System.out.println("Current input: " + input.substring(i));

            // Handle alternation
            if (token.type == Token.TokenType.ALTERNATION) {
                for (List<Token> alt : token.alternatives) {
                    List<Token> combined = new ArrayList<>(alt);
                    combined.addAll(tokens.subList(j + 1, tokens.size()));
                    if (matchesAlternative(input, i, combined)) {
                        return true;
                    }
                }
                return false;
            }

            // Handle group
            if (token.type == Token.TokenType.GROUP) {
                if (token.quantifier == Token.Quantifier.ONE) {
                    if (!matchGroup(input, i, token.groupTokens)) return false;
                    i = advanceGroup(input, i, token.groupTokens);
                    j++;
                } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                    int count = 0;
                    while (matchGroup(input, i, token.groupTokens)) {
                        i = advanceGroup(input, i, token.groupTokens);
                        count++;
                    }
                    if (count == 0) return false;
                    j++;
                } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                    if (matchGroup(input, i, token.groupTokens)) {
                        int next = advanceGroup(input, i, token.groupTokens);
                        if (matchesRemaining(input, next, j + 1)) return true;
                    }
                    j++;
                    continue;
                }
                continue;
            }

            // Handle CHAR tokens
            if (token.quantifier == Token.Quantifier.ONE) {
                if (i >= input.length() || !token.matches(input.charAt(i))) return false;
                i++;
                j++;
            } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                int count = 0;
                while (i < input.length() && token.matches(input.charAt(i))) {
                    i++;
                    count++;
                }
                if (count == 0) return false;
                j++;
            } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
            	if (matchesRemaining(input, i, j + 1)) return true;

                if (i < input.length() && token.matches(input.charAt(i))) {
                    if (matchesRemaining(input, i + 1, j + 1)) return true;
                }

                return false;
            }
        }

        return i == input.length();
    }
    
    
    private boolean matchesAlternative(String input, int i, List<Token> altTokens) {
        List<Token> savedTokens = this.tokens;
        this.tokens = altTokens;
        boolean result = matchesRemaining(input, i, 0);
        this.tokens = savedTokens;
        return result;
    }
    
    
    private boolean matchGroup(String input, int i, List<Token> groupTokens) {
        return matchTokens(input, i, groupTokens) != -1;
    }

    private int advanceGroup(String input, int i, List<Token> groupTokens) {
        return matchTokens(input, i, groupTokens);
    }

    private int matchTokens(String input, int i, List<Token> groupTokens) {
        int j = 0;
        int pos = i;
        while (j < groupTokens.size()) {
            Token token = groupTokens.get(j);
            
            System.out.println("Matching token: " + token.type + " at input pos: " + i);
            System.out.println("Current input: " + input.substring(i));


            if (token.type == Token.TokenType.ALTERNATION) {
                boolean matched = false;
                for (List<Token> alt : token.alternatives) {
                    int result = matchTokens(input, pos, alt);
                    if (result != -1) {
                        pos = result;
                        matched = true;
                        break;
                    }
                }
                if (!matched) return -1;
                j++;
            } else if (token.type == Token.TokenType.GROUP) {
            	if (token.quantifier == Token.Quantifier.ONE) {
                    int result = matchTokens(input, pos, token.groupTokens);
                    if (result == -1) return -1;
                    pos = result;
                    j++;
                } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                    int count = 0;
                    while (true) {
                        int result = matchTokens(input, pos, token.groupTokens);
                        if (result == -1) break;
                        pos = result;
                        count++;
                    }
                    if (count == 0) return -1;
                    j++;
                } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                    int skipResult = pos;
                    int matchResult = matchTokens(input, pos, token.groupTokens);

                    if (matchResult != -1) {
                        int afterMatch = matchTokens(input, matchResult, groupTokens.subList(j + 1, groupTokens.size()));
                        if (afterMatch != -1) return afterMatch;
                    }
                    j++;
                    continue;
                } else {
                    return -1;
                }
            } else if (token.quantifier == Token.Quantifier.ONE) {
                if (pos >= input.length() || !token.matches(input.charAt(pos))) return -1;
                pos++;
                j++;
            } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
            	int skipPos = matchTokens(input, pos, groupTokens.subList(j + 1, groupTokens.size()));
                if (skipPos != -1) return skipPos;

                if (pos < input.length() && token.matches(input.charAt(pos))) {
                    int matchPos = matchTokens(input, pos + 1, groupTokens.subList(j + 1, groupTokens.size()));
                    if (matchPos != -1) return matchPos;
                }

                return -1;
            } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                int count = 0;
                while (pos < input.length() && token.matches(input.charAt(pos))) {
                    pos++;
                    count++;
                }
                if (count == 0) return -1;
                j++;
            } else {
                return -1;
            }
        }
        return pos;
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
                int lastSplit = 0;
                int depth = 0;

                for (int j = 0; j <= group.length(); j++) {
                    if (j == group.length() || (group.charAt(j) == '|' && depth == 0)) {
                        String part = group.substring(lastSplit, j);
                        alternatives.add(tokenize(part));
                        lastSplit = j + 1;
                    } else if (group.charAt(j) == '(') {
                        depth++;
                    } else if (group.charAt(j) == ')') {
                        depth--;
                    }
                }

                if (alternatives.size() == 1) {
                    token = new Token(alternatives.get(0), Token.TokenType.GROUP);
                } else {
                    token = new Token(alternatives);
                }
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
            char c = pattern.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        throw new RuntimeException("Unclosed (");
    }
    

    
}