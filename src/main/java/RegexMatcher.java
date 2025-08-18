import java.util.ArrayList;
import java.util.List;

public class RegexMatcher {
    private final List<Token> tokens;
    private final boolean anchoredStart;
    private final boolean anchoredEnd;
    private int nextGroupIndex = 1;

    public RegexMatcher(String pattern) {
        boolean aStart = false, aEnd = false;
        if (pattern.startsWith("^")) {
            aStart = true;
            pattern = pattern.substring(1);
        }
        if (pattern.endsWith("$")) {
            aEnd = true;
            pattern = pattern.substring(0, pattern.length() - 1);
        }
        this.anchoredStart = aStart;
        this.anchoredEnd = aEnd;
        this.tokens = tokenize(pattern);
    }

    public boolean matches(String input) {
        if (anchoredStart) {
            return matchesAt(input, 0);
        } else {
            for (int i = 0; i <= input.length(); i++) {
                if (matchesAt(input, i)) return true;
            }
            return false;
        }
    }

    private boolean matchesAt(String input, int start) {
        return matchesRemaining(input, start, 0, new Captures());
    }

    private boolean matchesRemaining(String input, int i, int j, Captures caps) {
        while (j < tokens.size()) {
            Token token = tokens.get(j);

            // Alternation group
            if (token.type == Token.TokenType.ALTERNATION) {
                if (token.quantifier == Token.Quantifier.ONE) {
                    for (List<Token> alt : token.alternatives) {
                        Captures temp = caps.copy();
                        int next = matchTokens(input, i, alt, temp);
                        if (next != -1) {
                            if (token.capturing) temp.set(token.groupIndex, i, next);
                            if (matchesRemaining(input, next, j + 1, temp)) {
                                caps.replaceWith(temp);
                                return true;
                            }
                        }
                    }
                    return false;

                } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                    // take it
                    for (List<Token> alt : token.alternatives) {
                        Captures temp = caps.copy();
                        int next = matchTokens(input, i, alt, temp);
                        if (next != -1) {
                            if (token.capturing) temp.set(token.groupIndex, i, next);
                            if (matchesRemaining(input, next, j + 1, temp)) {
                                caps.replaceWith(temp);
                                return true;
                            }
                        }
                    }
                    // or skip
                    return matchesRemaining(input, i, j + 1, caps);

                } else { // ONE_OR_MORE
                    int pos = i;
                    List<Integer> posHistory = new ArrayList<>();
                    List<Captures> capHistory = new ArrayList<>();

                    while (true) {
                        int bestNext = -1;
                        Captures bestCaps = null;
                        for (List<Token> alt : token.alternatives) {
                            Captures temp = caps.copy();
                            int next = matchTokens(input, pos, alt, temp);
                            if (next != -1 && next > pos) {
                                if (token.capturing) temp.set(token.groupIndex, pos, next);
                                if (next > bestNext) {
                                    bestNext = next;
                                    bestCaps = temp;
                                }
                            }
                        }
                        if (bestNext == -1) break;
                        posHistory.add(bestNext);
                        capHistory.add(bestCaps);
                        pos = bestNext;
                    }
                    if (posHistory.isEmpty()) return false;
                    j++;
                    for (int idx = posHistory.size() - 1; idx >= 0; idx--) {
                        int p = posHistory.get(idx);
                        Captures temp = capHistory.get(idx).copy();
                        if (anchoredEnd && j >= tokens.size()) {
                            if (p == input.length()) {
                                caps.replaceWith(temp);
                                return true;
                            }
                        } else if (matchesRemaining(input, p, j, temp)) {
                            caps.replaceWith(temp);
                            return true;
                        }
                    }
                    return false;
                }
            }

            if (token.type == Token.TokenType.GROUP) {
                if (token.quantifier == Token.Quantifier.ONE) {
                    int next = matchGroupOnce(input, i, token, caps);
                    if (next == -1) return false;
                    i = next;
                    j++;

                } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                    Captures takeCaps = caps.copy();
                    int next = matchGroupOnce(input, i, token, takeCaps);
                    if (next != -1) {
                        if (matchesRemaining(input, next, j + 1, takeCaps)) {
                            caps.replaceWith(takeCaps);
                            return true;
                        }
                    }
                    // skip
                    return matchesRemaining(input, i, j + 1, caps);

                } else { // ONE_OR_MORE
                    int pos = i;
                    int count = 0;
                    List<Integer> posHistory = new ArrayList<>();
                    List<Captures> capHistory = new ArrayList<>();
                    while (true) {
                        Captures temp = caps.copy();
                        int next = matchGroupOnce(input, pos, token, temp);
                        if (next == -1 || next == pos) break;
                        count++;
                        pos = next;
                        posHistory.add(pos);
                        capHistory.add(temp);
                    }
                    if (count == 0) return false;
                    j++;
                    for (int idx = posHistory.size() - 1; idx >= 0; idx--) {
                        Captures temp = capHistory.get(idx).copy();
                        int p = posHistory.get(idx);
                        if (anchoredEnd && j >= tokens.size()) {
                            if (p == input.length()) {
                                caps.replaceWith(temp);
                                return true;
                            }
                        } else if (matchesRemaining(input, p, j, temp)) {
                            caps.replaceWith(temp);
                            return true;
                        }
                    }
                    return false;
                }
                continue;
            }

            // Simple tokens
            if (token.quantifier == Token.Quantifier.ONE) {
                int next = token.matchOnce(input, i, caps);
                if (next == -1) return false;
                i = next;
                j++;

            } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                Captures takeCaps = caps.copy();
                int next = token.matchOnce(input, i, takeCaps);
                if (next != -1) {
                    if (matchesRemaining(input, next, j + 1, takeCaps)) {
                        caps.replaceWith(takeCaps);
                        return true;
                    }
                }
                // skip
                return matchesRemaining(input, i, j + 1, caps);

            } else { // ONE_OR_MORE
                List<Integer> posHistory = new ArrayList<>();
                List<Captures> capHistory = new ArrayList<>();
                int pos2 = i;
                while (true) {
                    Captures temp = caps.copy();
                    int next = token.matchOnce(input, pos2, temp);
                    if (next == -1 || next == pos2) break;
                    pos2 = next;
                    posHistory.add(pos2);
                    capHistory.add(temp);
                }
                if (posHistory.isEmpty()) return false;
                j++;
                for (int idx = posHistory.size() - 1; idx >= 0; idx--) {
                    Captures temp = capHistory.get(idx).copy();
                    int p = posHistory.get(idx);
                    if (j >= tokens.size()) {
                        if (anchoredEnd) {
                            if (p == input.length()) {
                                caps.replaceWith(temp);
                                return true;
                            }
                        } else {
                            caps.replaceWith(temp);
                            return true;
                        }
                    } else if (matchesRemaining(input, p, j, temp)) {
                        caps.replaceWith(temp);
                        return true;
                    }
                }
                return false;
            }
        }

        return !anchoredEnd || (i == input.length());
    }

    private int matchGroupOnce(String input, int i, Token groupToken, Captures caps) {
        // Work directly on the live captures so nested backrefs see inner groups
        int res = matchTokens(input, i, groupToken.groupTokens, caps);
        if (res == -1) return -1;
        if (groupToken.capturing) {
            caps.set(groupToken.groupIndex, i, res);
        }
        return res;
    }
    
    private int matchAtomOnce(String input, int pos, Token token, Captures caps) {
        if (token.type == Token.TokenType.GROUP) {
            return matchGroupOnce(input, pos, token, caps);
        }
        return token.matchOnce(input, pos, caps);
    }

    private int matchTokens(String input, int i, List<Token> groupTokens, Captures caps) {
        // IMPORTANT: operate on live 'caps' so inner groups are visible to following backrefs
        int j = 0;
        int pos = i;

        while (j < groupTokens.size()) {
            Token token = groupTokens.get(j);

            if (token.type == Token.TokenType.ALTERNATION) {
                List<Token> remainder = groupTokens.subList(j + 1, groupTokens.size());

                if (token.quantifier == Token.Quantifier.ONE) {
                    for (List<Token> altBranch : token.alternatives) {
                        Captures branchCaps = caps.copy();
                        int mid = matchTokens(input, pos, altBranch, branchCaps);
                        if (mid == -1) continue;
                        if (token.capturing) branchCaps.set(token.groupIndex, pos, mid);
                        int endPos = matchTokens(input, mid, remainder, branchCaps);
                        if (endPos != -1) {
                            caps.replaceWith(branchCaps);
                            return endPos;
                        }
                    }
                    return -1;

                } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                    for (List<Token> altBranch : token.alternatives) {
                        Captures branchCaps = caps.copy();
                        int mid = matchTokens(input, pos, altBranch, branchCaps);
                        if (mid == -1) continue;
                        if (token.capturing) branchCaps.set(token.groupIndex, pos, mid);
                        int endPos = matchTokens(input, mid, remainder, branchCaps);
                        if (endPos != -1) {
                            caps.replaceWith(branchCaps);
                            return endPos;
                        }
                    }
                    // skip alternation and continue with remainder
                    j++;
                    continue;

                } else { // ONE_OR_MORE
                    List<Integer> posHistory = new ArrayList<>();
                    List<Captures> capHistory = new ArrayList<>();
                    int cur = pos;
                    boolean firstMatched = false;

                    while (true) {
                        int bestNext = -1;
                        Captures bestCaps = null;
                        for (List<Token> altBranch : token.alternatives) {
                            Captures branchCaps = caps.copy();
                            int mid = matchTokens(input, cur, altBranch, branchCaps);
                            if (mid != -1 && mid > cur) {
                                if (token.capturing) branchCaps.set(token.groupIndex, cur, mid);
                                if (mid > bestNext) {
                                    bestNext = mid;
                                    bestCaps = branchCaps;
                                }
                            }
                        }
                        if (bestNext == -1) break;
                        firstMatched = true;
                        posHistory.add(bestNext);
                        capHistory.add(bestCaps);
                        cur = bestNext;
                        caps.replaceWith(bestCaps);
                    }
                    if (!firstMatched) return -1;

                    for (int idx = posHistory.size() - 1; idx >= 0; idx--) {
                        int after = posHistory.get(idx);
                        Captures ccap = capHistory.get(idx).copy();
                        Captures saved = caps.copy();
                        caps.replaceWith(ccap);
                        int endPos = matchTokens(input, after, remainder, caps);
                        if (endPos != -1) {
                            return endPos;
                        }
                        caps.replaceWith(saved);
                    }
                    return -1;
                }
            }

            if (token.type == Token.TokenType.GROUP) {
                int result = matchGroupOnce(input, pos, token, caps);
                if (result == -1) return -1;
                pos = result;
                j++;
                continue;
            }
            
         // Generic atom handling (group or non-group) with quantifiers
            List<Token> remainder = groupTokens.subList(j + 1, groupTokens.size());

            if (token.quantifier == Token.Quantifier.ONE) {
            	int np = matchAtomOnce(input, pos, token, caps);
                if (np == -1) return -1;
                pos = np;
                j++;
                continue;

            } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
            	Captures takeCaps = caps.copy();
            	int np = matchAtomOnce(input, pos, token, takeCaps);
                if (np != -1) {
                	int endPos = matchTokens(input, np, remainder, takeCaps);
                    if (endPos != -1) {
                        caps.replaceWith(takeCaps);
                        return endPos;
                    }
                }
                j++;

            } else { // ONE_OR_MORE
                int count = 0;
                List<Integer> posHistory = new ArrayList<>();
                List<Captures> capHistory = new ArrayList<>();
                int cur = pos;
                while (true) {
                    Captures temp = caps.copy();
                    int np = matchAtomOnce(input, cur, token, temp);
                    if (np == -1 || np == cur) break;
                    cur = np;
                    posHistory.add(cur);
                    capHistory.add(temp);
                }
                if (posHistory.isEmpty()) return -1;

                // Backtrack repetitions against the remainder
                for (int idx = posHistory.size() - 1; idx >= 0; idx--) {
                    Captures branchCaps = capHistory.get(idx).copy();
                    int after = posHistory.get(idx);
                    int endPos = matchTokens(input, after, remainder, branchCaps);
                    if (endPos != -1) {
                        caps.replaceWith(branchCaps);
                        return endPos;
                    }
                }
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

                // Split by top-level |
                List<List<Token>> alternatives = new ArrayList<>();
                int last = 0;
                int depth = 0;
                for (int j = 0; j <= group.length(); j++) {
                    if (j == group.length() || (group.charAt(j) == '|' && depth == 0)) {
                        String part = group.substring(last, j);
                        alternatives.add(tokenize(part));
                        last = j + 1;
                    } else if (group.charAt(j) == '(') {
                        depth++;
                    } else if (group.charAt(j) == ')') {
                        depth--;
                    } else if (group.charAt(j) == '[') {
                        int close = findClosingBracket(group, j);
                        j = close;
                    }
                }

                if (alternatives.size() == 1) {
                    token = new Token(alternatives.get(0), Token.TokenType.GROUP);
                } else {
                    token = new Token(alternatives);
                }
                token.capturing = true;
                token.groupIndex = nextGroupIndex++;
                i = end + 1;

            } else if (c == '\\' && i + 1 < pattern.length()) {
                int j = i + 1;
                char next = pattern.charAt(j);
                if (next == 'd') {
                    token = new Token(Token.TokenType.DIGIT, "");
                    i += 2;
                } else if (next == 'w') {
                    token = new Token(Token.TokenType.WORD, "");
                    i += 2;
                } else if (Character.isDigit(next)) {
                    // multi-digit backrefs supported
                    int start = j;
                    while (j < pattern.length() && Character.isDigit(pattern.charAt(j))) j++;
                    int refNum = Integer.parseInt(pattern.substring(start, j));
                    token = new Token(refNum);
                    i = j;
                } else {
                    token = new Token(Token.TokenType.CHAR, String.valueOf(next));
                    i += 2;
                }

            } else if (c == '[') {
                int end = findClosingBracket(pattern, i);
                String cls = pattern.substring(i + 1, end);
                if (cls.startsWith("^")) {
                    token = new Token(Token.TokenType.NEGATIVE_GROUP, cls.substring(1));
                } else {
                    token = new Token(Token.TokenType.POSITIVE_GROUP, cls);
                }
                i = end + 1;

            } else if (c == '.') {
                token = new Token(Token.TokenType.DOT, "");
                i++;

            } else {
                token = new Token(Token.TokenType.CHAR, String.valueOf(c));
                i++;
            }

            // Quantifiers
            if (i < pattern.length()) {
                char q = pattern.charAt(i);
                if (q == '+') { token.quantifier = Token.Quantifier.ONE_OR_MORE; i++; }
                else if (q == '?') { token.quantifier = Token.Quantifier.ZERO_OR_ONE; i++; }
            }

            tokens.add(token);
        }
        return tokens;
    }

    private int findClosingParen(String pattern, int start) {
        int depth = 0;
        for (int i = start; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            } else if (c == '[') {
                i = findClosingBracket(pattern, i);
            }
        }
        throw new RuntimeException("Unclosed parenthesis in pattern");
    }

    private int findClosingBracket(String pattern, int start) {
        for (int i = start + 1; i < pattern.length(); i++) {
            if (pattern.charAt(i) == ']') return i;
        }
        throw new RuntimeException("Unclosed [ in pattern");
    }
}