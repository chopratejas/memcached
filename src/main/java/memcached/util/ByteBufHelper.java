package memcached.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import static memcached.command.CommandParser.CRLF;

/**
 * Helper singleton class for ByteBufs
 */
public class ByteBufHelper {

  private static ByteBufHelper byteBufHelper;

  private ByteBufHelper() {
  }

  public static ByteBufHelper getInstance() {
    if (byteBufHelper == null) {
      byteBufHelper = new ByteBufHelper();
    }
    return byteBufHelper;
  }

  /***
   * Check if the input buffer is CRLF only. i.e. only contains '\r\n'
   * @param input is the input bytebuf
   * @return true if the input buffer is CRLF only; false otherwise.
   */
  public boolean isCRLFOnly(ByteBuf input) {
    return input.equals(Unpooled.copiedBuffer(CRLF));
  }

  /***
   * This method is used to tokenize the input stream based on the separator. It will read the bytes
   * until the separator, skip the bytes after it, and return the token read.
   * @param in is the input buffer
   * @param separator is the separator byte
   * @param skipBytesCount are the number of bytes skipped after finding the separator
   * @return token read before the separator from input stream, and move the readerIndex of input stream
   */
  public ByteBuf tokenize(ByteBuf in, Byte separator, int skipBytesCount) {
    int len = in.bytesBefore(separator);
    if (len < 0) {
      return null;
    }
    ByteBuf token = in.readBytes(len);
    in.skipBytes(skipBytesCount);
    return token;
  }

  /***
   * This method returns the number of bytes in the input, which are *before*
   * the first instance of CRLF line ending (i.e. number of bytes before '\r\n')
   * @param input is the input buffer
   * @return number of bytes before CRLF; if no CRLF found, return -1
   */
  public int bytesBeforeCRLF(ByteBuf input) {
    ByteBuf buf = input.copy();
    int rIndex = buf.bytesBefore((byte) '\r');
    int nIndex = buf.bytesBefore((byte) '\n');
    int size = 0;
    if (rIndex < 0 || nIndex < 0) {
      // The input buffer contains no CRLF, return -1.
      return -1;
    }

    // Input could contain multiple \r and \n bytes. Find the one where '\r\n' occur together
    while (nIndex != (rIndex + 1)) {
      buf.readBytes(rIndex + 1);
      size += (rIndex + 1);
      rIndex = buf.bytesBefore((byte) '\r');
      nIndex = buf.bytesBefore((byte) '\n');
      if (rIndex < 0) {
        // No more '\r' found
        break;
      }
    }
    // If we've reached here without '\r\n', then return -1
    if (rIndex < 0) {
      return -1;
    }
    return size + rIndex;
  }

  /***
   * This function splits the incoming buffer into multiple CRLF ended buffers.
   * @param input is the input buffer.
   * @return a list of multiple CRLF ended buffers.
   */
  public ArrayList<ByteBuf> extractCrlfSplitBufs(ByteBuf input) {
    ByteBuf buffer = input.copy();
    ArrayList<ByteBuf> lines = new ArrayList<>();
    while (true) {
      // Obtain the (next) CRLF ended line
      int size = bytesBeforeCRLF(buffer);
      if (size < 0 || buffer.readableBytes() <= 0) {
        // No more CRLF ended buffer left
        break;
      }

      // Note that size covers line *without* CRLF ending. In order to move readerIndex
      // to process the remaining buffer, we read (size + CRLF.length).
      lines.add(buffer.readBytes(size + CRLF.length).copy());
    }
    return lines;
  }
}
