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

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.net.URLCodec;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.http.Consts;

import java.io.IOException;
import java.util.BitSet;

/**
 * Implement encode/decode logic to replace HttpClient 3 URIUtil
 */
public class URIUtil {

    /**
     * The percent "%" character always has the reserved purpose of being the
     * escape indicator, it must be escaped as "%25" in order to be used as
     * data within a URI.
     */
    protected static final BitSet percent = new BitSet(256);
    // Static initializer for percent
    static {
        percent.set('%');
    }


    /**
     * BitSet for digit.
     * <p><blockquote><pre>
     * digit    = "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" |
     *            "8" | "9"
     * </pre></blockquote><p>
     */
    protected static final BitSet digit = new BitSet(256);
    // Static initializer for digit
    static {
        for (int i = '0'; i <= '9'; i++) {
            digit.set(i);
        }
    }


    /**
     * BitSet for alpha.
     * <p><blockquote><pre>
     * alpha         = lowalpha | upalpha
     * </pre></blockquote><p>
     */
    protected static final BitSet alpha = new BitSet(256);
    // Static initializer for alpha
    static {
        for (int i = 'a'; i <= 'z'; i++) {
            alpha.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            alpha.set(i);
        }
    }


    /**
     * BitSet for alphanum (join of alpha &amp; digit).
     * <p><blockquote><pre>
     *  alphanum      = alpha | digit
     * </pre></blockquote><p>
     */
    protected static final BitSet alphanum = new BitSet(256);
    // Static initializer for alphanum
    static {
        alphanum.or(alpha);
        alphanum.or(digit);
    }


    /**
     * BitSet for hex.
     * <p><blockquote><pre>
     * hex           = digit | "A" | "B" | "C" | "D" | "E" | "F" |
     *                         "a" | "b" | "c" | "d" | "e" | "f"
     * </pre></blockquote><p>
     */
    protected static final BitSet hex = new BitSet(256);
    // Static initializer for hex
    static {
        hex.or(digit);
        for (int i = 'a'; i <= 'f'; i++) {
            hex.set(i);
        }
        for (int i = 'A'; i <= 'F'; i++) {
            hex.set(i);
        }
    }


    /**
     * BitSet for escaped.
     * <p><blockquote><pre>
     * escaped       = "%" hex hex
     * </pre></blockquote><p>
     */
    protected static final BitSet escaped = new BitSet(256);
    // Static initializer for escaped
    static {
        escaped.or(percent);
        escaped.or(hex);
    }


    /**
     * BitSet for mark.
     * <p><blockquote><pre>
     * mark          = "-" | "_" | "." | "!" | "~" | "*" | "'" |
     *                 "(" | ")"
     * </pre></blockquote><p>
     */
    protected static final BitSet mark = new BitSet(256);
    // Static initializer for mark
    static {
        mark.set('-');
        mark.set('_');
        mark.set('.');
        mark.set('!');
        mark.set('~');
        mark.set('*');
        mark.set('\'');
        mark.set('(');
        mark.set(')');
    }


    /**
     * Data characters that are allowed in a URI but do not have a reserved
     * purpose are called unreserved.
     * <p><blockquote><pre>
     * unreserved    = alphanum | mark
     * </pre></blockquote><p>
     */
    protected static final BitSet unreserved = new BitSet(256);
    // Static initializer for unreserved
    static {
        unreserved.or(alphanum);
        unreserved.or(mark);
    }


    /**
     * BitSet for reserved.
     * <p><blockquote><pre>
     * reserved      = ";" | "/" | "?" | ":" | "@" | "&amp;" | "=" | "+" |
     *                 "$" | ","
     * </pre></blockquote><p>
     */
    protected static final BitSet reserved = new BitSet(256);
    // Static initializer for reserved
    static {
        reserved.set(';');
        reserved.set('/');
        reserved.set('?');
        reserved.set(':');
        reserved.set('@');
        reserved.set('&');
        reserved.set('=');
        reserved.set('+');
        reserved.set('$');
        reserved.set(',');
    }


    /**
     * BitSet for uric.
     * <p><blockquote><pre>
     * uric          = reserved | unreserved | escaped
     * </pre></blockquote><p>
     */
    protected static final BitSet uric = new BitSet(256);
    // Static initializer for uric
    static {
        uric.or(reserved);
        uric.or(unreserved);
        uric.or(escaped);
    }

    /**
     * BitSet for pchar.
     * <p><blockquote><pre>
     * pchar         = unreserved | escaped |
     *                 ":" | "@" | "&amp;" | "=" | "+" | "$" | ","
     * </pre></blockquote><p>
     */
    protected static final BitSet pchar = new BitSet(256);
    // Static initializer for pchar
    static {
        pchar.or(unreserved);
        pchar.or(escaped);
        pchar.set(':');
        pchar.set('@');
        pchar.set('&');
        pchar.set('=');
        pchar.set('+');
        pchar.set('$');
        pchar.set(',');
    }


    /**
     * BitSet for param (alias for pchar).
     * <p><blockquote><pre>
     * param         = *pchar
     * </pre></blockquote><p>
     */
    protected static final BitSet param = pchar;


    /**
     * BitSet for segment.
     * <p><blockquote><pre>
     * segment       = *pchar *( ";" param )
     * </pre></blockquote><p>
     */
    protected static final BitSet segment = new BitSet(256);
    // Static initializer for segment
    static {
        segment.or(pchar);
        segment.set(';');
        segment.or(param);
    }


    /**
     * BitSet for path segments.
     * <p><blockquote><pre>
     * path_segments = segment *( "/" segment )
     * </pre></blockquote><p>
     */
    protected static final BitSet path_segments = new BitSet(256);
    // Static initializer for path_segments
    static {
        path_segments.set('/');
        path_segments.or(segment);
    }

    /**
     * URI absolute path.
     * <p><blockquote><pre>
     * abs_path      = "/"  path_segments
     * </pre></blockquote><p>
     */
    protected static final BitSet abs_path = new BitSet(256);
    // Static initializer for abs_path
    static {
        abs_path.set('/');
        abs_path.or(path_segments);
    }

    /**
     * Those characters that are allowed for the abs_path.
     */
    public static final BitSet allowed_abs_path = new BitSet(256);
    static {
        allowed_abs_path.or(abs_path);
        // allowed_abs_path.set('/');  // aleady included
        allowed_abs_path.andNot(percent);
        allowed_abs_path.clear('+');
    }

    /**
     * Decode url encoded string.
     * @param escaped encoded string
     * @return decoded string
     * @throws IOException on error
     */
    public static String decode(String escaped) throws IOException {
        try {
            return getString(URLCodec.decodeUrl(getAsciiBytes(escaped)));
        } catch (DecoderException e) {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * Encode url path.
     * @param unescaped unencoded path
     * @return escaped path
     */
    public static String encodePath(String unescaped) {
        return encode(unescaped, allowed_abs_path);
    }

    /**
     * URL encode string.
     * @param unescaped unencoded string
     * @param allowed allowed characters bitset
     * @return encoded string
     */
    public static String encode(String unescaped, BitSet allowed) {
        return getAsciiString(URLCodec.encodeUrl(allowed, getBytes(unescaped)));
    }

    /**
     * URL encode query string.
     * @param unescaped unencoded query string
     * @return encoded string query string
     */
    public static String encodeWithinQuery(String unescaped) {
        return encode(unescaped, URI.allowed_within_query);
    }

    /**
     * URL encode path and query string.
     * @param unescaped unencoded path and query string
     * @return encoded string path and query string
     */
    public static String encodePathQuery(String unescaped){
        int at = unescaped.indexOf('?');
        if (at < 0) {
            return encode(unescaped, URI.allowed_abs_path);
        } else {
            return encode(unescaped.substring(0, at), URI.allowed_abs_path)
                    + '?' + encode(unescaped.substring(at + 1), URI.allowed_query);
        }
    }

    public static byte[] getBytes(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("Parameter may not be null");
        }

        return value.getBytes(Consts.UTF_8);
    }

    public static byte[] getAsciiBytes(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("Parameter may not be null");
        }

        return value.getBytes(Consts.ASCII);
    }

    /**
     * Convert byte array to an ASCII string value.
     * @param bytes byte array
     * @return ASCII string
     */
    public static String getAsciiString(final byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Parameter may not be null");
        }

        return new String(bytes, Consts.ASCII);
    }

    /**
     * Convert byte array to a UTF-8 string value.
     * @param bytes byte array
     * @return ASCII string
     */
    public static String getString(final byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Parameter may not be null");
        }

        return new String(bytes, Consts.UTF_8);
    }
}
