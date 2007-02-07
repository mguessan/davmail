package davmail.exchange;

import davmail.Settings;
import org.apache.commons.httpclient.util.URIUtil;

/**
 *
 */
public class TestExchangeSession {
    public static void main(String[] argv) {
        Settings.setConfigFilePath(argv[0]);
        Settings.load();

        ExchangeSession session = new ExchangeSession();
        // test auth
        try {
            session.login(argv[1], argv[2]);

            ExchangeSession.Folder folder = session.selectFolder(argv[3]);
            String messageName;
            messageName = URIUtil.decode(argv[4]);

            long startTime = System.currentTimeMillis();
            ExchangeSession.Message messageTest = session.getMessage(folder.folderUrl+"/"+messageName);
            System.out.println("******");
            messageTest.write(System.out);
            System.out.println("Elapsed time " + (System.currentTimeMillis()-startTime) + " ms");

            session.purgeOldestTrashMessages();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
