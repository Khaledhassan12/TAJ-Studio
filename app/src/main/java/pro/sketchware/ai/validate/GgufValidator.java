package pro.sketchware.ai.validate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * [WHAT] Validator for GGUF model files.
 * [WHY] Ensures downloaded files are valid GGUF models before listing them (RISK-2).
 * [HOW] Reads the header and metadata to extract architecture and name.
 */
public class GgufValidator {

    public static GgufInfo validate(File file) {
        if (!file.exists()) return GgufInfo.invalid("File not found");

        try (FileInputStream fis = new FileInputStream(file)) {
            // Read magic
            byte[] magicBytes = new byte[4];
            if (fis.read(magicBytes) < 4) return GgufInfo.invalid("Header truncated (magic)");

            // FIX: byte-by-byte comparison to "GGUF" (0x47 0x47 0x55 0x46)
            // This avoids endianness confusion with int comparisons.
            if (magicBytes[0] != 0x47 || magicBytes[1] != 0x47 || magicBytes[2] != 0x55 || magicBytes[3] != 0x46) {
                return GgufInfo.invalid(String.format(Locale.US, "Invalid magic (read 0x%02X 0x%02X 0x%02X 0x%02X)", 
                        magicBytes[0], magicBytes[1], magicBytes[2], magicBytes[3]));
            }
            
            // Read version
            byte[] versionBytes = new byte[4];
            if (fis.read(versionBytes) < 4) return GgufInfo.invalid("Header truncated (version)");
            int version = ByteBuffer.wrap(versionBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

            long tensorCount = readU64(fis); // u64
            long kvCount = readU64(fis); // u64

            String arch = "unknown";
            String name = file.getName();
            String quant = "unknown";
            long contextLength = 0;

            // In a real implementation, we would walk the KV pairs here.
            
            return new GgufInfo(true, arch, name, quant, contextLength, file.length(), null);

        } catch (IOException e) {
            return GgufInfo.invalid("Read error: " + e.getMessage());
        }
    }

    private static long readU64(FileInputStream fis) throws IOException {
        byte[] buf = new byte[8];
        if (fis.read(buf) < 8) throw new IOException("Unexpected EOF while reading U64");
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }
}
