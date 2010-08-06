/*
 * DavMail POP/IMAP/SMTP/CalDav/LDAP Exchange Gateway
 * Copyright (C) 2009  Mickael Guessant
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
package davmail.util;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Various string handling methods
 */
public final class StringUtil {
    private StringUtil() {
    }

    /**
     * Return the sub string between startDelimiter and endDelimiter or null.
     *
     * @param value          String value
     * @param startDelimiter start delimiter
     * @param endDelimiter   end delimiter
     * @return token value
     */
    public static String getToken(String value, String startDelimiter, String endDelimiter) {
        String token = null;
        if (value != null) {
            int startIndex = value.indexOf(startDelimiter);
            if (startIndex >= 0) {
                startIndex += startDelimiter.length();
                int endIndex = value.indexOf(endDelimiter, startIndex);
                if (endIndex >= 0) {
                    token = value.substring(startIndex, endIndex);
                }
            }
        }
        return token;
    }

    /**
     * Return the sub string between startDelimiter and endDelimiter or null,
     * look for last token in string.
     *
     * @param value          String value
     * @param startDelimiter start delimiter
     * @param endDelimiter   end delimiter
     * @return token value
     */
    public static String getLastToken(String value, String startDelimiter, String endDelimiter) {
        String token = null;
        if (value != null) {
            int startIndex = value.lastIndexOf(startDelimiter);
            if (startIndex >= 0) {
                startIndex += startDelimiter.length();
                int endIndex = value.indexOf(endDelimiter, startIndex);
                if (endIndex >= 0) {
                    token = value.substring(startIndex, endIndex);
                }
            }
        }
        return token;
    }

    /**
     * Return the sub string between startDelimiter and endDelimiter with newToken.
     *
     * @param value          String value
     * @param startDelimiter start delimiter
     * @param endDelimiter   end delimiter
     * @param newToken       new token value
     * @return token value
     */
    public static String replaceToken(String value, String startDelimiter, String endDelimiter, String newToken) {
        String result = null;
        if (value != null) {
            int startIndex = value.indexOf(startDelimiter);
            if (startIndex >= 0) {
                startIndex += startDelimiter.length();
                int endIndex = value.indexOf(endDelimiter, startIndex);
                if (endIndex >= 0) {
                    result = value.substring(0, startIndex) + newToken + value.substring(endIndex);
                }
            }
        }
        return result;
    }

    /**
     * Join values with given separator.
     *
     * @param values    value set
     * @param separator separator
     * @return joined values
     */
    public static String join(Set<String> values, String separator) {
        if (values != null && !values.isEmpty()) {
            StringBuilder result = new StringBuilder();
            for (String value : values) {
                if (result.length() > 0) {
                    result.append(separator);
                }
                result.append(value);
            }
            return result.toString();
        } else {
            return null;
        }
    }

    private static final Pattern AMP_PATTERN = Pattern.compile("&");
    private static final Pattern LT_PATTERN = Pattern.compile("<");
    private static final Pattern GT_PATTERN = Pattern.compile(">");

    private static final Pattern URLENCODED_AMP_PATTERN = Pattern.compile("%26");

    private static final Pattern ENCODED_AMP_PATTERN = Pattern.compile("&amp;");
    private static final Pattern ENCODED_LT_PATTERN = Pattern.compile("&lt;");
    private static final Pattern ENCODED_GT_PATTERN = Pattern.compile("&gt;");

    private static final Pattern F8FF_PATTERN = Pattern.compile("_xF8FF_");
    private static final Pattern PLUS_PATTERN = Pattern.compile("\\+");
    private static final Pattern SLASH_PATTERN = Pattern.compile("/");
    private static final Pattern UNDERSCORE_PATTERN = Pattern.compile("_");
    private static final Pattern DASH_PATTERN = Pattern.compile("-");

    /**
     * Encode & to %26 for urlcompname.
     *
     * @param name decoded name
     * @return name encoded name
     */
    public static String urlEncodeAmpersand(String name) {
        String result = name;
        if (name.indexOf('&') >= 0) {
            result = AMP_PATTERN.matcher(result).replaceAll("%26");
        }
        return result;
    }

    /**
     * Decode %26 to & for urlcompname.
     *
     * @param name decoded name
     * @return name encoded name
     */
    public static String urlDecodeAmpersand(String name) {
        String result = name;
        if (name != null && name.indexOf("%26") >= 0) {
            result = URLENCODED_AMP_PATTERN.matcher(result).replaceAll("&");
        }
        return result;
    }

    /**
     * Need to encode xml for iCal.
     *
     * @param name decoded name
     * @return name encoded name
     */
    public static String xmlEncode(String name) {
        String result = name;
        if (name.indexOf('&') >= 0) {
            result = AMP_PATTERN.matcher(result).replaceAll("&amp;");
        }
        if (name.indexOf('<') >= 0) {
            result = LT_PATTERN.matcher(result).replaceAll("&lt;");
        }
        if (name.indexOf('>') >= 0) {
            result = GT_PATTERN.matcher(result).replaceAll("&gt;");
        }
        return result;
    }

    /**
     * Need to decode xml for iCal
     *
     * @param name encoded name
     * @return name decoded name
     */
    public static String xmlDecode(String name) {
        String result = name;
        if (name.indexOf("&amp;") >= 0) {
            result = ENCODED_AMP_PATTERN.matcher(result).replaceAll("&");
        }
        if (name.indexOf("&lt;") >= 0) {
            result = ENCODED_LT_PATTERN.matcher(result).replaceAll("<");
        }
        if (name.indexOf("&gt;") >= 0) {
            result = ENCODED_GT_PATTERN.matcher(result).replaceAll(">");
        }
        return result;
    }

    /**
     * Convert base64 value to hex.
     *
     * @param value base64 value
     * @return hex value
     */
    public static String base64ToHex(String value) {
        String hexValue = null;
        if (value != null) {
            hexValue = new String(Hex.encodeHex(Base64.decodeBase64(value.getBytes())));
        }
        return hexValue;
    }

    /**
     * Convert hex value to base64.
     *
     * @param value hex value
     * @return base64 value
     * @throws DecoderException on error
     */
    public static String hexToBase64(String value) throws DecoderException {
        String base64Value = null;
        if (value != null) {
            base64Value = new String(Base64.encodeBase64(Hex.decodeHex(value.toCharArray())));
        }
        return base64Value;
    }

    /**
     * Encode item name to get actual value stored in urlcompname MAPI property.
     *
     * @param value decoded value
     * @return urlcompname encoded value
     */
    public static String encodeUrlcompname(String value) {
        String result = value;
        if (result.indexOf("_xF8FF_") >= 0) {
            result = F8FF_PATTERN.matcher(result).replaceAll(String.valueOf((char) 0xF8FF));
        }
        if (result.indexOf('&') >= 0) {
            result = AMP_PATTERN.matcher(result).replaceAll("%26");
        }
        if (result.indexOf('+') >= 0) {
            result = PLUS_PATTERN.matcher(result).replaceAll("%2B");
        }
        return result;
    }

    /**
     * Urlencode plus sign in encoded href.
     * '+' is decoded as ' ' by URIUtil.decode, the workaround is to force urlencoding to '%2B' first
     *
     * @param value encoded href
     * @return encoded href
     */
    public static String encodePlusSign(String value) {
        String result = value;
        if (result.indexOf('+') >= 0) {
            result = PLUS_PATTERN.matcher(result).replaceAll("%2B");
        }
        return result;
    }

    public static String base64ToUrl(String value) {
        String result = value;
        if (value != null) {
            if (result.indexOf('+') >= 0) {
                result = PLUS_PATTERN.matcher(result).replaceAll("-");
            }
            if (result.indexOf('/') >= 0) {
                result = SLASH_PATTERN.matcher(result).replaceAll("_");
            }
        }
        return result;
    }

    public static String urlToBase64(String value) {
        String result = value;
        if (result.indexOf('-') >= 0) {
            result = DASH_PATTERN.matcher(result).replaceAll("+");
        }
        if (result.indexOf('_') >= 0) {
            result = UNDERSCORE_PATTERN.matcher(result).replaceAll("/");
        }
        return result;
    }
}
