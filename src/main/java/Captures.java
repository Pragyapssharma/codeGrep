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


    public Captures copy() {
        Captures c = new Captures();
        c.groups.putAll(this.groups);
        c.groupTokenMap.putAll(this.groupTokenMap);
        return c;
    }

    public void set(int idx, int start, int end) {
        groups.put(idx, new Span(start, end));
    }

    public String getGroup(String input, int idx) {
        Span s = groups.get(idx);
        if (s == null) return null;
        if (s.start < 0 || s.end < s.start || s.end > input.length()) return null;
        return input.substring(s.start, s.end);
    }
    
    public void setTokens(int idx, List<Token> tokens) {
        groupTokenMap.put(idx, tokens);
    }

    public List<Token> getGroupTokens(int idx) {
        return groupTokenMap.getOrDefault(idx, List.of());
    }

    public void replaceWith(Captures other) {
        this.groups.clear();
        this.groups.putAll(other.groups);
        this.groupTokenMap.clear();
        this.groupTokenMap.putAll(other.groupTokenMap);
    }
    
    public String resolveGroup(String input, int idx, List<Token> groupTokens) {
        
    	if (groupTokens == null || groupTokens.isEmpty()) {
            return getGroup(input, idx);
        }
    	
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
        if (!reconstructible) return getGroup(input, idx);
        
    	
    	StringBuilder sb = new StringBuilder();
    	
        for (Token t : groupTokens) {
            switch (t.type) {
                case CHAR:
                    sb.append(t.text);
                    break;
                case BACKREF:
                    List<Token> nestedTokens = getGroupTokens(t.backrefIndex);
                    if (!nestedTokens.isEmpty()) {
                        sb.append(resolveGroup(input, t.backrefIndex, nestedTokens));
                    } else {
                        String nested = getGroup(input, t.backrefIndex);
                        if (nested != null) sb.append(nested);
                    }
                    break;
                case GROUP: {
                	if (t.groupIndex >= 0) {
                        List<Token> nestedTokens2 = getGroupTokens(t.groupIndex);
                        if (!nestedTokens2.isEmpty()) {
                            sb.append(resolveGroup(input, t.groupIndex, nestedTokens2));
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
    
    
}