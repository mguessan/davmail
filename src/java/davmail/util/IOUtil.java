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
     * Resize image bytes to a max width or height image size.
     *
     * @param inputBytes input image bytes
     * @param max        max size
     * @return scaled image bytes
     * @throws IOException on error
     */
    public static byte[] resizeImage(byte[] inputBytes, int max) throws IOException {
        BufferedImage inputImage = ImageIO.read(new ByteArrayInputStream(inputBytes));
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
