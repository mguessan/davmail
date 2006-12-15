package davmail.exchange;

import davmail.Settings;
import org.apache.commons.httpclient.util.URIUtil;

/**
 *
 */
public class TestExchangeSession {
    public static void main(String[] argv) {
        Settings.load();

        ExchangeSession session = new ExchangeSession();
        // test auth
        try {
            session.login(argv[0], argv[1]);

            ExchangeSession.Folder folder = session.selectFolder("tests");
            session.selectFolder("tests");
            String messageName;
            messageName = URIUtil.decode(argv[2]);

            long startTime = System.currentTimeMillis();
            ExchangeSession.Message messageTest = session.getMessage(folder.folderUrl + "/"+messageName);
            System.out.println("******");
            messageTest.write(System.out);
            System.out.println("Elapsed time " + (System.currentTimeMillis()-startTime) + " ms");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
