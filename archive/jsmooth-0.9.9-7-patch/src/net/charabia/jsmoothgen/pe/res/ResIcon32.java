package net.charabia.jsmoothgen.pe.res;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 32 bits res icon with alpha channel
 */
public class ResIcon32 extends ResIcon {
    BufferedImage image;

    /**
     * Creates a new instance based on the data of the Image argument.
     *
     * @param img image
     * @throws Exception on error
     */
    public ResIcon32(Image img) throws Exception {
        int width = img.getWidth(null);
        int height = img.getHeight(null);

        image = (BufferedImage) img;

        this.BitsPerPixel = 32;

        // header size
        this.Size = 40;
        this.Width = width;
        this.Height = height * 2;
        this.Planes = 1;
        this.Compression = 0;

        this.SizeOfBitmap = 0;
        this.HorzResolution = 0;
        this.VertResolution = 0;

        this.ColorsUsed = 0;
        this.ColorsImportant = 0;
    }

    /**
     * Creates and returns a ByteBuffer containing an image under
     * the .ico format expected by Windows.
     *
     * @return a ByteBuffer with the .ico data
     */
    public ByteBuffer getData() {
        // transparency mask rows must be double bytes aligned
        int rowsize = (int) Width / 8;
        int padding = 0;
        if ((rowsize % 4) > 0) {
            padding = (int) ((4 - Width / 8 % 4) * 8);
            rowsize += 4 - (rowsize % 4);
        }
        // transparency line padding size
        // create transparency mask buffer
        int transparencyBytesSize = (int) (rowsize * (Height / 2));
        int[] transparencyMask = new int[transparencyBytesSize];

        // allocate header + pixel count * bytes / pixel + transparency mask size
        ByteBuffer buf = ByteBuffer.allocate((int) (40 + (Width * ((Height) / 2) * BitsPerPixel/8) + transparencyBytesSize));

        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.position(0);

        // write header
        buf.putInt((int) Size);
        buf.putInt((int) Width);
        buf.putInt((int) Height);
        buf.putShort((short) Planes);
        buf.putShort((short) BitsPerPixel);
        buf.putInt((int) Compression);
        buf.putInt((int) SizeOfBitmap);
        buf.putInt((int) HorzResolution);
        buf.putInt((int) VertResolution);
        buf.putInt((int) ColorsUsed);
        buf.putInt((int) ColorsImportant);

        int pixelCount = 0;
        for (int y = (int) ((Height / 2) - 1); y >= 0; y--) {
            for (int x = 0; x < Width; x++) {
                int pix = image.getRGB(x, y);
                //System.out.println(x+" "+y+":"+Integer.toHexString((pix >> 24) & 0xff));
                boolean isTransparent = ((pix >> 24) & 0xff) == 0;
                if (isTransparent) {
                    int index = pixelCount / 8;
                    byte bitindex = (byte) (pixelCount % 8);
                    transparencyMask[index] |= 0x80 >> bitindex;
                }
                buf.putInt(pix);
                pixelCount++;
            }
            // skip to next transparency line
            pixelCount += padding;
        }

        // transparency mask
        for (int x = 0; x < transparencyBytesSize; x++) {
            buf.put((byte) (transparencyMask[x] & 0xff));
        }

        buf.position(0);
        return buf;
    }

    public String toString() {
        StringBuffer out = new StringBuffer();

        out.append("Size: ").append(Size);
        out.append("\nWidth: ").append(Width);
        out.append("\nHeight: ").append(Height);
        out.append("\nPlanes: ").append(Planes);
        out.append("\nBitsPerPixel: ").append(BitsPerPixel);
        out.append("\nCompression: ").append(Compression);
        out.append("\nSizeOfBitmap: ").append(SizeOfBitmap);
        out.append("\nHorzResolution: ").append(HorzResolution);
        out.append("\nVertResolution: ").append(VertResolution);
        out.append("\nColorsUsed: ").append(ColorsUsed);
        out.append("\nColorsImportant: ").append(ColorsImportant);

        return out.toString();
    }

}
