import java.util.*;

public class RegexMatcher {
    private List<Token> tokens;
    private final boolean anchored;
    private final boolean anchoredEnd;

    // Track whether we've already marked the first capturing group
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
        return matchesRemaining(input, start, 0, new Captures());
    }

    private boolean matchesRemaining(String input, int i, int j, Captures caps) {
        while (j < tokens.size()) {
            Token token = tokens.get(j);

            // Handle an alternation token (i.e., (...) with branches)
            if (token.type == Token.TokenType.ALTERNATION) {
                if (token.quantifier == Token.Quantifier.ONE) {
                    for (List<Token> alt : token.alternatives) {
                        Captures temp = caps.copy();
                        int next = matchTokens(input, i, alt, temp);
                        if (next != -1) {
                            if (token.capturing) {
                                temp.start = i;
                                temp.end = next;
                            }
                            if (matchesRemaining(input, next, j + 1, temp)) {
                                caps.start = temp.start;
                                caps.end = temp.end;
                                return true;
                            }
                        }
                    }
                    return false;
                } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                    // try taking one
                    for (List<Token> alt : token.alternatives) {
                        Captures temp = caps.copy();
                        int next = matchTokens(input, i, alt, temp);
                        if (next != -1) {
                            if (token.capturing) {
                                temp.start = i;
                                temp.end = next;
                            }
                            if (matchesRemaining(input, next, j + 1, temp)) {
                                caps.start = temp.start;
                                caps.end = temp.end;
                                return true;
                            }
                        }
                    }
                    // or skip
                    return matchesRemaining(input, i, j + 1, caps);
                } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                    // Greedily repeat alternation, then backtrack
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
                                if (token.capturing) {
                                    temp.start = pos;
                                    temp.end = next;
                                }
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
                        // update running capture to the latest repetition
                        caps.start = bestCaps.start;
                        caps.end = bestCaps.end;
                    }

                    if (posHistory.isEmpty()) return false;
                    j++;
                    // Backtrack over repetition count
                    for (int idx = posHistory.size() - 1; idx >= 0; idx--) {
                        int p = posHistory.get(idx);
                        Captures temp = capHistory.get(idx);
                        Captures saved = caps.copy();
                        caps.start = temp.start;
                        caps.end = temp.end;
                        if (matchesRemaining(input, p, j, caps)) {
                            return true;
                        }
                        caps.start = saved.start;
                        caps.end = saved.end;
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
                } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
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
                    // Greedy backoff
                    for (int idx = posHistory.size() - 1; idx >= 0; idx--) {
                        Captures temp = capHistory.get(idx);
                        int p = posHistory.get(idx);
                        Captures saved = caps.copy();
                        caps.start = temp.start;
                        caps.end = temp.end;
                        if (anchoredEnd && j >= tokens.size()) {
                            if (p == input.length()) return true;
                        } else if (matchesRemaining(input, p, j, caps)) {
                            return true;
                        }
                        // restore and try fewer repetitions
                        caps.start = saved.start;
                        caps.end = saved.end;
                    }
                    return false;

                } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                    Captures takeCaps = caps.copy();
                    int next = matchGroupOnce(input, i, token, takeCaps);
                    if (next != -1) {
                        if (matchesRemaining(input, next, j + 1, takeCaps)) {
                            caps.start = takeCaps.start;
                            caps.end = takeCaps.end;
                            return true;
                        }
                    }
                    // try skipping the group
                    return matchesRemaining(input, i, j + 1, caps);
                }
                continue;
            }

            // Handle simple tokens (including BACKREF) with quantifiers
            if (token.quantifier == Token.Quantifier.ONE) {
                int next = token.matchOnce(input, i, caps);
                if (next == -1) return false;
                i = next;
                j++;
            } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
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
                // Greedy backoff over the number of repetitions
                for (int idx = posHistory.size() - 1; idx >= 0; idx--) {
                    Captures temp = capHistory.get(idx);
                    int p = posHistory.get(idx);
                    Captures saved = caps.copy();
                    caps.start = temp.start;
                    caps.end = temp.end;
                    if (j >= tokens.size()) {
                        if (anchoredEnd) {
                            if (p == input.length()) return true;
                        } else {
                            return true;
                        }
                    } else if (matchesRemaining(input, p, j, caps)) {
                        return true;
                    }
                    caps.start = saved.start;
                    caps.end = saved.end;
                }
                return false;

            } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                Captures takeCaps = caps.copy();
                int next = token.matchOnce(input, i, takeCaps);
                if (next != -1) {
                    if (matchesRemaining(input, next, j + 1, takeCaps)) {
                        caps.start = takeCaps.start;
                        caps.end = takeCaps.end;
                        return true;
                    }
                }
                // skip
                return matchesRemaining(input, i, j + 1, caps);
            }
        }

        return !anchoredEnd || (i == input.length());
    }

    private int matchGroupOnce(String input, int i, Token groupToken, Captures caps) {
        Captures work = caps.copy();
        int res = matchTokens(input, i, groupToken.groupTokens, work);
        if (res == -1) return -1;
        if (groupToken.capturing) {
            work.start = i;
            work.end = res;
        }
        caps.start = work.start;
        caps.end = work.end;
        return res;
    }

    private boolean matchGroup(String input, int i, List<Token> groupTokens, Captures caps) {
        return matchTokens(input, i, groupTokens, caps) != -1;
    }

    private int advanceGroup(String input, int i, Token groupToken, Captures caps) {
        return matchGroupOnce(input, i, groupToken, caps);
    }

    private int matchTokens(String input, int i, List<Token> groupTokens, Captures caps) {
        Captures work = caps.copy();

        int j = 0;
        int pos = i;
        while (j < groupTokens.size()) {
            Token token = groupTokens.get(j);

            if (token.type == Token.TokenType.ALTERNATION) {
                // Remainder of the group after this alternation
                List<Token> remainder = groupTokens.subList(j + 1, groupTokens.size());

                if (token.quantifier == Token.Quantifier.ONE) {
                    for (List<Token> altBranch : token.alternatives) {
                        Captures branchCaps = work.copy();
                        int mid = matchTokens(input, pos, altBranch, branchCaps);
                        if (mid == -1) continue;

                        if (token.capturing) {
                            branchCaps.start = pos;
                            branchCaps.end = mid;
                        }

                        int endPos = matchTokens(input, mid, remainder, branchCaps);
                        if (endPos != -1) {
                            work.start = branchCaps.start;
                            work.end = branchCaps.end;
                            caps.start = work.start;
                            caps.end = work.end;
                            return endPos;
                        }
                    }
                    return -1;
                } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                    // Try taking the alternation once, then the remainder
                    for (List<Token> altBranch : token.alternatives) {
                        Captures branchCaps = work.copy();
                        int mid = matchTokens(input, pos, altBranch, branchCaps);
                        if (mid == -1) continue;

                        if (token.capturing) {
                            branchCaps.start = pos;
                            branchCaps.end = mid;
                        }

                        int endPos = matchTokens(input, mid, remainder, branchCaps);
                        if (endPos != -1) {
                            work.start = branchCaps.start;
                            work.end = branchCaps.end;
                            caps.start = work.start;
                            caps.end = work.end;
                            return endPos;
                        }
                    }
                    // Or skip this alternation and continue with the remainder directly
                    j++;
                    continue;
                } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                    // Consume 1+ repetitions, then try remainder with backoff
                    List<Integer> posHistory = new ArrayList<>();
                    List<Captures> capHistory = new ArrayList<>();
                    int cur = pos;

                    // First repetition must match
                    boolean firstMatched = false;
                    while (true) {
                        int bestNext = -1;
                        Captures bestCaps = null;
                        for (List<Token> altBranch : token.alternatives) {
                            Captures branchCaps = work.copy();
                            int mid = matchTokens(input, cur, altBranch, branchCaps);
                            if (mid != -1 && mid > cur) {
                                if (token.capturing) {
                                    branchCaps.start = cur;
                                    branchCaps.end = mid;
                                }
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
                        work.start = bestCaps.start;
                        work.end = bestCaps.end;
                    }

                    if (!firstMatched) return -1;

                    // Try the remainder after k repetitions, backing off k
                    for (int idx = posHistory.size() - 1; idx >= 0; idx--) {
                        int after = posHistory.get(idx);
                        Captures ccap = capHistory.get(idx);
                        Captures saved = work.copy();
                        work.start = ccap.start;
                        work.end = ccap.end;

                        int endPos = matchTokens(input, after, remainder, work);
                        if (endPos != -1) {
                            caps.start = work.start;
                            caps.end = work.end;
                            return endPos;
                        }
                        work.start = saved.start;
                        work.end = saved.end;
                    }
                    return -1;
                }
            } else if (token.type == Token.TokenType.GROUP) {
                int result = matchGroupOnce(input, pos, token, work);
                if (result == -1) return -1;
                pos = result;
                j++;

            } else if (token.quantifier == Token.Quantifier.ONE) {
                int np = token.matchOnce(input, pos, work);
                if (np == -1) return -1;
                pos = np;
                j++;

            } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                int np = token.matchOnce(input, pos, work);
                if (np != -1) {
                    pos = np;
                }
                j++;

            } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                int count = 0;
                while (true) {
                    int np = token.matchOnce(input, pos, work);
                    if (np == -1 || np == pos) break;
                    pos = np;
                    count++;
                }
                if (count == 0) return -1;
                j++;

            } else {
                return -1;
            }
        }

        caps.start = work.start;
        caps.end = work.end;
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
                // mark the very first group (simple or alternation) as capturing
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
                    i += 2;
                } else if (next == 'w') {
                    token = new Token(Token.TokenType.WORD, "");
                    i += 2;
                } else if (Character.isDigit(next)) {
                    int refNum = Character.getNumericValue(next);
                    if (refNum == 1) {
                        token = new Token(refNum);
                    } else {
                        throw new RuntimeException("Only \\1 backreference supported in this stage");
                    }
                    i += 2;
                } else {
                    token = new Token(Token.TokenType.CHAR, String.valueOf(next));
                    i += 2;
                }

            } else if (c == '[') {
                int end = pattern.indexOf(']', i + 1);
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

            // Unified quantifier handling
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