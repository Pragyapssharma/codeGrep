import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Captures {
    public static final class Span {
        public final int start;
        public final int end;
        public Span(int s, int e) { this.start = s; this.end = e; }
    }

    private final Map<Integer, Span> groups = new HashMap<>();
    private final Map<Integer, List<Token>> groupTokenMap = new HashMap<>();
    private final java.util.Set<Integer> lockedGroups = new java.util.HashSet<>();
    private boolean allowLocking = true;

    public void disableLocking() { this.allowLocking = false; }
    public void enableLocking() { this.allowLocking = true; }


    public Captures copy() {
        Captures c = new Captures();
        c.groups.putAll(this.groups);
        c.groupTokenMap.putAll(this.groupTokenMap);
        c.lockedGroups.addAll(this.lockedGroups);
        c.allowLocking = this.allowLocking;
        return c;
    }

    public void set(int idx, int start, int end) {
        if (!lockedGroups.contains(idx)) {
            groups.put(idx, new Span(start, end));
            if (allowLocking) lockedGroups.add(idx);
            System.out.println("in Captures.set idx :" + idx + " start : " + start + " end : " + end);
        }
    }

    public String getGroup(String input, int idx) {
        Span s = groups.get(idx);
        if (s == null) return null;
        if (s.start < 0 || s.end < s.start || s.end > input.length()) return null;
        return input.substring(s.start, s.end);
    }
    
    public void setTokens(int idx, List<Token> tokens) {
        if (!lockedGroups.contains(idx)) {
            groupTokenMap.put(idx, tokens);
            if (allowLocking) lockedGroups.add(idx);
            System.err.printf("[DEBUG] Group %d tokens: %s%n", idx, tokensToString(tokens));
        }
    }

    public List<Token> getGroupTokens(int idx) {
        return groupTokenMap.getOrDefault(idx, List.of());
    }

//    public void replaceWith(Captures other) {
//        this.groups.clear();
//        this.groups.putAll(other.groups);
//        this.groupTokenMap.clear();
//        this.groupTokenMap.putAll(other.groupTokenMap);
//    }
    
    public void replaceWith(Captures other) {
        for (Map.Entry<Integer, Span> entry : other.groups.entrySet()) {
            int idx = entry.getKey();
            if (!lockedGroups.contains(idx)) {
                groups.put(idx, entry.getValue());
            }
        }
        for (Map.Entry<Integer, List<Token>> entry : other.groupTokenMap.entrySet()) {
            int idx = entry.getKey();
            if (!lockedGroups.contains(idx)) {
                groupTokenMap.put(idx, entry.getValue());
            }
        }
    }
    
    public String resolveGroup(String input, int idx, List<Token> groupTokens) {
        return resolveGroup(input, idx, groupTokens, new java.util.HashSet<>());
    }

    private String resolveGroup(String input, int idx, List<Token> groupTokens, java.util.Set<Integer> visited) {
        if (groupTokens == null || groupTokens.isEmpty()) {
            String raw = getGroup(input, idx);
            return raw != null ? raw : "";
        }
        if (visited.contains(idx)) {
            String raw = getGroup(input, idx);
            return raw != null ? raw : "";
        }
        visited.add(idx);

        boolean reconstructible = true;
        for (Token t : groupTokens) {
            if (t.quantifier != Token.Quantifier.ONE) { reconstructible = false; break; }
            switch (t.type) {
                case CHAR:
                case BACKREF:
                case GROUP:
                    break;
                default:
                    reconstructible = false; break;
            }
            if (!reconstructible) break;
        }
        if (!reconstructible) {
            String raw = getGroup(input, idx);
            return raw != null ? raw : "";
        }

        StringBuilder sb = new StringBuilder();
        for (Token t : groupTokens) {
            switch (t.type) {
                case CHAR:
                    sb.append(t.text);
                    break;
                case BACKREF: {
                    List<Token> nestedTokens = getGroupTokens(t.backrefIndex);
                    if (!nestedTokens.isEmpty()) {
                        sb.append(resolveGroup(input, t.backrefIndex, nestedTokens, visited));
                    } else {
                        String nested = getGroup(input, t.backrefIndex);
                        if (nested != null) sb.append(nested);
                    }
                    break;
                }
                case GROUP: {
                    if (t.groupIndex >= 0) {
                        List<Token> nestedTokens2 = getGroupTokens(t.groupIndex);
                        if (!nestedTokens2.isEmpty()) {
                            sb.append(resolveGroup(input, t.groupIndex, nestedTokens2, visited));
                        } else {
                            String raw = getGroup(input, t.groupIndex);
                            if (raw != null) sb.append(raw);
                        }
                    }
                    break;
                }
                default: {
                    if (t.groupIndex >= 0) {
                        String raw = getGroup(input, t.groupIndex);
                        if (raw != null) sb.append(raw);
                    }
                    break;
                }
            }
        }
        return sb.toString();
    }
    
    private String tokensToString(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            sb.append(t.type);
            if (t.type == Token.TokenType.CHAR) sb.append("('").append(t.text).append("')");
            if (t.type == Token.TokenType.BACKREF) sb.append("(\\").append(t.backrefIndex).append(")");
            sb.append(" ");
        }
        return sb.toString().trim();
    }


    
//    public String resolveGroup(String input, int idx, List<Token> groupTokens) {
//        
//    	if (groupTokens == null || groupTokens.isEmpty()) {
//            return getGroup(input, idx);
//        }
//    	
//    	boolean reconstructible = true;
//        for (Token t : groupTokens) {
//            if (t.quantifier != Token.Quantifier.ONE) { reconstructible = false; break; }
//            switch (t.type) {
//                case CHAR:
//                case BACKREF:
//                case GROUP:
//                    break;
//                default:
//                    reconstructible = false; break;
//            }
//            if (!reconstructible) break;
//        }
//        if (!reconstructible) return getGroup(input, idx);
//        
//    	
//    	StringBuilder sb = new StringBuilder();
//    	
//        for (Token t : groupTokens) {
//            switch (t.type) {
//                case CHAR:
//                    sb.append(t.text);
//                    break;
//                case BACKREF:
//                    List<Token> nestedTokens = getGroupTokens(t.backrefIndex);
//                    if (!nestedTokens.isEmpty()) {
//                        sb.append(resolveGroup(input, t.backrefIndex, nestedTokens));
//                    } else {
//                        String nested = getGroup(input, t.backrefIndex);
//                        if (nested != null) sb.append(nested);
//                    }
//                    break;
//                case GROUP: {
//                	if (t.groupIndex >= 0) {
//                        List<Token> nestedTokens2 = getGroupTokens(t.groupIndex);
//                        if (!nestedTokens2.isEmpty()) {
//                            sb.append(resolveGroup(input, t.groupIndex, nestedTokens2));
//                        } else {
//                            String raw = getGroup(input, t.groupIndex);
//                            if (raw != null) sb.append(raw);
//                        }
//                    }
//                    break;
//                }
//                default: {
//                    if (t.groupIndex >= 0) {
//                        String raw = getGroup(input, t.groupIndex);
//                        if (raw != null) sb.append(raw);
//                    }
//                    break;
//                }
//            }
//        }
//        return sb.toString();
//    }
    
    
}