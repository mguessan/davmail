package davmail.exchange;

// Imports

import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Encodes the input data using the BASE64 transformation as specified in
 * <A HREF="http://www.faqs.org/rfcs/rfc2045.html">RFC 2045</A>, section
 * 6.8, and outputs the encoded data to the underlying
 * <code>OutputStream</code>.
 *
 * @author David A. Herman
 * @version 1.0 of September 2000
 * @see java.io.FilterOutputStream
 */
public class BASE64EncoderStream extends FilterOutputStream {

    /**
     * Useful constant representing the default maximum number of output
     * characters per line (76).
     */
    public static final int LINE_LENGTH = 76;

    /**
     * The BASE64 alphabet.
     */
    private static final byte[] alphabet;

    /**
     * Fills the BASE64 alphabet table with the ASCII byte values of
     * the characters.
     **/
    static {
        try {
            alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".getBytes("US-ASCII");
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException("ASCII character encoding not supported.");
        }
    }

    /**
     * The internal buffer of encoded output bytes.
     */
    private final byte[] output = new byte[4];

    /**
     * The internal buffer of input bytes to be encoded.
     */
    private byte[] input = new byte[3];

    /**
     * The index of the next position in the internal buffer of input bytes
     * at which to store input.
     */
    private int inputIndex = 0;

    /**
     * The number of characters that have been output on the current line.
     */
    private int chars = 0;

    /**
     * The maximum number of characters to output per line.
     */
    private final int maxLineLength;

    /**
     * The index into the BASE64 alphabet to generate the next encoded
     * character of output data. This index is generated as input data comes
     * in, sometimes requiring more than one byte of input before it is
     * completely calculated, so it is shared in the object.
     */
    private int index;

    /**
     * Builds a BASE64 encoding stream on top of the given underlying output
     * stream, with the default maximum number of characters per line.
     *
     * @param out output stream
     */
    public BASE64EncoderStream(OutputStream out) {
        this(out, LINE_LENGTH);
    }

    /**
     * Builds a BASE64 encoding stream on top of the given underlying output
     * stream, with the specified maximum number of characters per line. For
     * For every <code>max</code> characters that are output to the
     * underlying stream, a CRLF sequence (<code>'\r'</code>,
     * <code>'\n'</code>) is written.
     *
     * @param out the underlying output stream.
     * @param max the maximum number of output bytes per line.
     */
    public BASE64EncoderStream(OutputStream out, int max) {
        super(out);
        maxLineLength = max;
    }

    public void flush() throws IOException {
        pad();
        out.flush();
    }

    /**
     * Completes the encoding of data, padding the input data if necessary
     * to end the input on a multiple of 4 bytes, writes a terminating
     * CRLF sequence (<code>'\r'</code>, <code>'\n'</code>) to the
     * underlying output stream, and closes the underlying output stream.
     *
     * @throws IOException if an I/O error occurs.
     */
    public void close() throws IOException {
        try {
            flush();
        } catch (IOException ignored) {
            // ignore
        }

        // Add a terminating CRLF sequence.
        out.write('\r');
        out.write('\n');

        // Close the underlying output stream.
        out.close();
    }

    /**
     * Encodes the given byte array, to be written to the underlying output
     * stream.
     *
     * @param b the byte array to be encoded.
     * @throws IOException if an I/O error occurs.
     */
    public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Encodes <code>len</code> bytes from the given byte array starting
     * at offset <code>off</code>, to be written to the underlying output
     * stream.
     *
     * @param b   the byte array to be encoded.
     * @param off the offset at which to start reading from the byte array.
     * @param len the number of bytes to read.
     * @throws IOException if an I/O error occurs.
     */
    public void write(byte b[], int off, int len) throws IOException {
        for (int i = 0; i < len; i++) {
            write(b[off + i]);
        }
    }

    /**
     * Encodes the 8 low-order bits of the given integer, to be written to
     * the underlying output stream. The 24 high-order bits are discarded.
     * If the internal buffer of encoded data is filled upon appending the
     * encoded data to it, the buffer is written to the underlying output
     * stream.
     *
     * @param b the integer whose low-order byte is to be encoded.
     * @throws IOException if an I/O error occurs.
     */
    public void write(int b) throws IOException {
        switch (inputIndex) {
            case 0:
                // The first output character generates its
                // index from the first six bits of the first byte.
                //
                // Input:     XXXXXXoo oooooooo oooooooo
                // Mask:      11111100                   &
                //            ----------------------------
                // Output: 00 XXXXXX

                input[0] = (byte) (b & 0xFF);
                index = ((input[0] & 0xFC) >> 2);
                output[0] = alphabet[index];

                // Pre-calculate the first two bits of the
                // second output character. If this turns out
                // to be the last byte of input, then it will
                // already be padded with zeroes, and the rest
                // can be padded with '=' characters.
                index = ((input[0] & 0x03) << 4);

                break;

            case 1:
                // The second output character generates its
                // index from the last two bits of the first
                // byte and the first four bits of the second.
                //
                // Input:  ooooooXX YYYYoooo oooooooo
                // Mask:   00000011 11110000          &
                //         ----------------------------
                // Output:    00 XX YYYY

                input[1] = (byte) (b & 0xFF);

                // The first two bits of the second output character
                // have already been calculated and stored in the
                // member variable 'index'. Add the last four bits
                // to the index and generate the output character.
                index += ((input[1] & 0xF0) >> 4);
                output[1] = alphabet[index];

                // Pre-calculate the first four bits of the
                // third output character. If this turns out
                // to be the last byte of input, then it will
                // already be padded with zeroes, and the rest
                // can be padded with '=' characters.
                index = ((input[1] & 0x0F) << 2);

                break;

            case 2:
                // The third output character generates its
                // index from the last four bits of the second
                // byte and the first two bits of the third.
                //
                // Input:  oooooooo ooooXXXX YYoooooo
                // Mask:            00001111 11000000 &
                //         ----------------------------
                // Output:           00 XXXX YY

                input[2] = (byte) (b & 0xFF);

                // The first four bits of the third output character
                // have already been calculated and stored in the
                // member variable 'index'. Add the last two bits
                // to the index and generate the output character.
                index += ((input[2] & 0xC0) >> 6);
                output[2] = alphabet[index];

                // The fourth output character generates its
                // index from the last six bits of the third byte.
                //
                // Input:  oooooooo oooooooo ooXXXXXX
                // Mask:                     00111111 &
                //         ----------------------------
                // Output:                  00 XXXXXX

                index = (b & 0x3F);
                output[3] = alphabet[index];

                break;
        }

        inputIndex = ((inputIndex + 1) % 3);

        // If the internal buffer is filled, write its contents to the
        // underlying output stream.
        if (inputIndex == 0) {
            writeOutput();
        }
    }

    /**
     * Writes the internal buffer of encoded output bytes to the underlying
     * output stream. This method is called whenever the 4-byte internal
     * buffer is filled.
     *
     * @throws IOException if an I/O error occurs.
     */
    private void writeOutput() throws IOException {
        int newchars = (chars + 4) % maxLineLength;
        if (newchars == 0) {
            out.write(output);
            out.write('\r');
            out.write('\n');
        } else if (newchars < chars) {
            out.write(output, 0, 4 - newchars);
            out.write('\r');
            out.write('\n');
            out.write(output, 4 - newchars, newchars);
        } else
            out.write(output);
        chars = newchars;
    }

    /**
     * Pads the encoded data to a multiple of 4 bytes, if necessary. Since
     * BASE64 encodes every 3 bytes as 4 bytes of text, if the input is not
     * a multiple of 3, the end of the input data must be padded in order
     * to send a final quantum of 4 bytes. The BASE64 special character
     * <code>'='</code> is used for this purpose. See
     * <A HREF="http://www.faqs.org/rfcs/rfc2045.html">RFC 2045</A>, section
     * 6.8, for more information.
     *
     * @throws IOException if an I/O error occurs.
     */
    private void pad() throws IOException {
        // If the input index is 0, then we ended on a multiple of 3 bytes
        // of input, so no padding is necessary.
        if (inputIndex > 0) {
            // If the input index is 1, then the input text is equivalent
            // to 1 modulus 3 bytes, so two input bytes need to be padded.
            // We pad the final two output bytes as '=' characters.
            if (inputIndex == 1) {
                output[1] = alphabet[index];
                output[2] = alphabet[64];
                output[3] = alphabet[64];
            }
            // If the input index is 2, then the input text is equivalent
            // to 2 modulus 3 bytes, so one input byte needs to be padded.
            // We pad the final output byte as a '=' character.
            else if (inputIndex == 2) {
                output[2] = alphabet[index];
                output[3] = alphabet[64];
            }

            // This is unnecessary, but just for the sake of clarity.
            inputIndex = 0;

            writeOutput();
        }
    }

    public static byte[] encode(byte[] bytes) {

        // Note: This is a public method on Sun's implementation
        // and so it should be supported for compatibility.
        // Also this method is used by the "B" encoding for now.
        // This implementation usesthe encoding stream to
        // process the bytes.  Possibly, the BASE64 encoding
        // stream should use this method for it's encoding.

        // Variables
        ByteArrayOutputStream byteStream;
        BASE64EncoderStream encoder = null;

        // Create Streams
        byteStream = new ByteArrayOutputStream();

        try {
            encoder = new BASE64EncoderStream(byteStream);

            // Write Bytes
            encoder.write(bytes);
            encoder.flush();

        } catch (IOException e) {
            // ignore
        } finally {
            try {
                if (encoder != null) {
                    encoder.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }

        // Return Encoded Byte Array
        return byteStream.toByteArray();

    } // encode()

}