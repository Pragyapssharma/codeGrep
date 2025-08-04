import java.io.IOException;
import java.util.Scanner;

public class Main {
  public static void main(String[] args){
    if (args.length != 2 || !args[0].equals("-E")) {
      System.out.println("Usage: ./your_program.sh -E <pattern>");
      System.exit(1);
    }

    String pattern = args[1];  
    Scanner scanner = new Scanner(System.in);
    String inputLine = scanner.nextLine();

    // Debugging output
    System.err.println("Logs from your program will appear here!");
     
//  Match and exit
     if (matchPattern(inputLine, pattern)) {
         System.exit(0);
     } else {
        System.exit(1);
     }
  }

  public static boolean matchPattern(String inputLine, String pattern) {
    if (pattern.length() == 1) {
      // Single character match
      return inputLine.contains(pattern);
    } else if (pattern.equals("\\d")) {
      // \d matches any digit
      for (char c : inputLine.toCharArray()) {
        if (Character.isDigit(c)) {
          return true;
        }
      }
      return false;
    } else if (pattern.equals("\\w")) {
      // \w matches any alphanumeric character or underscore
      for (char c : inputLine.toCharArray()) {
        if (Character.isLetterOrDigit(c) || c == '_') {
          return true;
        }
      }
	return false;
    } else if (pattern.startsWith("[") && pattern.endsWith("]")) {
      // [abc] matches any character inside the brackets
      String group = pattern.substring(1, pattern.length() - 1);
      for (char c : inputLine.toCharArray()) {
        if (group.indexOf(c) != -1) {
          return true;
        }
      }

      return false;
    } else {
      throw new RuntimeException("Unhandled pattern: " + pattern);
    }
  }
}
