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

    public ICSBufferedReader(Reader in, int sz) throws IOException {
        super(in, sz);
        nextLine = super.readLine();
    }

    public ICSBufferedReader(Reader in) throws IOException {
        super(in);
        nextLine = super.readLine();
    }

    @Override
    public String readLine() throws IOException {
        String currentLine = nextLine;
        nextLine = super.readLine();
        while (nextLine != null && nextLine.charAt(0) == ' ') {
            currentLine += nextLine.substring(1);
            nextLine = super.readLine();
        }
        return currentLine;
    }
}
