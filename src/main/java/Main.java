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
     
 RegexMatcher matcher = new RegexMatcher(pattern);
    if (matcher.matches(inputLine)) {
      System.exit(0);
    } else {
      System.exit(1);
    }
  }

}

