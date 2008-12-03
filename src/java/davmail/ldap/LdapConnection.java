package davmail.ldap;

import com.sun.jndi.ldap.Ber;
import com.sun.jndi.ldap.BerDecoder;
import com.sun.jndi.ldap.BerEncoder;
import davmail.AbstractConnection;
import davmail.exchange.ExchangeSessionFactory;
import davmail.tray.DavGatewayTray;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

/**
 * Handle a caldav connection.
 */
public class LdapConnection extends AbstractConnection {
    static final int LDAP_VERSION2 = 0x02;
    static final int LDAP_VERSION3 = 0x03;              // LDAPv3

    static final int LDAP_OTHER = 80;
    static final int LDAP_REQ_BIND = 0x60;
    static final int LDAP_REQ_SEARCH = 99;
    static final int LDAP_REP_BIND = 0x61;
    static final int LDAP_REP_SEARCH = 0x64;
    static final int LDAP_REP_RESULT = 0x65;
    static final int LDAP_SUCCESS = 0;
    static final int LDAP_SIZE_LIMIT_EXCEEDED = 4;
    static final int LDAP_INVALID_CREDENTIALS = 49;


    static final int LDAP_FILTER_OR = 0xa1;

    static final int LDAP_FILTER_SUBSTRINGS = 0xa4;
    static final int LDAP_FILTER_GE = 0xa5;
    static final int LDAP_FILTER_LE = 0xa6;
    static final int LDAP_FILTER_PRESENT = 0x87;
    static final int LDAP_FILTER_APPROX = 0xa8;

    static final int LDAP_SUBSTRING_INITIAL = 0x80;
    static final int LDAP_SUBSTRING_ANY = 0x81;
    static final int LDAP_SUBSTRING_FINAL = 0x82;

    static final int LBER_ENUMERATED = 0x0a;
    static final int LBER_SET = 0x31;
    static final int LBER_SEQUENCE = 0x30;
    static final int LDAP_REQ_UNBIND = 0x42;

    static final int SCOPE_BASE_OBJECT = 0;
    static final int SCOPE_ONE_LEVEL = 1;
    static final int SCOPE_SUBTREE = 2;

    protected InputStream is;

    // Initialize the streams and start the thread
    public LdapConnection(String name, Socket clientSocket) {
        super(name + "-" + clientSocket.getPort(), clientSocket);
        try {
            is = new BufferedInputStream(client.getInputStream());
            os = new BufferedOutputStream(client.getOutputStream());
        } catch (IOException e) {
            close();
            DavGatewayTray.error("Exception while getting socket streams", e);
        }
    }

    public void run() {
        byte inbuf[] = new byte[2048];   // Buffer for reading incoming bytes
        int inMsgId = 0;    // Message id of incoming response
        int bytesread;  // Number of bytes in inbuf
        int bytesleft;  // Number of bytes that need to read for completing resp
        int br;         // Temp; number of bytes read from stream
        int offset;     // Offset of where to store bytes in inbuf
        int seqlen;     // Length of ASN sequence
        int seqlenlen;  // Number of sequence length bytes
        int operation = 0;
        boolean eos;    // End of stream
        BerDecoder reqBer;    // Decoder for ASN.1 BER data from inbuf

        try {
            while (true) {
                offset = 0;
                seqlen = 0;
                seqlenlen = 0;


                // check that it is the beginning of a sequence
                bytesread = is.read(inbuf, offset, 1);
                if (bytesread < 0) {
                    break; // EOF
                }

                if (inbuf[offset++] != (Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR))
                    continue;

                // get length of sequence
                bytesread = is.read(inbuf, offset, 1);
                if (bytesread < 0)
                    break; // EOF
                seqlen = inbuf[offset++];

                // if high bit is on, length is encoded in the
                // subsequent length bytes and the number of length bytes
                // is equal to & 0x80 (i.e. length byte with high bit off).
                if ((seqlen & 0x80) == 0x80) {
                    seqlenlen = seqlen & 0x7f;  // number of length bytes

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
                    byte nbuf[] = new byte[offset + bytesleft];
                    System.arraycopy(inbuf, 0, nbuf, 0, offset);
                    inbuf = nbuf;
                }
                while (bytesleft > 0) {
                    bytesread = is.read(inbuf, offset, bytesleft);
                    if (bytesread < 0)
                        break; // EOF
                    offset += bytesread;
                    bytesleft -= bytesread;
                }

                reqBer = new BerDecoder(inbuf, 0, offset);

                reqBer.parseSeq(null);
                inMsgId = reqBer.parseInt();
                operation = reqBer.parseSeq(null);

                Ber.dumpBER(System.out, "request\n", inbuf, 0, offset);

                if (operation == LDAP_REQ_BIND) {
                    int ldapVersion = reqBer.parseInt();
                    String userName = reqBer.parseString(ldapVersion == LDAP_VERSION3);
                    String password = reqBer.parseStringWithTag(0x80, ldapVersion == LDAP_VERSION3, null);

                    if (userName.length() > 0 && password.length() > 0) {
                        try {
                            session = ExchangeSessionFactory.getInstance(userName, password);
                            sendClient(inMsgId, LDAP_REP_BIND, LDAP_SUCCESS, "");
                        } catch (IOException e) {
                            sendClient(inMsgId, LDAP_REP_BIND, LDAP_INVALID_CREDENTIALS, "");
                        }
                    } else {
                        // anonymous bind
                        sendClient(inMsgId, LDAP_REP_BIND, LDAP_SUCCESS, "");
                    }

                } else if (operation == LDAP_REQ_UNBIND) {
                    if (session != null) {
                        session.close();
                        session = null;
                    }
                } else if (operation == LDAP_REQ_SEARCH) {
                    String dn = reqBer.parseString(true);
                    int scope = reqBer.parseEnumeration();
                    int deref = reqBer.parseEnumeration();
                    int sizeLimit = reqBer.parseInt();
                    if (sizeLimit > 100 || sizeLimit == 0) {
                        sizeLimit = 100;
                    }
                    int timelimit = reqBer.parseInt();
                    boolean attrsOnly = reqBer.parseBoolean();
                    int size = 0;

                    BerEncoder retBer = new BerEncoder(2048);

                    if (scope == SCOPE_BASE_OBJECT) {
                        if ("".equals(dn)) {
                            // Root DSE
                            size = 1;
                            // root
                            retBer.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
                            retBer.encodeInt(inMsgId);
                            retBer.beginSeq(LDAP_REP_SEARCH);

                            retBer.encodeString("Root DSE", true);
                            retBer.beginSeq(LBER_SEQUENCE);
                            retBer.beginSeq(LBER_SEQUENCE);
                            retBer.encodeString("namingContexts", true);
                            retBer.beginSeq(LBER_SET);
                            retBer.encodeString("ou=people", true);
                            retBer.endSeq();
                            retBer.endSeq();
                            retBer.endSeq();

                            retBer.endSeq();
                            retBer.endSeq();
                        } if ("ou=people".equals(dn)) {
                            size = 1;
                            // root
                            retBer.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
                            retBer.encodeInt(inMsgId);
                            retBer.beginSeq(LDAP_REP_SEARCH);

                            retBer.encodeString("ou=people", true);
                            retBer.beginSeq(LBER_SEQUENCE);
                            retBer.beginSeq(LBER_SEQUENCE);
                            retBer.encodeString("description", true);
                            retBer.beginSeq(LBER_SET);
                            retBer.encodeString("people", true);
                            retBer.endSeq();
                            retBer.endSeq();
                            retBer.endSeq();

                            retBer.endSeq();
                            retBer.endSeq();
                        } else if (dn.startsWith("uid=") && session != null) {
                            String uid = dn.substring(4, dn.indexOf(','));
                            Map<String, Map<String, String>> persons = session.galFind("AN", uid);
                            size = persons.size();
                            // TODO refactor
                            for (Map<String, String> person : persons.values()) {
                                HashMap<String, String> ATTRIBUTE_MAP = new HashMap<String, String>();
                                ATTRIBUTE_MAP.put("uid", "AN");
                                ATTRIBUTE_MAP.put("mail", "EM");
                                ATTRIBUTE_MAP.put("displayName", "DN");
                                ATTRIBUTE_MAP.put("telephoneNumber", "PH");
                                ATTRIBUTE_MAP.put("l", "OFFICE");
                                ATTRIBUTE_MAP.put("company", "CP");
                                ATTRIBUTE_MAP.put("title", "TL");

                                ATTRIBUTE_MAP.put("cn", "DN");
                                ATTRIBUTE_MAP.put("givenName", "first");
                                ATTRIBUTE_MAP.put("initials", "initials");
                                ATTRIBUTE_MAP.put("sn", "last");
                                ATTRIBUTE_MAP.put("street", "street");
                                ATTRIBUTE_MAP.put("st", "state");
                                ATTRIBUTE_MAP.put("postalCode", "zip");
                                ATTRIBUTE_MAP.put("c", "country");
                                ATTRIBUTE_MAP.put("departement", "department");
                                ATTRIBUTE_MAP.put("mobile", "mobile");

                                Map<String, String> ldapPerson = new HashMap<String, String>();

                                for (Map.Entry<String, String> entry : ATTRIBUTE_MAP.entrySet()) {
                                    String ldapAttribute = entry.getKey();
                                    String exchangeAttribute = entry.getValue();
                                    String value = person.get(exchangeAttribute);
                                    if (value != null) {
                                        ldapPerson.put(ldapAttribute, value);
                                    }
                                }

                                retBer.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
                                retBer.encodeInt(inMsgId);
                                retBer.beginSeq(LDAP_REP_SEARCH);

                                retBer.encodeString("uid=" + ldapPerson.get("uid") + ",ou=people", true);
                                retBer.beginSeq(LBER_SEQUENCE);

                                for (Map.Entry<String, String> entry : ldapPerson.entrySet()) {
                                    retBer.beginSeq(LBER_SEQUENCE);
                                    retBer.encodeString(entry.getKey(), true);
                                    retBer.beginSeq(LBER_SET);
                                    retBer.encodeString(entry.getValue(), true);
                                    retBer.endSeq();
                                    retBer.endSeq();
                                }
                                retBer.endSeq();

                                retBer.endSeq();
                                retBer.endSeq();

                            }
                            //end TODO
                        }

                    } else if ("ou=people".equals(dn) && session != null) {
                        // filter
                        Map<String, String> criteria = new HashMap<String, String>();
                        try {
                            int[] seqSize = new int[1];
                            int ldapFilterType = reqBer.parseSeq(seqSize);
                            int end = reqBer.getParsePosition() + seqSize[0];
                            if (ldapFilterType == LDAP_FILTER_OR) {
                                System.out.print("(|");
                                while (reqBer.getParsePosition() < end && reqBer.bytesLeft() > 0) {
                                    int ldapFilterOperator = reqBer.parseSeq(null);
                                    if (ldapFilterOperator == LDAP_FILTER_SUBSTRINGS) {
                                        String attributeName = reqBer.parseString(true).toLowerCase();
                                        /*LBER_SEQUENCE*/
                                        reqBer.parseSeq(null);
                                        int ldapFilterMode = reqBer.peekByte();
                                        String value = reqBer.parseStringWithTag(ldapFilterMode, true, null);
                                        if (ldapFilterMode == LDAP_SUBSTRING_ANY) {
                                            System.out.print("(" + attributeName + "=*" + value + "*)");
                                        } else if (ldapFilterMode == LDAP_SUBSTRING_INITIAL) {
                                            System.out.print("(" + attributeName + "=" + value + "*)");
                                        } else if (ldapFilterMode == LDAP_SUBSTRING_FINAL) {
                                            System.out.print("(" + attributeName + "=*" + value + ")");
                                        }
                                        criteria.put(attributeName, value);
                                    }
                                }
                                System.out.println(")");
                                // simple filter
                            } else if (ldapFilterType == LDAP_FILTER_SUBSTRINGS) {
                                // TODO refactor
                                String attributeName = reqBer.parseString(true).toLowerCase();
                                /*LBER_SEQUENCE*/
                                reqBer.parseSeq(null);
                                int ldapFilterMode = reqBer.peekByte();
                                String value = reqBer.parseStringWithTag(ldapFilterMode, true, null);
                                if (ldapFilterMode == LDAP_SUBSTRING_ANY) {
                                    System.out.print("(" + attributeName + "=*" + value + "*)");
                                } else if (ldapFilterMode == LDAP_SUBSTRING_INITIAL) {
                                    System.out.print("(" + attributeName + "=" + value + "*)");
                                } else if (ldapFilterMode == LDAP_SUBSTRING_FINAL) {
                                    System.out.print("(" + attributeName + "=*" + value + ")");
                                }
                                criteria.put(attributeName, value);
                            }
                        } catch (Exception e) {
                            // unsupported filter
                            System.out.println(e.getMessage());
                        }


                        Map<String, Map<String, String>> persons = new HashMap<String, Map<String, String>>();
                        if (criteria.size() > 0) {
                            if (criteria.containsKey("displayname")) {
                                for (Map<String, String> person : session.galFind("DN", criteria.get("displayname")).values()) {
                                    persons.put(person.get("AN"), person);
                                    if (persons.size() == sizeLimit) {
                                        break;
                                    }
                                }
                            } else if (criteria.containsKey("cn")) {
                                for (Map<String, String> person : session.galFind("DN", criteria.get("cn")).values()) {
                                    persons.put(person.get("AN"), person);
                                    if (persons.size() == sizeLimit) {
                                        break;
                                    }
                                }
                            }
                            if (criteria.containsKey("givenname") && persons.size() < sizeLimit) {
                                for (Map<String, String> person : session.galFind("FN", criteria.get("givenname")).values()) {
                                    persons.put(person.get("AN"), person);
                                    if (persons.size() == sizeLimit) {
                                        break;
                                    }
                                }
                            }
                            if (criteria.containsKey("sn") && persons.size() < sizeLimit) {
                                for (Map<String, String> person : session.galFind("LN", criteria.get("sn")).values()) {
                                    persons.put(person.get("AN"), person);
                                    if (persons.size() == sizeLimit) {
                                        break;
                                    }
                                }
                            }
                            if (criteria.containsKey("title") && persons.size() < sizeLimit) {
                                for (Map<String, String> person : session.galFind("TL", criteria.get("title")).values()) {
                                    persons.put(person.get("AN"), person);
                                    if (persons.size() == sizeLimit) {
                                        break;
                                    }
                                }
                            }
                            if (criteria.containsKey("company") && persons.size() < sizeLimit) {
                                for (Map<String, String> person : session.galFind("CP", criteria.get("company")).values()) {
                                    persons.put(person.get("AN"), person);
                                    if (persons.size() == sizeLimit) {
                                        break;
                                    }
                                }
                            }
                            if (criteria.containsKey("o") && persons.size() < sizeLimit) {
                                for (Map<String, String> person : session.galFind("CP", criteria.get("o")).values()) {
                                    persons.put(person.get("AN"), person);
                                    if (persons.size() == sizeLimit) {
                                        break;
                                    }
                                }
                            }
                            if (criteria.containsKey("department") && persons.size() < sizeLimit) {
                                for (Map<String, String> person : session.galFind("DP", criteria.get("department")).values()) {
                                    persons.put(person.get("AN"), person);
                                    if (persons.size() == sizeLimit) {
                                        break;
                                    }
                                }
                            }
                            if (criteria.containsKey("l") && persons.size() < sizeLimit) {
                                for (Map<String, String> person : session.galFind("OF", criteria.get("l")).values()) {
                                    persons.put(person.get("AN"), person);
                                    if (persons.size() == sizeLimit) {
                                        break;
                                    }
                                }
                            }
                            if (criteria.containsKey("l") && persons.size() < sizeLimit) {
                                for (Map<String, String> person : session.galFind("OF", criteria.get("l")).values()) {
                                    persons.put(person.get("AN"), person);
                                    if (persons.size() == sizeLimit) {
                                        break;
                                    }
                                }
                            }
                        } else {
                            // full list
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
                        }

                        size = persons.size();
                        for (Map<String, String> person : persons.values()) {
                            HashMap<String, String> ATTRIBUTE_MAP = new HashMap<String, String>();
                            ATTRIBUTE_MAP.put("AN", "uid");
                            ATTRIBUTE_MAP.put("EM", "mail");
                            ATTRIBUTE_MAP.put("DN", "displayName");
                            ATTRIBUTE_MAP.put("PH", "telephoneNumber");
                            ATTRIBUTE_MAP.put("OFFICE", "l");
                            ATTRIBUTE_MAP.put("CP", "company");
                            ATTRIBUTE_MAP.put("TL", "title");

                            ATTRIBUTE_MAP.put("first", "givenName");
                            ATTRIBUTE_MAP.put("initials", "initials");
                            ATTRIBUTE_MAP.put("last", "sn");
                            ATTRIBUTE_MAP.put("street", "street");
                            ATTRIBUTE_MAP.put("state", "st");
                            ATTRIBUTE_MAP.put("zip", "postalCode");
                            ATTRIBUTE_MAP.put("country", "c");
                            ATTRIBUTE_MAP.put("departement", "department");
                            ATTRIBUTE_MAP.put("mobile", "mobile");

                            Map<String, String> ldapPerson = new HashMap<String, String>();
                            for (Map.Entry<String, String> entry : person.entrySet()) {
                                String ldapAttribute = ATTRIBUTE_MAP.get(entry.getKey());
                                if (ldapAttribute != null) {
                                    ldapPerson.put(ldapAttribute, entry.getValue());
                                }
                            }


                            retBer.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
                            retBer.encodeInt(inMsgId);
                            retBer.beginSeq(LDAP_REP_SEARCH);

                            retBer.encodeString("uid=" + ldapPerson.get("uid") + ",ou=people", true);
                            retBer.beginSeq(LBER_SEQUENCE);

                            for (Map.Entry<String, String> entry : ldapPerson.entrySet()) {
                                retBer.beginSeq(LBER_SEQUENCE);
                                retBer.encodeString(entry.getKey(), true);
                                retBer.beginSeq(LBER_SET);
                                retBer.encodeString(entry.getValue(), true);
                                retBer.endSeq();
                                retBer.endSeq();
                            }
                            retBer.endSeq();

                            retBer.endSeq();
                            retBer.endSeq();

                        }
                    }

                    Ber.dumpBER(System.out, "response\n", retBer.getBuf(), 0, retBer.getDataLen());

                    os.write(retBer.getBuf(), 0, retBer.getDataLen());
                    os.flush();
                    if (size == sizeLimit) {
                        sendClient(inMsgId, LDAP_REP_RESULT, LDAP_SIZE_LIMIT_EXCEEDED, "");
                    } else {
                        sendClient(inMsgId, LDAP_REP_RESULT, LDAP_SUCCESS, "");
                    }
                } else {
                    sendClient(inMsgId, 0, LDAP_OTHER, "Unsupported operation");
                }


            }
        } catch (IOException e) {
            DavGatewayTray.error(e);
            try {
                sendErr(inMsgId, operation, e);
            } catch (IOException e2) {
                DavGatewayTray.debug("Exception sending error to client", e2);
            }
        } finally {
            close();
        }
        DavGatewayTray.resetIcon();
    }

    public void sendErr(int inMsgId, int operation, Exception e) throws IOException {
        String message = e.getMessage();
        if (message == null) {
            message = e.toString();
        }
        sendClient(inMsgId, operation, LDAP_OTHER, message);
    }

    public void sendClient(int inMsgId, int operation, int status, String message) throws IOException {
        BerEncoder retBer = new BerEncoder(2048);

        retBer.beginSeq(Ber.ASN_SEQUENCE | Ber.ASN_CONSTRUCTOR);
        retBer.encodeInt(inMsgId);
        retBer.beginSeq(operation);
        retBer.encodeInt(status, LBER_ENUMERATED);
        // dn
        retBer.encodeString("", true);
        // error message
        retBer.encodeString(message, true);
        retBer.endSeq();

        retBer.endSeq();

        os.write(retBer.getBuf(), 0, retBer.getDataLen());
        os.flush();
    }


}

