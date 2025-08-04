import java.util.*;

public class RegexMatcher {
    private List<Token> tokens;
    private boolean anchored;
    private boolean anchoredEnd;

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
        if (j == tokens.size()) {
            return !anchoredEnd || i == input.length();
        }

        Token token = tokens.get(j);

        // Debug print
        System.out.println("Matching token: " + token.type + " at input pos: " + i);
        System.out.println("Current input: " + (i < input.length() ? input.substring(i) : "EOF"));

        if (token.type == Token.TokenType.ALTERNATION) {
            for (List<Token> alt : token.alternatives) {
                List<Token> combined = new ArrayList<>(alt);
                combined.addAll(tokens.subList(j + 1, tokens.size()));
                RegexMatcher altMatcher = new RegexMatcher("");
                altMatcher.tokens = combined;
                altMatcher.anchoredEnd = this.anchoredEnd;
                altMatcher.anchored = true;
                if (altMatcher.matchesRemaining(input, i, 0)) {
                    return true;
                }
            }
            return false;
        }

        if (token.type == Token.TokenType.GROUP) {
            if (token.quantifier == Token.Quantifier.ONE) {
                int posAfter = matchTokens(input, i, token.groupTokens);
                if (posAfter == -1) return false;
                return matchesRemaining(input, posAfter, j + 1);
            } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                // Try to match group as many times as possible, backtracking on failure
                int pos = i;
                List<Integer> positions = new ArrayList<>();
                while (true) {
                    int nextPos = matchTokens(input, pos, token.groupTokens);
                    if (nextPos == -1) break;
                    positions.add(nextPos);
                    pos = nextPos;
                    if (pos == i) break; // avoid infinite loop if group matches empty string
                }
                // Now backtrack trying fewer repetitions
                for (int count = positions.size(); count >= 1; count--) {
                    int tryPos = positions.get(count - 1);
                    if (matchesRemaining(input, tryPos, j + 1)) return true;
                }
                return false;
            } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                // Try skipping group
                if (matchesRemaining(input, i, j + 1)) return true;
                // Try matching group once
                int posAfter = matchTokens(input, i, token.groupTokens);
                if (posAfter != -1) {
                    if (matchesRemaining(input, posAfter, j + 1)) return true;
                }
                return false;
            }
        }

        // Handle CHAR, DIGIT, WORD, DOT, etc.
        if (token.quantifier == Token.Quantifier.ONE) {
            if (i >= input.length() || !token.matches(input.charAt(i))) return false;
            return matchesRemaining(input, i + 1, j + 1);
        } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
            int pos = i;
            List<Integer> positions = new ArrayList<>();
            while (pos < input.length() && token.matches(input.charAt(pos))) {
                positions.add(pos + 1);
                pos++;
            }
            if (positions.isEmpty()) return false;
            // Backtrack on number of matches
            for (int count = positions.size(); count >= 1; count--) {
                int tryPos = positions.get(count - 1);
                if (matchesRemaining(input, tryPos, j + 1)) return true;
            }
            return false;
        } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
            // Try skipping token
            if (matchesRemaining(input, i, j + 1)) return true;
            // Try matching token once
            if (i < input.length() && token.matches(input.charAt(i))) {
                if (matchesRemaining(input, i + 1, j + 1)) return true;
            }
            return false;
        }

        return false;
    }
    
    private int matchTokens(String input, int i, List<Token> groupTokens) {
        return matchTokensHelper(input, i, groupTokens, 0);
    }

    private int matchTokensHelper(String input, int pos, List<Token> groupTokens, int idx) {
        if (idx == groupTokens.size()) return pos;

        Token token = groupTokens.get(idx);

        // Debug print
        System.out.println("Matching token in group: " + token.type + " at input pos: " + pos);
        System.out.println("Current input: " + (pos < input.length() ? input.substring(pos) : "EOF"));

        if (token.type == Token.TokenType.ALTERNATION) {
            for (List<Token> alt : token.alternatives) {
                int res = matchTokensHelper(input, pos, alt, 0);
                if (res != -1) {
                    int afterAlt = matchTokensHelper(input, res, groupTokens, idx + 1);
                    if (afterAlt != -1) return afterAlt;
                }
            }
            return -1;
        }

        if (token.type == Token.TokenType.GROUP) {
            if (token.quantifier == Token.Quantifier.ONE) {
                int res = matchTokens(input, pos, token.groupTokens);
                if (res == -1) return -1;
                return matchTokensHelper(input, res, groupTokens, idx + 1);
            } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                // Greedy with backtracking
                int currentPos = pos;
                List<Integer> positions = new ArrayList<>();
                while (true) {
                    int nextPos = matchTokens(input, currentPos, token.groupTokens);
                    if (nextPos == -1) break;
                    if (nextPos == currentPos) break; // prevent infinite loop on empty matches
                    positions.add(nextPos);
                    currentPos = nextPos;
                }
                for (int count = positions.size(); count >= 1; count--) {
                    int tryPos = positions.get(count - 1);
                    int afterGroup = matchTokensHelper(input, tryPos, groupTokens, idx + 1);
                    if (afterGroup != -1) return afterGroup;
                }
                return -1;
            } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                // Try skip group
                int skipRes = matchTokensHelper(input, pos, groupTokens, idx + 1);
                if (skipRes != -1) return skipRes;
                // Try match group once
                int matchRes = matchTokens(input, pos, token.groupTokens);
                if (matchRes != -1) {
                    int afterMatch = matchTokensHelper(input, matchRes, groupTokens, idx + 1);
                    if (afterMatch != -1) return afterMatch;
                }
                return -1;
            }
        }

        // Handle tokens with quantifiers ONE, ONE_OR_MORE, ZERO_OR_ONE
        if (token.quantifier == Token.Quantifier.ONE) {
            if (pos >= input.length() || !token.matches(input.charAt(pos))) return -1;
            return matchTokensHelper(input, pos + 1, groupTokens, idx + 1);
        } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
            int currentPos = pos;
            List<Integer> positions = new ArrayList<>();
            while (currentPos < input.length() && token.matches(input.charAt(currentPos))) {
                currentPos++;
                positions.add(currentPos);
            }
            if (positions.isEmpty()) return -1;
            for (int count = positions.size(); count >= 1; count--) {
                int tryPos = positions.get(count - 1);
                int afterToken = matchTokensHelper(input, tryPos, groupTokens, idx + 1);
                if (afterToken != -1) return afterToken;
            }
            return -1;
        } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
            // Try skip
            int skipRes = matchTokensHelper(input, pos, groupTokens, idx + 1);
            if (skipRes != -1) return skipRes;
            // Try match token once
            if (pos < input.length() && token.matches(input.charAt(pos))) {
                int matchRes = matchTokensHelper(input, pos + 1, groupTokens, idx + 1);
                if (matchRes != -1) return matchRes;
            }
            return -1;
        }

        return -1;
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