import java.util.HashMap;
import java.util.Map;

public class Captures {
    public static final class Span {
        public final int start;
        public final int end;
        public Span(int s, int e) { this.start = s; this.end = e; }
    }

    private final Map<Integer, Span> groups = new HashMap<>();

    public Captures copy() {
        Captures c = new Captures();
        c.groups.putAll(this.groups);
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

    public void replaceWith(Captures other) {
        this.groups.clear();
        this.groups.putAll(other.groups);
    }
}