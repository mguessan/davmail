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
package davmail.util;

import org.apache.commons.codec.binary.Base64;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

/**
 * Input output functions.
 */
public final class IOUtil {
    private IOUtil() {
    }

    /**
     * Write all inputstream content to outputstream.
     *
     * @param inputStream  input stream
     * @param outputStream output stream
     * @throws IOException on error
     */
    public static void write(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] bytes = new byte[8192];
        int length;
        while ((length = inputStream.read(bytes)) > 0) {
            outputStream.write(bytes, 0, length);
        }
    }


    /**
     * Decode base64 input string, return byte array.
     *
     * @param encoded Base64 encoded string
     * @return decoded content as byte arrey
     * @throws IOException on error
     */
    public static byte[] decodeBase64(String encoded) throws IOException {
        return Base64.decodeBase64(encoded.getBytes("ASCII"));
    }

    /**
     * Decode base64 input string, return content as UTF-8 String.
     *
     * @param encoded Base64 encoded string
     * @return decoded content as byte arrey
     * @throws IOException on error
     */
    public static String decodeBase64AsString(String encoded) throws IOException {
        return new String(decodeBase64(encoded), "UTF-8");
    }

    /**
     * Base64 encode value.
     *
     * @param value input value
     * @return base64  value
     * @throws IOException on error
     */
    public static String encodeBase64AsString(String value) throws IOException {
        return new String(Base64.encodeBase64(value.getBytes("UTF-8")), "ASCII");
    }

    /**
     * Base64 encode value.
     *
     * @param value input value
     * @return base64  value
     * @throws IOException on error
     */
    public static String encodeBase64AsString(byte[] value) throws IOException {
        return new String(Base64.encodeBase64(value), "ASCII");
    }

    /**
     * Base64 encode value.
     *
     * @param value input value
     * @return base64  value
     * @throws IOException on error
     */
    public static byte[] encodeBase64(String value) throws IOException {
        return Base64.encodeBase64(value.getBytes("UTF-8"));
    }

    /**
     * Base64 encode value.
     *
     * @param value input value
     * @return base64  value
     * @throws IOException on error
     */
    public static byte[] encodeBase64(byte[] value) throws IOException {
        return Base64.encodeBase64(value);
    }

    /**
     * Resize image bytes to a max width or height image size.
     *
     * @param inputBytes input image bytes
     * @param max        max size
     * @return scaled image bytes
     * @throws IOException on error
     */
    public static byte[] resizeImage(byte[] inputBytes, int max) throws IOException {
        BufferedImage inputImage = ImageIO.read(new ByteArrayInputStream(inputBytes));
        if (inputImage == null) {
            throw new IOException("Unable to decode image data");
        }
        BufferedImage outputImage = resizeImage(inputImage, max);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(outputImage, "jpg", baos);
        return baos.toByteArray();
    }

    /**
     * Resize image to a max width or height image size.
     *
     * @param inputImage input image
     * @param max        max size
     * @return scaled image
     */
    public static BufferedImage resizeImage(BufferedImage inputImage, int max) {
        int width = inputImage.getWidth();
        int height = inputImage.getHeight();
        int targetWidth;
        int targetHeight;
        if (width <= max && height <= max) {
            return inputImage;
        } else if (width > height) {
            targetWidth = max;
            targetHeight = targetWidth * height / width;
        } else {
            targetHeight = max;
            targetWidth = targetHeight * width / height;
        }
        Image scaledImage = inputImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
        BufferedImage targetImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        targetImage.getGraphics().drawImage(scaledImage, 0, 0, null);
        return targetImage;
    }

    /**
     * Read all inputStream content to a byte array.
     *
     * @param inputStream input stream
     * @return content as byte array
     * @throws IOException on error
     */
    public static byte[] readFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] bytes = new byte[8192];
        int length;
        while ((length = inputStream.read(bytes)) > 0) {
            baos.write(bytes, 0, length);
        }
        return baos.toByteArray();
    }

}
