/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2010  Mickael Guessant
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package davmail.http;

import org.apache.commons.codec.binary.Base64;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;

public class NTLMMessageDecoder {
    static final int NTLMSSP_KEY_56 = 0x80000000;
    static final int NTLMSSP_KEY_EXCHANGE = 0x40000000;
    static final int NTLMSSP_KEY_128 = 0x20000000;

    static final int NTLMSSP_TARGET_INFO = 0x00800000;

    static final int NTLMSSP_NTLM2_KEY = 0x00080000;
    static final int NTLMSSP_CHALL_NOT_NT = 0x00040000;
    static final int NTLMSSP_CHALL_ACCEPT = 0x00020000;
    static final int NTLMSSP_CHALL_INIT = 0x00010000;
    static final int NTLMSSP_ALWAYS_SIGN = 0x00008000;

    static final long NTLMSSP_NEGOTIATE_UNICODE = 0x00000001;
    static final long NTLMSSP_REQUEST_TARGET = 0x00000004;
    static final long NTLMSSP_NEGOTIATE_OEM_DOMAIN_SUPPLIED = 0x00001000;
    static final long NTLMSSP_NEGOTIATE_OEM_WORKSTATION_SUPPLIED = 0x00002000;
    static final long NTLMSSP_NEGOTIATE_TARGET_INFO = 0x00800000;
    static final long NTLMSSP_NEGOTIATE_ALWAYS_SIGN = 0x00008000;
    static final long NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY = 0x00080000;

    static final int NTLMSSP_LOCAL_CALL = 0x00004000;
    static final int NTLMSSP_WORKSTATION = 0x00002000;
    static final int NTLMSSP_DOMAIN = 0x00001000;

    static final int NTLMSSP_NTLM_KEY = 0x00000200;
    static final int NTLMSSP_NETWARE = 0x00000100;
    static final int NTLMSSP_LM_KEY = 0x00000080;
    static final int NTLMSSP_DATAGRAM = 0x00000040;
    static final int NTLMSSP_SEAL = 0x00000020;
    static final int NTLMSSP_SIGN = 0x00000010;

    static final int NTLMSSP_TARGET = 0x00000004;
    static final int NTLMSSP_OEM = 0x00000002;
    static final int NTLMSSP_UNICODE = 0x00000001;

    private final String base64Message;

    public static String decode(String message) {
        return new NTLMMessageDecoder(message).decodeMessage();
    }

    NTLMMessageDecoder(String base64Message) {
        this.base64Message = base64Message;
    }

    public String decodeMessage() {
        StringBuilder buffer = new StringBuilder();
        byte[] message = Base64.decodeBase64(base64Message.getBytes());
        String prefix = new String(message, 0, 7);
        if (!"NTLMSSP".equals(prefix) || message[7] != 0) {
            buffer.append("Invalid Prefix: ");
        } else {
            int messageType = getInt(message, 8);
            buffer.append("NTLM type: ").append(messageType);

            if (messageType == 1) {
                buffer.append(getFlags(message, 12));
                buffer.append(" domain: ").append(getStringValue(message, 16, "ASCII"));
                buffer.append(" host: ").append(getStringValue(message, 24, "ASCII"));
            } else if (messageType == 2) {
                buffer.append(" target: ").append(getStringValue(message, 12, "UnicodeLittleUnmarked"));
                buffer.append(getFlags(message, 20));
                byte[] challenge = getBytes(message, 24, 8);
                buffer.append(" challenge: ").append(toHexString(challenge));
                if (message.length > 32) {
                    buffer.append(" context: 0x").append(Integer.toHexString(getInt(message, 32))).append(" 0x").append(Integer.toHexString(getInt(message, 36)));
                }
                if (message.length > 40) {
                    buffer.append(" target info: ").append(getValues(message, 40, "UnicodeLittleUnmarked"));
                }
                if (message.length > 48) {
                    buffer.append(getOSVersion(message, 48));
                }

            } else if (messageType == 3) {
                buffer.append(" lm response: ").append(getByteValue(message, 12));
                buffer.append(" ntlm response: ").append(getByteValue(message, 20));
                buffer.append(" target name: ").append(getStringValue(message, 28, "UnicodeLittleUnmarked"));
                buffer.append(" user name: ").append(getStringValue(message, 36, "UnicodeLittleUnmarked"));
                buffer.append(" workstation name: ").append(getStringValue(message, 44, "UnicodeLittleUnmarked"));
                // optional
                if (getShort(message, 32) > 52) {
                    buffer.append(" session key: ").append(getByteValue(message, 52));
                }
                if (getShort(message, 32) > 60) {
                    buffer.append(getFlags(message, 60));
                }
                if (getShort(message, 32) > 64) {
                    buffer.append(getOSVersion(message, 64));
                }
            }
        }
        return buffer.toString();
    }


    public int getShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) + ((data[offset + 1] & 0xFF) << 8);
    }

    public int getInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) + ((data[offset + 1] & 0xFF) << 8) + ((data[offset + 2] & 0xFF) << 16) + ((data[offset + 3] & 0xFF) << 24);
    }

    public String getStringValue(byte[] data, int offset, String encoding) {
        int length = getShort(data, offset);
        int maxLength = getShort(data, offset + 2);
        int valueOffset = getInt(data, offset + 4);
        String value = "";
        if (valueOffset + length <= data.length) {
            try {
                value = new String(data, valueOffset, length, encoding);
            } catch (UnsupportedEncodingException e) {
                value = "[Invalid encoding " + encoding + "]";
            }
        }
        return "(length: " + length + " maxLength: " + maxLength + " offset: " + valueOffset + " value:" + value + ")";
    }

    public String getValueType(int valueType) {
        if (valueType == 0) {
            return "EOL";
        } else if (valueType == 1) {
            return "NBCOMPUTERNAME";
        } else if (valueType == 2) {
            return "NBDOMAINNAME";
        } else if (valueType == 3) {
            return "DNSCOMPUTERNAME";
        } else if (valueType == 4) {
            return "DNSDOMAINNAME";
        } else if (valueType == 5) {
            return "DNSTREENAME";
        } else if (valueType == 6) {
            return "FLAGS";
        } else if (valueType == 7) {
            return "TIMESTAMP";
        } else if (valueType == 8) {
            return "SINGLEHOST";
        } else if (valueType == 9) {
            return "TARGETNAME";
        } else if (valueType == 10) {
            return "CHANNELBINDING";
        } else {
            return String.valueOf(valueType);
        }
    }

    public String getValues(byte[] data, int offset, String encoding) {
        StringBuilder buffer = new StringBuilder();
        int length = getShort(data, offset);
        int maxLength = getShort(data, offset + 2);
        int valueOffset = getInt(data, offset + 4);
        // String array available at valueOffset
        int valueType = getShort(data, valueOffset);
        int valueLength = getShort(data, valueOffset + 2);
        while (valueLength > 0) {
            String value;
            if (valueType == 7) {
                long timestamp = ByteBuffer.wrap(data, valueOffset + 4, valueLength).order(ByteOrder.LITTLE_ENDIAN).getLong();
                value = timestamp + " " + new Date((timestamp - 116444736000000000L) / 10000);
            } else {
                try {
                    value = new String(data, valueOffset + 4, valueLength, encoding);
                } catch (UnsupportedEncodingException e) {
                    value = "[Invalid encoding " + encoding + "]";
                }
            }
            buffer.append("(").append(getValueType(valueType)).append(": ").append(value).append(")");
            valueOffset += valueLength + 4;
            valueType = getShort(data, valueOffset);
            valueLength = getShort(data, valueOffset + 2);
        }

        return buffer.toString();
    }

    public String getByteValue(byte[] data, int offset) {
        int length = getShort(data, offset);
        int maxLength = getShort(data, offset + 2);
        int valueOffset = getInt(data, offset + 4);
        byte[] value = getBytes(data, valueOffset, length);
        return "(length: " + length + " maxLength: " + maxLength + " offset: " + valueOffset + " value:" + toHexString(value) + ")";
    }

    public byte[] getBytes(byte[] data, int offset, int length) {
        byte[] value = null;
        if (offset + length <= data.length) {
            value = new byte[length];
            System.arraycopy(data, offset, value, 0, length);
        }
        return value;
    }

    public String getFlags(byte[] data, int offset) {
        StringBuilder buffer = new StringBuilder();
        int flags = getInt(data, offset);
        buffer.append(" flags: 0x").append(Integer.toHexString(flags));

        if ((flags & NTLMSSP_KEY_56) != 0) {
            buffer.append(" NTLMSSP_KEY_56");
        }
        if ((flags & NTLMSSP_KEY_EXCHANGE) != 0) {
            buffer.append(" NTLMSSP_KEY_EXCHANGE");
        }
        if ((flags & NTLMSSP_KEY_128) != 0) {
            buffer.append(" NTLMSSP_KEY_128");
        }

        if ((flags & NTLMSSP_TARGET_INFO) != 0) {
            buffer.append(" NTLMSSP_TARGET_INFO");
        }

        if ((flags & NTLMSSP_NTLM2_KEY) != 0) {
            buffer.append(" NTLMSSP_NTLM2_KEY");
        }
        if ((flags & NTLMSSP_CHALL_NOT_NT) != 0) {
            buffer.append(" NTLMSSP_CHALL_NOT_NT");
        }
        if ((flags & NTLMSSP_CHALL_ACCEPT) != 0) {
            buffer.append(" NTLMSSP_CHALL_ACCEPT");
        }
        if ((flags & NTLMSSP_CHALL_INIT) != 0) {
            buffer.append(" NTLMSSP_CHALL_INIT");
        }
        if ((flags & NTLMSSP_ALWAYS_SIGN) != 0) {
            buffer.append(" NTLMSSP_ALWAYS_SIGN");
        }
        if ((flags & NTLMSSP_LOCAL_CALL) != 0) {
            buffer.append(" NTLMSSP_LOCAL_CALL");
        }
        if ((flags & NTLMSSP_WORKSTATION) != 0) {
            buffer.append(" NTLMSSP_WORKSTATION");
        }
        if ((flags & NTLMSSP_DOMAIN) != 0) {
            buffer.append(" NTLMSSP_DOMAIN");
        }

        if ((flags & NTLMSSP_NTLM_KEY) != 0) {
            buffer.append(" NTLMSSP_NTLM_KEY");
        }
        if ((flags & NTLMSSP_NETWARE) != 0) {
            buffer.append(" NTLMSSP_NETWARE");
        }
        if ((flags & NTLMSSP_LM_KEY) != 0) {
            buffer.append(" NTLMSSP_LM_KEY");
        }
        if ((flags & NTLMSSP_DATAGRAM) != 0) {
            buffer.append(" NTLMSSP_DATAGRAM");
        }
        if ((flags & NTLMSSP_SEAL) != 0) {
            buffer.append(" NTLMSSP_SEAL");
        }
        if ((flags & NTLMSSP_SIGN) != 0) {
            buffer.append(" NTLMSSP_SIGN");
        }

        if ((flags & NTLMSSP_TARGET) != 0) {
            buffer.append(" NTLMSSP_TARGET");
        }
        if ((flags & NTLMSSP_OEM) != 0) {
            buffer.append(" NTLMSSP_OEM");
        }
        if ((flags & NTLMSSP_UNICODE) != 0) {
            buffer.append(" NTLMSSP_UNICODE");
        }
        return buffer.toString();
    }

    public String getOSVersion(byte[] data, int offset) {
        return " os version: " + data[offset] + '.' + data[offset + 1] + ' ' + getShort(data, offset + 2);
    }


    public String toHexString(byte[] data) {
        StringBuilder buffer = new StringBuilder();
        for (byte b : data) {
            if ((b & 0xF0) == 0) {
                buffer.append('0');
            }
            buffer.append(Integer.toHexString(b & 0xFF));
        }
        return buffer.toString();
    }

}
