package davmail.ldap;

import com.sun.jndi.ldap.Ber;
import com.sun.jndi.ldap.BerDecoder;
import com.sun.jndi.ldap.BerEncoder;
import davmail.AbstractConnection;
import davmail.Settings;
import davmail.exchange.ExchangeSessionFactory;
import davmail.tray.DavGatewayTray;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;

/**
 * Handle a caldav connection.
 */
public class LdapConnection extends AbstractConnection {
    /**
     * Davmail base context
     */
    static final String BASE_CONTEXT = "ou=people";

    static final List<String> PERSON_OBJECT_CLASSES = new ArrayList<String>();

    static {
        PERSON_OBJECT_CLASSES.add("top");
        PERSON_OBJECT_CLASSES.add("person");
        PERSON_OBJECT_CLASSES.add("organizationalPerson");
        PERSON_OBJECT_CLASSES.add("inetOrgPerson");
    }

    /**
     * Exchange to LDAP attribute map
     */
    static final HashMap<String, String> ATTRIBUTE_MAP = new HashMap<String, String>();

    static {
        ATTRIBUTE_MAP.put("uid", "AN");
        ATTRIBUTE_MAP.put("mail", "EM");
        ATTRIBUTE_MAP.put("cn", "DN");
        ATTRIBUTE_MAP.put("displayName", "DN");
        ATTRIBUTE_MAP.put("telephoneNumber", "PH");
        ATTRIBUTE_MAP.put("l", "OFFICE");
        ATTRIBUTE_MAP.put("company", "CP");
        ATTRIBUTE_MAP.put("title", "TL");

        ATTRIBUTE_MAP.put("givenName", "first");
        ATTRIBUTE_MAP.put("initials", "initials");
        ATTRIBUTE_MAP.put("sn", "last");
        ATTRIBUTE_MAP.put("street", "street");
        ATTRIBUTE_MAP.put("st", "state");
        ATTRIBUTE_MAP.put("postalCode", "zip");
        ATTRIBUTE_MAP.put("c", "country");
        ATTRIBUTE_MAP.put("departement", "department");
        ATTRIBUTE_MAP.put("mobile", "mobile");
    }

    static final HashSet<String> EXTENDED_ATTRIBUTES = new HashSet<String>();

    static {
        EXTENDED_ATTRIBUTES.add("givenname");
        EXTENDED_ATTRIBUTES.add("initials");
        EXTENDED_ATTRIBUTES.add("sn");
        EXTENDED_ATTRIBUTES.add("street");
        EXTENDED_ATTRIBUTES.add("st");
        EXTENDED_ATTRIBUTES.add("postalcode");
        EXTENDED_ATTRIBUTES.add("c");
        EXTENDED_ATTRIBUTES.add("departement");
        EXTENDED_ATTRIBUTES.add("mobile");
    }

    /**
     * LDAP to Exchange Criteria Map
     */
    static final HashMap<String, String> CRITERIA_MAP = new HashMap<String, String>();

    static {
        // assume mail starts with firstname
        CRITERIA_MAP.put("uid", "AN");
        CRITERIA_MAP.put("mail", "FN");
        CRITERIA_MAP.put("displayname", "DN");
        CRITERIA_MAP.put("cn", "DN");
        CRITERIA_MAP.put("givenname", "FN");
        CRITERIA_MAP.put("sn", "LN");
        CRITERIA_MAP.put("title", "TL");
        CRITERIA_MAP.put("company", "CP");
        CRITERIA_MAP.put("o", "CP");
        CRITERIA_MAP.put("l", "OF");
        CRITERIA_MAP.put("department", "DP");
    }

    // LDAP version
    static final int LDAP_VERSION2 = 0x02;
    static final int LDAP_VERSION3 = 0x03;

    // LDAP request operations
    static final int LDAP_REQ_BIND = 0x60;
    static final int LDAP_REQ_SEARCH = 0x63;
    static final int LDAP_REQ_UNBIND = 0x42;
    static final int LDAP_REQ_ABANDON = 0x50;

    // LDAP response operations
    static final int LDAP_REP_BIND = 0x61;
    static final int LDAP_REP_SEARCH = 0x64;
    static final int LDAP_REP_RESULT = 0x65;

    // LDAP return codes
    static final int LDAP_OTHER = 80;
    static final int LDAP_SUCCESS = 0;
    static final int LDAP_SIZE_LIMIT_EXCEEDED = 4;
    static final int LDAP_INVALID_CREDENTIALS = 49;

    static final int LDAP_FILTER_AND = 0xa0;
    static final int LDAP_FILTER_OR = 0xa1;

    // LDAP filter operators (only LDAP_FILTER_SUBSTRINGS is supported)
    static final int LDAP_FILTER_SUBSTRINGS = 0xa4;
    static final int LDAP_FILTER_GE = 0xa5;
    static final int LDAP_FILTER_LE = 0xa6;
    static final int LDAP_FILTER_PRESENT = 0x87;
    static final int LDAP_FILTER_APPROX = 0xa8;
    static final int LDAP_FILTER_EQUALITY = 0xa3;

    // LDAP filter mode (only startsWith supported by galfind)
    static final int LDAP_SUBSTRING_INITIAL = 0x80;
    static final int LDAP_SUBSTRING_ANY = 0x81;
    static final int LDAP_SUBSTRING_FINAL = 0x82;

    // BER data types
    static final int LBER_ENUMERATED = 0x0a;
    static final int LBER_SET = 0x31;
    static final int LBER_SEQUENCE = 0x30;

    // LDAP search scope
    static final int SCOPE_BASE_OBJECT = 0;
    static final int SCOPE_ONE_LEVEL = 1;
    static final int SCOPE_SUBTREE = 2;

    /**
     * For some unknow reaseon parseIntWithTag is private !
     */
    static final Method parseIntWithTag;

    static {
        try {
            parseIntWithTag = BerDecoder.class.getDeclaredMethod("parseIntWithTag", int.class);
            parseIntWithTag.setAccessible(true);
        } catch (NoSuchMethodException e) {
            DavGatewayTray.error("Unable to get BerDecoder.parseIntWithTag method");
            throw new RuntimeException(e);
        }
    }

    /**
     * raw connection inputStream
     */
    protected BufferedInputStream is;

    /**
     * reusable BER encoder
     */
    protected final BerEncoder responseBer = new BerEncoder();

    /**
     * Current LDAP version (used for String encoding)
     */
    int ldapVersion = LDAP_VERSION3;

    // Initialize the streams and start the thread
    public LdapConnection(Socket clientSocket) {
        super("LdapConnection-" + clientSocket.getPort(), clientSocket);
        try {
            is = new BufferedInputStream(client.getInputStream());
            os = new BufferedOutputStream(client.getOutputStream());
        } catch (IOException e) {
            close();
            DavGatewayTray.error("Exception while getting socket streams", e);
        }
    }

    protected boolean isLdapV3() {
        return ldapVersion == LDAP_VERSION3;
    }

    public void run() {
        byte[] inbuf = new byte[2048];   // Buffer for reading incoming bytes
        int bytesread;  // Number of bytes in inbuf
        int bytesleft;  // Number of bytes that need to read for completing resp
        int br;         // Temp; number of bytes read from stream
        int offset;     // Offset of where to store bytes in inbuf
        boolean eos;    // End of stream

        try {
            ExchangeSessionFactory.checkConfig();
            while (true) {
                offset = 0;

                // check that it is the beginning of a sequence
                bytesread = is.read(inbuf, offset, 1);
                if (bytesread < 0) {
                    break; // EOF
                }

                if (inbuf[offset++] != (Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR)) {
                    continue;
                }

                // get length of sequence
                bytesread = is.read(inbuf, offset, 1);
                if (bytesread < 0) {
                    break; // EOF
                }
                int seqlen = inbuf[offset++]; // Length of ASN sequence

                // if high bit is on, length is encoded in the
                // subsequent length bytes and the number of length bytes
                // is equal to & 0x80 (i.e. length byte with high bit off).
                if ((seqlen & 0x80) == 0x80) {
                    int seqlenlen = seqlen & 0x7f;  // number of length bytes

                    bytesread = 0;
                    eos = false;

                    // Read all length bytes
                    while (bytesread < seqlenlen) {
                        br = is.read(inbuf, offset + bytesread,
                                seqlenlen - bytesread);
                        if (br < 0) {
                            eos = true;
                            break; // EOF
                        }
                        bytesread += br;
                    }

                    // end-of-stream reached before length bytes are read
                    if (eos)
                        break;  // EOF

                    // Add contents of length bytes to determine length
                    seqlen = 0;
                    for (int i = 0; i < seqlenlen; i++) {
                        seqlen = (seqlen << 8) + (inbuf[offset + i] & 0xff);
                    }
                    offset += bytesread;
                }

                // read in seqlen bytes
                bytesleft = seqlen;
                if ((offset + bytesleft) > inbuf.length) {
                    byte[] nbuf = new byte[offset + bytesleft];
                    System.arraycopy(inbuf, 0, nbuf, 0, offset);
                    inbuf = nbuf;
                }
                while (bytesleft > 0) {
                    bytesread = is.read(inbuf, offset, bytesleft);
                    if (bytesread < 0) {
                        break; // EOF
                    }
                    offset += bytesread;
                    bytesleft -= bytesread;
                }

                //Ber.dumpBER(System.out, "request\n", inbuf, 0, offset);
                handleRequest(new BerDecoder(inbuf, 0, offset));
            }

        } catch (SocketException e) {
            DavGatewayTray.debug("Connection closed");
        } catch (SocketTimeoutException e) {
            DavGatewayTray.debug("Closing connection on timeout");
        } catch (IOException e) {
            DavGatewayTray.error(e);
            try {
                sendErr(0, LDAP_REP_BIND, e);
            } catch (IOException e2) {
                DavGatewayTray.warn("Exception sending error to client", e2);
            }
        } finally {
            close();
        }
        DavGatewayTray.resetIcon();
    }

    protected void handleRequest(BerDecoder reqBer) throws IOException {
        int currentMessageId = 0;
        try {
            reqBer.parseSeq(null);
            currentMessageId = reqBer.parseInt();
            int requestOperation = reqBer.peekByte();

            if (requestOperation == LDAP_REQ_BIND) {
                reqBer.parseSeq(null);
                ldapVersion = reqBer.parseInt();
                String userName = reqBer.parseString(isLdapV3());
                String password = reqBer.parseStringWithTag(Ber.ASN_CONTEXT, isLdapV3(), null);

                if (userName.length() > 0 && password.length() > 0) {
                    DavGatewayTray.debug("LDAP_REQ_BIND " + currentMessageId + " " + userName);
                    try {
                        session = ExchangeSessionFactory.getInstance(userName, password);
                        sendClient(currentMessageId, LDAP_REP_BIND, LDAP_SUCCESS, "");
                    } catch (IOException e) {
                        sendClient(currentMessageId, LDAP_REP_BIND, LDAP_INVALID_CREDENTIALS, "");
                    }
                } else {
                    DavGatewayTray.debug("LDAP_REQ_BIND " + currentMessageId + " anonymous" + userName);
                    // anonymous bind
                    sendClient(currentMessageId, LDAP_REP_BIND, LDAP_SUCCESS, "");
                }

            } else if (requestOperation == LDAP_REQ_UNBIND) {
                DavGatewayTray.debug("LDAP_REQ_UNBIND " + currentMessageId);
                if (session != null) {
                    ExchangeSessionFactory.close(session);
                    session = null;
                }
            } else if (requestOperation == LDAP_REQ_SEARCH) {
                reqBer.parseSeq(null);
                String dn = reqBer.parseString(isLdapV3());
                int scope = reqBer.parseEnumeration();
                /*int derefAliases =*/
                reqBer.parseEnumeration();
                int sizeLimit = reqBer.parseInt();
                if (sizeLimit > 100 || sizeLimit == 0) {
                    sizeLimit = 100;
                }
                int timelimit = reqBer.parseInt();
                /*boolean typesOnly =*/
                reqBer.parseBoolean();
                LdapFilter ldapFilter = parseFilter(reqBer);
                Set<String> returningAttributes = parseReturningAttributes(reqBer);

                int size = 0;
                DavGatewayTray.debug("LDAP_REQ_SEARCH " + currentMessageId + " base=" + dn + " scope: " + scope + " sizelimit: " + sizeLimit + " timelimit: " + timelimit +
                        " filter: " + ldapFilter.toString() + " returning attributes: " + returningAttributes);

                if (scope == SCOPE_BASE_OBJECT) {
                    if ("".equals(dn)) {
                        size = 1;
                        sendRootDSE(currentMessageId);
                    } else if (BASE_CONTEXT.equals(dn)) {
                        size = 1;
                        // root
                        sendBaseContext(currentMessageId);
                    } else if (dn.startsWith("uid=") && dn.indexOf(',') > 0 && session != null) {
                        // single user request
                        String uid = dn.substring("uid=".length(), dn.indexOf(','));
                        Map<String, Map<String, String>> persons = session.galFind("AN", uid);
                        Map<String, String> person = persons.get(uid.toLowerCase());
                        // filter out non exact results
                        if (persons.size() > 1 || person == null) {
                            persons = new HashMap<String, Map<String, String>>();
                            if (person != null) {
                                persons.put(uid.toLowerCase(), person);
                            }
                        }
                        size = persons.size();
                        sendPersons(currentMessageId, persons, returningAttributes);
                    } else {
                        DavGatewayTray.debug("LDAP_REQ_SEARCH " + currentMessageId + " unrecognized dn " + dn);
                    }

                } else if (BASE_CONTEXT.equalsIgnoreCase(dn) && session != null) {

                    Map<String, Map<String, String>> persons = new HashMap<String, Map<String, String>>();
                    if (ldapFilter.isFullSearch()) {
                        // full search
                        for (char c = 'A'; c < 'Z'; c++) {
                            if (persons.size() < sizeLimit) {
                                for (Map<String, String> person : session.galFind("AN", String.valueOf(c)).values()) {
                                    persons.put(person.get("AN"), person);
                                    if (persons.size() == sizeLimit) {
                                        break;
                                    }
                                }
                            }
                            if (persons.size() == sizeLimit) {
                                break;
                            }
                        }
                    } else {
                        for (Map.Entry<String, SimpleFilter> entry : ldapFilter.getOrFilterEntrySet()) {
                            if (persons.size() < sizeLimit) {
                                for (Map<String, String> person : session.galFind(entry.getKey(), entry.getValue().value).values()) {
                                    if ((entry.getValue().operator == LDAP_FILTER_SUBSTRINGS)
                                            || (entry.getValue().operator == LDAP_FILTER_EQUALITY &&
                                            entry.getValue().value.equalsIgnoreCase(person.get(entry.getKey())))) {
                                        persons.put(person.get("AN"), person);
                                    }
                                    if (persons.size() == sizeLimit) {
                                        break;
                                    }
                                }
                            }
                            if (persons.size() == sizeLimit) {
                                break;
                            }
                        }
                    }

                    size = persons.size();
                    DavGatewayTray.debug("LDAP_REQ_SEARCH " + currentMessageId + " found " + size + " results");
                    sendPersons(currentMessageId, persons, returningAttributes);
                    DavGatewayTray.debug("LDAP_REQ_SEARCH " + currentMessageId + " end");
                } else {
                    DavGatewayTray.debug("LDAP_REQ_SEARCH " + currentMessageId + " invalid dn " + dn);
                }

                if (size == sizeLimit) {
                    sendClient(currentMessageId, LDAP_REP_RESULT, LDAP_SIZE_LIMIT_EXCEEDED, "");
                } else {
                    sendClient(currentMessageId, LDAP_REP_RESULT, LDAP_SUCCESS, "");
                }
            } else if (requestOperation == LDAP_REQ_ABANDON) {
                int canceledMessageId = 0;
                try {
                    canceledMessageId = (Integer) parseIntWithTag.invoke(reqBer, LDAP_REQ_ABANDON);
                } catch (IllegalAccessException e) {
                    DavGatewayTray.error(e);
                } catch (InvocationTargetException e) {
                    DavGatewayTray.error(e);
                }
                DavGatewayTray.debug("LDAP_REQ_ABANDON " + currentMessageId + " for search " + canceledMessageId + ", too late !");
            } else {
                DavGatewayTray.debug("Unsupported operation: " + requestOperation);
                sendClient(currentMessageId, LDAP_REP_RESULT, LDAP_OTHER, "Unsupported operation");
            }
        } catch (IOException e) {
            try {
                sendErr(currentMessageId, LDAP_REP_RESULT, e);
            } catch (IOException e2) {
                DavGatewayTray.debug("Exception sending error to client", e2);
            }
            throw e;
        }
    }

    protected LdapFilter parseFilter(BerDecoder reqBer) throws IOException {
        LdapFilter ldapFilter = new LdapFilter();
        if (reqBer.peekByte() == LDAP_FILTER_PRESENT) {
            String attributeName = reqBer.parseStringWithTag(LDAP_FILTER_PRESENT, isLdapV3(), null).toLowerCase();
            if ("objectclass".equals(attributeName)) {
                ldapFilter.addFilter(attributeName, new SimpleFilter());
            } else {
                DavGatewayTray.warn("Unsupported filter");
            }
        } else {
            int[] seqSize = new int[1];
            int ldapFilterType = reqBer.parseSeq(seqSize);
            int end = reqBer.getParsePosition() + seqSize[0];

            parseNestedFilter(reqBer, ldapFilter, ldapFilterType, end);
        }
        return ldapFilter;
    }

    protected void parseNestedFilter(BerDecoder reqBer, LdapFilter ldapFilter, int ldapFilterType, int end) throws IOException {
        if (ldapFilterType == LDAP_FILTER_OR) {
            ldapFilter.startFilter(LDAP_FILTER_OR);
            // OR filter
            while (reqBer.getParsePosition() < end && reqBer.bytesLeft() > 0) {
                int ldapFilterOperator = reqBer.parseSeq(null);
                parseNestedFilter(reqBer, ldapFilter, ldapFilterOperator, end);
            }
            ldapFilter.endFilter();
        } else if (ldapFilterType == LDAP_FILTER_AND) {
            ldapFilter.startFilter(LDAP_FILTER_AND);
            // AND filter
            while (reqBer.getParsePosition() < end && reqBer.bytesLeft() > 0) {
                int ldapFilterOperator = reqBer.parseSeq(null);
                parseNestedFilter(reqBer, ldapFilter, ldapFilterOperator, end);
            }
            ldapFilter.endFilter();
        } else {
            // simple filter
            parseSimpleFilter(reqBer, ldapFilter, ldapFilterType);
        }
    }

    protected void parseSimpleFilter(BerDecoder reqBer, LdapFilter ldapFilter, int ldapFilterOperator) throws IOException {
        String attributeName = reqBer.parseString(isLdapV3()).toLowerCase();

        StringBuilder value = new StringBuilder();
        if (ldapFilterOperator == LDAP_FILTER_SUBSTRINGS) {
            // Thunderbird sends values with space as separate strings, rebuild value
            int[] seqSize = new int[1];
            /*LBER_SEQUENCE*/
            reqBer.parseSeq(seqSize);
            int end = reqBer.getParsePosition() + seqSize[0];
            while (reqBer.getParsePosition() < end && reqBer.bytesLeft() > 0) {
                int ldapFilterMode = reqBer.peekByte();
                if (value.length() > 0) {
                    value.append(' ');
                }
                value.append(reqBer.parseStringWithTag(ldapFilterMode, isLdapV3(), null));
            }
        } else if (ldapFilterOperator == LDAP_FILTER_EQUALITY) {
            value.append(reqBer.parseString(isLdapV3()));
        } else {
            DavGatewayTray.warn("Unsupported filter value");
        }

        ldapFilter.addFilter(attributeName, new SimpleFilter(value.toString(), ldapFilterOperator));
    }

    protected Set<String> parseReturningAttributes(BerDecoder reqBer) throws IOException {
        Set<String> returningAttributes = new HashSet<String>();
        int[] seqSize = new int[1];
        reqBer.parseSeq(seqSize);
        int end = reqBer.getParsePosition() + seqSize[0];
        while (reqBer.getParsePosition() < end && reqBer.bytesLeft() > 0) {
            returningAttributes.add(reqBer.parseString(isLdapV3()).toLowerCase());
        }
        return returningAttributes;
    }

    /**
     * Convert to LDAP attributes and send entry
     *
     * @param currentMessageId    current Message Id
     * @param persons             persons Map
     * @param returningAttributes returning attributes
     * @throws IOException on error
     */
    protected void sendPersons(int currentMessageId, Map<String, Map<String, String>> persons, Set<String> returningAttributes) throws IOException {
        boolean needObjectClasses = returningAttributes.contains("objectclass") || returningAttributes.size() == 0;
        boolean returnAllAttributes = returningAttributes.size() == 0;
        for (Map<String, String> person : persons.values()) {
            boolean needDetails = returnAllAttributes;
            if (!needDetails) {
                for (String attributeName : EXTENDED_ATTRIBUTES) {
                    if (returningAttributes.contains(attributeName)) {
                        needDetails = true;
                        break;
                    }
                }
            }

            // add detailed information
            if (needDetails) {
                session.galLookup(person);
            }

            returningAttributes.add("uid");

            Map<String, Object> ldapPerson = new HashMap<String, Object>();

            // Process all attributes that are mapped from exchange

            for (Map.Entry<String, String> entry : ATTRIBUTE_MAP.entrySet()) {
                String ldapAttribute = entry.getKey();
                String exchangeAttribute = entry.getValue();
                String value = person.get(exchangeAttribute);
                if (value != null
                        && (returnAllAttributes || returningAttributes.contains(ldapAttribute.toLowerCase()))) {
                    ldapPerson.put(ldapAttribute, value);
                }
            }
            if (needObjectClasses) {
                ldapPerson.put("objectClass", PERSON_OBJECT_CLASSES);
            }
            DavGatewayTray.debug("LDAP_REQ_SEARCH " + currentMessageId + " send uid=" + ldapPerson.get("uid") + " " + ldapPerson);
            sendEntry(currentMessageId, "uid=" + ldapPerson.get("uid") + "," + BASE_CONTEXT, ldapPerson);
        }

    }

    /**
     * Send Root DSE
     *
     * @param currentMessageId current message id
     * @throws IOException on error
     */
    protected void sendRootDSE(int currentMessageId) throws IOException {
        DavGatewayTray.debug("Sending root DSE");

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("objectClass", "top");
        attributes.put("namingContexts", BASE_CONTEXT);

        sendEntry(currentMessageId, "Root DSE", attributes);
    }

    /**
     * Send Base Context
     *
     * @param currentMessageId current message id
     * @throws IOException on error
     */
    protected void sendBaseContext(int currentMessageId) throws IOException {
        List<String> objectClasses = new ArrayList<String>();
        objectClasses.add("top");
        objectClasses.add("organizationalUnit");
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("objectClass", objectClasses);
        attributes.put("description", "DavMail Gateway LDAP for " + Settings.getProperty("davmail.url"));
        sendEntry(currentMessageId, BASE_CONTEXT, attributes);
    }

    protected void sendEntry(int currentMessageId, String dn, Map<String, Object> attributes) throws IOException {
        responseBer.reset();
        responseBer.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
        responseBer.encodeInt(currentMessageId);
        responseBer.beginSeq(LDAP_REP_SEARCH);
        responseBer.encodeString(dn, isLdapV3());
        responseBer.beginSeq(LBER_SEQUENCE);
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            responseBer.beginSeq(LBER_SEQUENCE);
            responseBer.encodeString(entry.getKey(), isLdapV3());
            responseBer.beginSeq(LBER_SET);
            Object values = entry.getValue();
            if (values instanceof String) {
                responseBer.encodeString((String) values, isLdapV3());
            } else if (values instanceof List) {
                for (Object value : (List) values) {
                    responseBer.encodeString((String) value, isLdapV3());
                }
            } else {
                throw new IllegalArgumentException();
            }
            responseBer.endSeq();
            responseBer.endSeq();
        }
        responseBer.endSeq();
        responseBer.endSeq();
        responseBer.endSeq();
        sendResponse();
    }

    protected void sendErr(int currentMessageId, int responseOperation, Exception e) throws IOException {
        String message = e.getMessage();
        if (message == null) {
            message = e.toString();
        }
        sendClient(currentMessageId, responseOperation, LDAP_OTHER, message);
    }

    protected void sendClient(int currentMessageId, int responseOperation, int status, String message) throws IOException {
        responseBer.reset();

        responseBer.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
        responseBer.encodeInt(currentMessageId);
        responseBer.beginSeq(responseOperation);
        responseBer.encodeInt(status, LBER_ENUMERATED);
        // dn
        responseBer.encodeString("", isLdapV3());
        // error message
        responseBer.encodeString(message, isLdapV3());
        responseBer.endSeq();
        responseBer.endSeq();
        sendResponse();
    }

    protected void sendResponse() throws IOException {
        //Ber.dumpBER(System.out, ">\n", responseBer.getBuf(), 0, responseBer.getDataLen());
        os.write(responseBer.getBuf(), 0, responseBer.getDataLen());
        os.flush();
    }

    class LdapFilter {
        StringBuilder filterString = new StringBuilder();
        int ldapFilterType = 0;
        boolean isFullSearch = true;
        Map<String, SimpleFilter> orCriteria = new HashMap<String, SimpleFilter>();
        Map<String, SimpleFilter> andCriteria = new HashMap<String, SimpleFilter>();

        public void addFilter(String attributeName, SimpleFilter simpleFilter) {
            filterString.append('(').append(attributeName).append('=').append(simpleFilter.value).append(')');

            String exchangeAttributeName = CRITERIA_MAP.get(attributeName);
            if (exchangeAttributeName != null) {
                isFullSearch = false;
                if (ldapFilterType == 0 || ldapFilterType == LDAP_FILTER_OR) {
                    orCriteria.put(exchangeAttributeName, simpleFilter);
                } else if (ldapFilterType == LDAP_FILTER_AND) {
                    andCriteria.put(exchangeAttributeName, simpleFilter);
                }
            } else if ("objectclass".equals(attributeName) && SimpleFilter.STAR.equals(simpleFilter.value)) {
                isFullSearch = true;
            } else {
                DavGatewayTray.warn("Unsupported filter attribute: " + attributeName + " = " + simpleFilter.value);
            }
        }

        @Override
        public String toString() {
            return filterString.toString();
        }

        public void startFilter(int ldapFilterType) {
            this.ldapFilterType = ldapFilterType;
            filterString.append('(');
            if (ldapFilterType == LDAP_FILTER_OR) {
                filterString.append('|');
            } else if (ldapFilterType == LDAP_FILTER_AND) {
                filterString.append('&');
            }
        }

        public void endFilter() {
            ldapFilterType = 0;
            filterString.append(')');
        }

        public boolean isFullSearch() {
            return isFullSearch;
        }

        public Set<Map.Entry<String, SimpleFilter>> getOrFilterEntrySet() {
            return orCriteria.entrySet();
        }
    }

    class SimpleFilter {
        static final String STAR = "*";
        final String value;
        final int operator;

        SimpleFilter() {
            this.value = SimpleFilter.STAR;
            this.operator = LDAP_FILTER_SUBSTRINGS;
        }

        SimpleFilter(String value, int ldapFilterOperator) {
            this.value = value;
            this.operator = ldapFilterOperator;
        }
    }

}

