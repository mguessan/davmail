package davmail.exchange;

import davmail.Settings;

/**
 *  Test Exchange session
 */
public class TestExchangeSession {

    private TestExchangeSession() {
    }

    /**
     * main method
     * @param argv command line arg
     */
    public static void main(String[] argv) {
        // register custom SSL Socket factory
        int currentArg = 0;
        Settings.setConfigFilePath(argv[currentArg++]);
        Settings.load();

        ExchangeSession session;
        // test auth
        try {
            ExchangeSessionFactory.checkConfig();
            session = ExchangeSessionFactory.getInstance(argv[currentArg++], argv[currentArg]);

            ExchangeSession.Folder folder = session.getFolder("INBOX");
            folder.loadMessages();

            //session.purgeOldestTrashAndSentMessages();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
