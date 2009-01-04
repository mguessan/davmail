package davmail.exchange;

/**
 * ICS String writer.
 * split lines longer than 75 characters
 */
public class ICSBufferedWriter {
    StringBuilder buffer = new StringBuilder();

    public void writeLine(String line) {
        if (line.length() > 75) {
            buffer.append(line.substring(0, 75));
            newLine();
            buffer.append(' ');
            writeLine(line.substring(75));
        } else {
            buffer.append(line);
            newLine();
        }
    }

    public void newLine() {
        buffer.append((char) 13).append((char) 10);
    }

    @Override
    public String toString() {
      return buffer.toString();  
    }

}
