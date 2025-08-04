import java.util.*;

public class RegexMatcher {
  private final List<Token> tokens;
private final boolean anchored;

  public RegexMatcher(String pattern) {
    if (pattern.startsWith("^")) {
    this.anchored = true;
    pattern = pattern.substring(1); // remove the anchor for tokenization
  } else {
    this.anchored = false;
  }
  this.tokens = tokenize(pattern);
  }

  public boolean matches(String input) {
    if (anchored) {
    return matchesAt(input, 0); // only match at start
  } else {
    for (int i = 0; i <= input.length() - tokens.size(); i++) {
      if (matchesAt(input, i)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesAt(String input, int start) {
    for (int i = 0; i < tokens.size(); i++) {
      if (start + i >= input.length()) return false;
      if (!tokens.get(i).matches(input.charAt(start + i))) return false;
    }
    return true;
  }

  private List<Token> tokenize(String pattern) {
    List<Token> tokens = new ArrayList<>();
    for (int i = 0; i < pattern.length(); ) {
      char c = pattern.charAt(i);
      if (c == '\\' && i + 1 < pattern.length()) {
        char next = pattern.charAt(i + 1);
        if (next == 'd') {
          tokens.add(new Token(TokenType.DIGIT, ""));
        } else if (next == 'w') {
          tokens.add(new Token(TokenType.WORD, ""));
        } else {
          tokens.add(new Token(TokenType.CHAR, String.valueOf(next)));
        }
        i += 2;
      } else if (c == '[') {
        int end = pattern.indexOf(']', i);
        if (end == -1) throw new RuntimeException("Unclosed [");
        String group = pattern.substring(i + 1, end);
        if (group.startsWith("^")) {
          tokens.add(new Token(TokenType.NEGATIVE_GROUP, group.substring(1)));
        } else {
          tokens.add(new Token(TokenType.POSITIVE_GROUP, group));
        }
        i = end + 1;
      } else {
        tokens.add(new Token(TokenType.CHAR, String.valueOf(c)));
        i++;
      }
    }
    return tokens;
  }

  enum TokenType {
    DIGIT, WORD, CHAR, POSITIVE_GROUP, NEGATIVE_GROUP
  }

  static class Token {
    TokenType type;
    String value;

    Token(TokenType type, String value) {
      this.type = type;
      this.value = value;
    }

    boolean matches(char c) {
      switch (type) {
        case DIGIT: return Character.isDigit(c);
        case WORD: return Character.isLetterOrDigit(c) || c == '_';
        case CHAR: return c == value.charAt(0);
        case POSITIVE_GROUP: return value.indexOf(c) != -1;
        case NEGATIVE_GROUP: return value.indexOf(c) == -1;
        default: return false;
      }
    }
  }
}