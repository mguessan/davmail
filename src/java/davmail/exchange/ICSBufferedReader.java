package davmail.exchange;

import java.io.BufferedReader;
import java.io.Reader;
import java.io.IOException;

/**
 * ICS Buffered Reader.
 * Read events by line, handle multiline elements
 */
public class ICSBufferedReader extends BufferedReader {
    protected String nextLine;
    protected final StringBuilder currentLine = new StringBuilder(75);

    public ICSBufferedReader(Reader in) throws IOException {
        super(in);
        nextLine = super.readLine();
    }

    @Override
    public String readLine() throws IOException {
        if (nextLine == null) {
            return null;
        } else {
            currentLine.setLength(0);
            currentLine.append(nextLine);
            nextLine = super.readLine();
            while (nextLine != null && !(nextLine.length() == 0) && nextLine.charAt(0) == ' ') {
                currentLine.append(nextLine.substring(1));
                nextLine = super.readLine();
            }
            return currentLine.toString();
        }
    }
}
