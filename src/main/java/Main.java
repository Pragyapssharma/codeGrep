import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {
    public static void main(String[] args) throws Exception {
        String pattern = null;

        for (int i = 0; i < args.length; i++) {
            if ("-E".equals(args[i]) && i + 1 < args.length) {
                pattern = args[++i];
                break;
            }
        }

        if (pattern == null) {
            System.err.println("Usage: java Main -E \"<pattern>\"");
            System.exit(2);
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = br.read()) != -1) sb.append((char) ch);
        String input = sb.toString();

        RegexMatcher matcher = new RegexMatcher(pattern);
        boolean ok = matcher.matches(input);

        System.exit(ok ? 0 : 1);
    }
}