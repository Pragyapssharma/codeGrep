import java.util.*;

public class RegexMatcher {
    private List<Token> tokens;
    private final boolean anchored;
    private final boolean anchoredEnd;

    // Track if we've assigned the single capturing group already
    private boolean firstCaptureAssigned = false;

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
        Captures caps = new Captures();
        if (anchored) {
            return matchesAt(input, 0, caps);
        } else {
            for (int i = 0; i <= input.length(); i++) {
                if (matchesAt(input, i, caps.copy())) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean matchesAt(String input, int start, Captures caps) {
        return matchesRemaining(input, start, 0, caps);
    }

    private boolean matchesRemaining(String input, int i, int j, Captures caps) {
        while (j < tokens.size()) {
            Token token = tokens.get(j);

            // Debug (stdout shown by grader; use stderr if needed)
            System.out.println("Matching token: " + token.type + " at input pos: " + i);
            System.out.println("Current input: " + (i < input.length() ? input.substring(i) : "EOF"));

            // Top-level alternation (rare unless pattern uses '|' without parentheses)
            if (token.type == Token.TokenType.ALTERNATION) {
                for (List<Token> alt : token.alternatives) {
                    List<Token> combined = new ArrayList<>(alt);
                    combined.addAll(tokens.subList(j + 1, tokens.size()));
                    Captures tmp = caps.copy();
                    if (matchesAlternative(input, i, combined, tmp)) {
                        // propagate captures from successful branch
                        caps.start = tmp.start; caps.end = tmp.end;
                        return true;
                    }
                }
                return false;
            }

            // Groups
            if (token.type == Token.TokenType.GROUP) {
                if (token.quantifier == Token.Quantifier.ONE) {
                    int next = advanceGroup(input, i, token.groupTokens, caps);
                    if (next == -1) return false;

                    Captures saved = caps.copy();
                    if (token.capturing) { caps.start = i; caps.end = next; }

                    boolean ok = matchesRemaining(input, next, j + 1, caps);
                    if (!ok) { caps.start = saved.start; caps.end = saved.end; }
                    return ok;

                } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                    // Greedy, then backtrack over number of repetitions
                    List<Integer> ends = new ArrayList<>();
                    List<Captures> capsHistory = new ArrayList<>();
                    int pos2 = i;
                    Captures cur = caps.copy();

                    while (true) {
                        int next = advanceGroup(input, pos2, token.groupTokens, cur);
                        if (next == -1 || next == pos2) break;
                        pos2 = next;
                        // if capturing, capture the latest full span from original i
                        Captures snapshot = cur.copy();
                        if (token.capturing) { snapshot.start = i; snapshot.end = pos2; }
                        ends.add(pos2);
                        capsHistory.add(snapshot);
                        if (anchoredEnd && j + 1 == tokens.size() && pos2 == input.length()) {
                            // accept immediately if end-anchored and fully consumed
                            caps.start = snapshot.start; caps.end = snapshot.end;
                            return true;
                        }
                    }
                    if (ends.isEmpty()) return false;

                    for (int k = ends.size() - 1; k >= 0; k--) {
                        if (matchesRemaining(input, ends.get(k), j + 1, capsHistory.get(k))) {
                            caps.start = capsHistory.get(k).start; caps.end = capsHistory.get(k).end;
                            return true;
                        }
                    }
                    return false;

                } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                    // try with
                    int next = advanceGroup(input, i, token.groupTokens, caps);
                    if (next != -1) {
                        Captures saved = caps.copy();
                        if (token.capturing) { caps.start = i; caps.end = next; }
                        if (matchesRemaining(input, next, j + 1, caps)) {
                            return true;
                        }
                        // backtrack capture
                        caps.start = saved.start; caps.end = saved.end;
                    }
                    // try without
                    return matchesRemaining(input, i, j + 1, caps);
                }
                continue;
            }

            // Backreference token (may consume multiple characters)
            if (token.type == Token.TokenType.BACKREF) {
                if (token.quantifier == Token.Quantifier.ONE) {
                    int next = token.matchOnce(input, i, caps);
                    if (next == -1) return false;
                    i = next; j++;
                } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                    int next = token.matchOnce(input, i, caps);
                    if (next != -1 && matchesRemaining(input, next, j + 1, caps)) return true;
                    return matchesRemaining(input, i, j + 1, caps);
                } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                    // Greedy then backtrack
                    List<Integer> ends = new ArrayList<>();
                    int pos2 = i;
                    int step = token.matchOnce(input, pos2, caps);
                    if (step == -1) return false; // need at least one
                    pos2 = step; ends.add(pos2);
                    while (true) {
                        int step2 = token.matchOnce(input, pos2, caps);
                        if (step2 == -1 || step2 == pos2) break;
                        pos2 = step2; ends.add(pos2);
                        if (anchoredEnd && j + 1 == tokens.size() && pos2 == input.length()) {
                            return true;
                        }
                    }
                    for (int k = ends.size() - 1; k >= 0; k--) {
                        if (matchesRemaining(input, ends.get(k), j + 1, caps)) return true;
                    }
                    return false;
                }
                continue;
            }

            // Char-like tokens (single char consumption)
            if (token.quantifier == Token.Quantifier.ONE) {
                if (i >= input.length() || !token.matches(input.charAt(i))) return false;
                i++; j++;
            } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                // Greedy, then backtrack, respecting next tokens
                int maxPos = i;
                while (maxPos < input.length() && token.matches(input.charAt(maxPos))) {
                    maxPos++;
                }
                for (int pos2 = maxPos; pos2 >= i + 1; pos2--) {
                    if (j + 1 == tokens.size()) {
                        if (anchoredEnd) {
                            if (pos2 == input.length()) return true;
                        } else {
                            return true;
                        }
                    } else if (matchesRemaining(input, pos2, j + 1, caps)) {
                        return true;
                    }
                }
                return false;

            } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                if (i < input.length() && token.matches(input.charAt(i))) {
                    if (matchesRemaining(input, i + 1, j + 1, caps)) return true;
                }
                return matchesRemaining(input, i, j + 1, caps);
            }
        }

        return !anchoredEnd || (i == input.length());
    }

    private boolean matchesAlternative(String input, int i, List<Token> altTokens, Captures caps) {
        List<Token> savedTokens = this.tokens;
        this.tokens = altTokens;
        boolean result = matchesRemaining(input, i, 0, caps);
        this.tokens = savedTokens;
        return result;
    }

    private boolean matchGroup(String input, int i, List<Token> groupTokens, Captures caps) {
        return matchTokens(input, i, groupTokens, caps) != -1;
    }

    private int advanceGroup(String input, int i, List<Token> groupTokens, Captures caps) {
        System.out.println("in advanceGroup: " + input + " at input i: " + i + " tokengroup: " + groupTokens);
        return matchTokens(input, i, groupTokens, caps);
    }

    private int matchTokens(String input, int i, List<Token> groupTokens, Captures caps) {
        int j = 0;
        int pos = i;
        while (j < groupTokens.size()) {
            Token token = groupTokens.get(j);

            System.out.println("Matching token in group: " + token.type + " at input pos: " + pos);
            System.out.println("Current input: " + (pos < input.length() ? input.substring(pos) : "EOF"));

            // Alternation inside group: try branches + remainder; respect ZERO_OR_ONE
            if (token.type == Token.TokenType.ALTERNATION) {
                boolean optional = (token.quantifier == Token.Quantifier.ZERO_OR_ONE);
                int bestPos = -1;
                Captures bestCaps = null;

                for (List<Token> altBranch : token.alternatives) {
                    List<Token> trialChain = new ArrayList<>(altBranch);
                    trialChain.addAll(groupTokens.subList(j + 1, groupTokens.size()));
                    Captures tmp = caps.copy();
                    int matchPos = matchTokens(input, pos, trialChain, tmp);
                    if (matchPos > bestPos) {
                        bestPos = matchPos;
                        bestCaps = tmp;
                    }
                }
                if (bestPos != -1) {
                    if (bestCaps != null) { caps.start = bestCaps.start; caps.end = bestCaps.end; }
                    return bestPos;
                } else if (optional) {
                    j++; // skip alternation entirely
                    continue;
                } else {
                    return -1;
                }
            }

            // Nested group inside group
            if (token.type == Token.TokenType.GROUP) {
                int result = matchTokens(input, pos, token.groupTokens, caps);
                if (result == -1) return -1;

                // If this nested group is the capturing one, record span
                Captures saved = caps.copy();
                if (token.capturing) { caps.start = pos; caps.end = result; }

                pos = result;
                j++;
                continue;
            }

            // Backreference inside group
            if (token.type == Token.TokenType.BACKREF) {
                if (token.quantifier == Token.Quantifier.ONE) {
                    int next = token.matchOnce(input, pos, caps);
                    if (next == -1) return -1;
                    pos = next; j++;
                } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                    int next = token.matchOnce(input, pos, caps);
                    if (next != -1) pos = next;
                    j++;
                } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                    int count = 0;
                    int next;
                    // at least one
                    next = token.matchOnce(input, pos, caps);
                    if (next == -1) return -1;
                    pos = next; count++;
                    // more
                    while (true) {
                        int more = token.matchOnce(input, pos, caps);
                        if (more == -1 || more == pos) break;
                        pos = more; count++;
                    }
                    if (count == 0) return -1;
                    j++;
                } else {
                    return -1;
                }
                continue;
            }

            // Char-like tokens
            if (token.quantifier == Token.Quantifier.ONE) {
                if (pos >= input.length() || !token.matches(input.charAt(pos))) return -1;
                pos++;
                j++;

            } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                if (pos < input.length() && token.matches(input.charAt(pos))) {
                    pos++;
                }
                j++;

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

    // ---------------------------
    // Tokenizer (adds BACKREF and capturing)
    // ---------------------------
    private List<Token> tokenize(String pattern) {
        List<Token> tokens = new ArrayList<>();
        for (int i = 0; i < pattern.length();) {
            char c = pattern.charAt(i);
            Token token;

            if (c == '(') {
                int end = findClosingParen(pattern, i);
                String group = pattern.substring(i + 1, end);

                // Split group into alternatives at top-level '|'
                List<List<Token>> alternatives = new ArrayList<>();
                int lastSplit = 0;
                int depth = 0;
                for (int j = 0; j <= group.length(); j++) {
                    if (j == group.length() || (j < group.length() && group.charAt(j) == '|' && depth == 0)) {
                        String part = group.substring(lastSplit, j);
                        alternatives.add(tokenize(part)); // recursive tokenize
                        lastSplit = j + 1;
                    } else if (group.charAt(j) == '(') {
                        depth++;
                    } else if (group.charAt(j) == ')') {
                        depth--;
                    }
                }

                // Always wrap parentheses as a GROUP so we can capture the whole span.
                List<Token> groupTokens = new ArrayList<>();
                if (alternatives.size() == 1) {
                    groupTokens.addAll(alternatives.get(0));
                } else {
                    groupTokens.add(new Token(alternatives)); // an ALTERNATION token inside the group
                }
                token = new Token(groupTokens, Token.TokenType.GROUP);

                // Mark the first group as capturing
                if (!firstCaptureAssigned) {
                    token.capturing = true;
                    token.groupIndex = 1;
                    firstCaptureAssigned = true;
                }

                i = end + 1;

            } else if (c == '\\' && i + 1 < pattern.length()) {
                char next = pattern.charAt(i + 1);
                if (next == 'd') {
                    token = new Token(Token.TokenType.DIGIT, "");
                } else if (next == 'w') {
                    token = new Token(Token.TokenType.WORD, "");
                } else if (next == '1') {
                    token = new Token(1); // BACKREF \1
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

            // Unified quantifier handling for all tokens (including groups/backref)
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
            if (pattern.charAt(i) == '(') {
                depth++;
            } else if (pattern.charAt(i) == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new RuntimeException("Unclosed parenthesis in pattern");
    }
}