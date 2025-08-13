import java.util.*;

public class RegexMatcher {
    private final List<Token> tokens;
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

//    public boolean matches(String input) {
//        if (anchored) {
//            return matchesAt(input, 0);
//        } else {
//            for (int i = 0; i <= input.length(); i++) {
//                if (matchesAt(input, i)) {
//                    return true;
//                }
//            }
//            return false;
//        }
//    }
    
    private boolean matchesRemaining(String input, int i, int j, List<Token> tokenList) {
        while (j < tokenList.size()) {
            Token token = tokenList.get(j);

            System.out.println("Matching token: " + token.type + " at input pos: " + i);
            System.out.println("Current input: " + (i < input.length() ? input.substring(i) : "EOF"));

            if (token.type == Token.TokenType.ALTERNATION) {
                for (List<Token> alt : token.alternatives) {
                    List<Token> combined = new ArrayList<>(alt);
                    combined.addAll(tokenList.subList(j + 1, tokenList.size()));
                    if (matchesAlternative(input, i, combined)) {
                        return true;
                    }
                }
                return false;
            }

            if (i >= input.length() || !token.matches(input.charAt(i))) {
                return false;
            }
            i++;
            j++;
        }
        return i == input.length();
    }

    

    
    public boolean matches(String input) {
        return matchesRemaining(input, 0, 0, this.tokens);
    }

    private boolean matchesAt(String input, int start) {
        return matchesRemaining(input, start, 0);
    }

    private boolean matchesRemaining(String input, int i, int j) {
        while (j < tokens.size()) {
            Token token = tokens.get(j);

            System.out.println("Matching token: " + token.type + " at input pos: " + i);
            System.out.println("Current input: " + (i < input.length() ? input.substring(i) : "EOF"));

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

            if (token.type == Token.TokenType.GROUP) {
                if (token.quantifier == Token.Quantifier.ONE) {
                    if (!matchGroup(input, i, token.groupTokens)) return false;
                    i = advanceGroup(input, i, token.groupTokens);
                    j++;
                } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                    int pos = i;
                    int count = 0;
                    System.out.println("Trying ONE_OR_MORE for group at input pos: " + i);
                    while (true) {
                        int next = advanceGroup(input, pos, token.groupTokens);
                        System.out.println("advanceGroup returned: " + next);
                        if (next == -1 || next == pos) break;
                        count++;
                        pos = next;
                        if (anchoredEnd && j + 1 == tokens.size() && pos == input.length()) {
                            return true;
                        }
                    }
                    if (count == 0) return false;
                    j++;
                    System.out.println("Final input pos: " + pos + ", anchoredEnd: " + anchoredEnd);

                    if (anchoredEnd && j >= tokens.size()) {
                        return pos == input.length();
                    }

                    return matchesRemaining(input, pos, j);
                } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                    if (matchGroup(input, i, token.groupTokens)) {
                        int next = advanceGroup(input, i, token.groupTokens);
                        if (matchesRemaining(input, next, j + 1)) return true;
                    }
                    return matchesRemaining(input, i, j + 1);
                }
                continue;
            }

            if (token.quantifier == Token.Quantifier.ONE) {
                if (i >= input.length() || !token.matches(input.charAt(i))) return false;
                i++;
                j++;
            } else if (token.quantifier == Token.Quantifier.ONE_OR_MORE) {
                int count = 0;
                int newPos = i;
                while (true) {
                    int nextPos = matchTokens(input, newPos, token.groupTokens);
                    if (nextPos == -1) break;
                    newPos = nextPos;
                    count++;
                }
                if (count == 0) return false;
                i = newPos;
                j++;
                return true;
                
            } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                if (i < input.length() && token.matches(input.charAt(i))) {
                    if (matchesRemaining(input, i + 1, j + 1)) return true;
                }
                return matchesRemaining(input, i, j + 1);
            }
        }

        return !anchoredEnd || (i == input.length());
    }

//    private boolean matchesAlternative(String input, int i, List<Token> altTokens) {
//        List<Token> savedTokens = this.tokens;
//        this.tokens = altTokens;
//        boolean result = matchesRemaining(input, i, 0);
//        this.tokens = savedTokens;
//        return result;
//    }
    
    private boolean matchesAlternative(String input, int i, List<Token> altTokens) {
        return matchesRemaining(input, i, 0, altTokens);
    }


    private boolean matchGroup(String input, int i, List<Token> groupTokens) {
        return matchTokens(input, i, groupTokens) != -1;
    }

    private int advanceGroup(String input, int i, List<Token> groupTokens) {
    	System.out.println("in advanceGroup: " + input + " at input i: " + i + " tokengroup: "+groupTokens);
        return matchTokens(input, i, groupTokens);
    }

    private int matchTokens(String input, int i, List<Token> groupTokens) {
        int j = 0;
        int pos = i;
        while (j < groupTokens.size()) {
            Token token = groupTokens.get(j);

            System.out.println("Matching token in group: " + token.type + " at input pos: " + pos);
            System.out.println("Current input: " + (pos < input.length() ? input.substring(pos) : "EOF"));

            if (token.type == Token.TokenType.ALTERNATION) {
                int bestMatchPos = -1;
                for (List<Token> altBranch : token.alternatives) {
                    List<Token> trialChain = new ArrayList<>(altBranch);
                    trialChain.addAll(groupTokens.subList(j + 1, groupTokens.size()));

                    int matchPos = matchTokens(input, pos, trialChain);
                    if (matchPos > bestMatchPos) {
                        bestMatchPos = matchPos;
                    }
                }
                return bestMatchPos != -1 ? bestMatchPos : -1;


            } else if (token.type == Token.TokenType.GROUP) {
                int result = matchTokens(input, pos, token.groupTokens);
                if (result == -1) return -1;
                pos = result;
                j++;
            } else if (token.quantifier == Token.Quantifier.ONE) {
                if (pos >= input.length() || !token.matches(input.charAt(pos))) return -1;
                pos++;
                j++;
            } else if (token.quantifier == Token.Quantifier.ZERO_OR_ONE) {
                if (token.type == Token.TokenType.GROUP) {
                    int result = matchTokens(input, pos, token.groupTokens);
                    if (result != -1) {
                        pos = result;
                    }
                } else if (token.type == Token.TokenType.ALTERNATION) {
                    boolean matched = false;
                    for (List<Token> altBranch : token.alternatives) {
                        int result = matchTokens(input, pos, altBranch);
                        if (result != -1) {
                            pos = result;
                            matched = true;
                            break;
                        }
                    }
                  
                } else {
                    if (pos < input.length() && token.matches(input.charAt(pos))) {
                        pos++;
                    }
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

//    private List<Token> tokenize(String pattern) {
//        List<Token> tokens = new ArrayList<>();
//        for (int i = 0; i < pattern.length();) {
//            char c = pattern.charAt(i);
//            Token token;
//
//            if (c == '(') {
//                int end = findClosingParen(pattern, i);
//                String group = pattern.substring(i + 1, end);
//                List<List<Token>> alternatives = new ArrayList<>();
//                int lastSplit = 0;
//                int depth = 0;
//
//                for (int j = 0; j <= group.length(); j++) {
//                    if (j == group.length() || (group.charAt(j) == '|' && depth == 0)) {
//                        String part = group.substring(lastSplit, j);
//                        alternatives.add(tokenize(part));
//                        lastSplit = j + 1;
//                    } else if (group.charAt(j) == '(') {
//                        depth++;
//                    } else if (group.charAt(j) == ')') {
//                        depth--;
//                    }
//                }
//
//                if (alternatives.size() == 1) {
//                    token = new Token(alternatives.get(0), Token.TokenType.GROUP);
//                } else {
//                    token = new Token(alternatives);
//                }
//                i = end + 1;
//                
//             // Check for quantifier after group
//                if (i < pattern.length()) {
//                    char next = pattern.charAt(i);
//                    if (next == '+') {
//                        token.quantifier = Token.Quantifier.ONE_OR_MORE;
//                        i++;
//                    } else if (next == '?') {
//                        token.quantifier = Token.Quantifier.ZERO_OR_ONE;
//                        i++;
//                    }
//                }
//                
//            } else if (c == '\\' && i + 1 < pattern.length()) {
//                char next = pattern.charAt(i + 1);
//                if (next == 'd') {
//                    token = new Token(Token.TokenType.DIGIT, "");
//                } else if (next == 'w') {
//                    token = new Token(Token.TokenType.WORD, "");
//                } else {
//                    token = new Token(Token.TokenType.CHAR, String.valueOf(next));
//                }
//                i += 2;
//            } else if (c == '[') {
//                int end = pattern.indexOf(']', i);
//                if (end == -1) throw new RuntimeException("Unclosed [");
//                String group = pattern.substring(i + 1, end);
//                if (group.startsWith("^")) {
//                    token = new Token(Token.TokenType.NEGATIVE_GROUP, group.substring(1));
//                } else {
//                    token = new Token(Token.TokenType.POSITIVE_GROUP, group);
//                }
//                i = end + 1;
//            } else if (c == '.') {
//                token = new Token(Token.TokenType.DOT, "");
//                i++;
//            } else {
//                token = new Token(Token.TokenType.CHAR, String.valueOf(c));
//                i++;
//            }
//
//            if (i < pattern.length()) {
//                char next = pattern.charAt(i);
//                if (next == '+') {
//                    token.quantifier = Token.Quantifier.ONE_OR_MORE;
//                    i++;
//                } else if (next == '?') {
//                    token.quantifier = Token.Quantifier.ZERO_OR_ONE;
//                    i++;
//                }
//            }
//
//            tokens.add(token);
//        }
//
//        return tokens;
//    }

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
    
    private List<Token> tokenize(String pattern) {
        // Step 1: Check for top-level alternation
        int depth = 0;
        List<List<Token>> alternatives = new ArrayList<>();
        int lastSplit = 0;

        for (int i = 0; i <= pattern.length(); i++) {
            if (i == pattern.length() || (pattern.charAt(i) == '|' && depth == 0)) {
                String part = pattern.substring(lastSplit, i);
                alternatives.add(tokenizePart(part)); // tokenizePart handles normal tokens
                lastSplit = i + 1;
            } else if (pattern.charAt(i) == '(') {
                depth++;
            } else if (pattern.charAt(i) == ')') {
                depth--;
            }
        }

        if (alternatives.size() > 1) {
            // We found a top-level alternation
            return Collections.singletonList(new Token(alternatives));
        }

        // Step 2: No top-level alternation, just tokenize normally
        return tokenizePart(pattern);
    }

    private List<Token> tokenizePart(String pattern) {
        List<Token> tokens = new ArrayList<>();
        for (int i = 0; i < pattern.length();) {
            char c = pattern.charAt(i);
            Token token;

            if (c == '(') {
                int end = findClosingParen(pattern, i);
                String group = pattern.substring(i + 1, end);

                // Check for alternation inside group
                List<List<Token>> alternatives = new ArrayList<>();
                int lastSplit = 0;
                int depth = 0;
                for (int j = 0; j <= group.length(); j++) {
                    if (j == group.length() || (group.charAt(j) == '|' && depth == 0)) {
                        alternatives.add(tokenizePart(group.substring(lastSplit, j)));
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

                // Quantifier after group
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
                String groupStr = pattern.substring(i + 1, end);
                if (groupStr.startsWith("^")) {
                    token = new Token(Token.TokenType.NEGATIVE_GROUP, groupStr.substring(1));
                } else {
                    token = new Token(Token.TokenType.POSITIVE_GROUP, groupStr);
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

    
}