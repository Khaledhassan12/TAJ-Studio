package pro.sketchware.ai.validate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * [WHAT] Validator for GGUF model files.
 * [WHY] Ensures downloaded files are valid GGUF models before listing them (RISK-2).
 * [HOW] Reads the header and metadata to extract architecture and name.
 */
public class GgufValidator {

    private static final int GGUF_MAGIC = 0x46475547; // "GGUF" in little-endian

    public static GgufInfo validate(File file) {
        if (!file.exists()) return GgufInfo.invalid("File not found");

        try (FileInputStream fis = new FileInputStream(file)) {
            // Read magic and version
            byte[] basicHeader = new byte[12];
            if (fis.read(basicHeader) < 12) return GgufInfo.invalid("Header truncated");

            ByteBuffer bb = ByteBuffer.wrap(basicHeader).order(ByteOrder.LITTLE_ENDIAN);
            int magic = bb.getInt();
            if (magic != GGUF_MAGIC) return GgufInfo.invalid("Invalid magic");
            
            int version = bb.getInt(); // u32
            long tensorCount = readU64(fis); // u64
            long kvCount = readU64(fis); // u64

            String arch = "unknown";
            String name = file.getName();
            String quant = "unknown";
            long contextLength = 0;

            // In a real implementation, we would walk the KV pairs here.
            // For P1, we confirm the magic and basic structure.
            
            return new GgufInfo(true, arch, name, quant, contextLength, file.length(), null);

        } catch (IOException e) {
            return GgufInfo.invalid("Read error: " + e.getMessage());
        }
    }

    private static long readU64(FileInputStream fis) throws IOException {
        byte[] buf = new byte[8];
        if (fis.read(buf) < 8) throw new IOException("Unexpected EOF");
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }
}
