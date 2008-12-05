package davmail.exchange;

import davmail.Settings;
import org.apache.commons.httpclient.util.URIUtil;

/**
 *
 */
public class TestExchangeSession {

    protected TestExchangeSession() {
    }

    public static void main(String[] argv) {
        int currentArg = 0;
        Settings.setConfigFilePath(argv[currentArg++]);
        Settings.load();

        ExchangeSession session;
        // test auth
        try {
            ExchangeSessionFactory.checkConfig();
            session = ExchangeSessionFactory.getInstance(argv[currentArg++], argv[currentArg++]);

            ExchangeSession.Folder folder = session.selectFolder(argv[currentArg++]);
            String messageName;
            messageName = URIUtil.decode(argv[currentArg]);

            long startTime = System.currentTimeMillis();
            ExchangeSession.Message messageTest = session.getMessage(folder.folderUrl + "/" + messageName);
            System.out.println("******");
            messageTest.write(System.out);
            System.out.println("Elapsed time " + (System.currentTimeMillis() - startTime) + " ms");

            session.purgeOldestTrashAndSentMessages();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
