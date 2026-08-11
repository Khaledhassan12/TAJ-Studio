package pro.sketchware.ai.validate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * [WHAT] Validator for GGUF model files.
 * [WHY] Ensures downloaded files are valid GGUF models before listing them (RISK-2)
 * and exposes metadata (architecture) for the embedding import gate (P2-CS2, RISK-20).
 * [HOW] Reads the header, then walks the metadata KV pairs to extract
 * general.architecture (pure Java, no native deps).
 */
public class GgufValidator {

    // GGUF metadata value types (ggml gguf spec v2/v3).
    private static final int GGUF_TYPE_UINT8 = 0;
    private static final int GGUF_TYPE_INT8 = 1;
    private static final int GGUF_TYPE_UINT16 = 2;
    private static final int GGUF_TYPE_INT16 = 3;
    private static final int GGUF_TYPE_UINT32 = 4;
    private static final int GGUF_TYPE_INT32 = 5;
    private static final int GGUF_TYPE_FLOAT32 = 6;
    private static final int GGUF_TYPE_BOOL = 7;
    private static final int GGUF_TYPE_STRING = 8;
    private static final int GGUF_TYPE_ARRAY = 9;
    private static final int GGUF_TYPE_UINT64 = 10;
    private static final int GGUF_TYPE_INT64 = 11;
    private static final int GGUF_TYPE_FLOAT64 = 12;

    /** Known GENERATIVE (chat) architectures — rejected as embedding models (RISK-20). */
    private static final String[] GENERATIVE_ARCHS = {
            "llama", "qwen", "phi", "gemma", "mistral", "mixtral", "gpt2", "falcon",
            "mpt", "starcoder", "deepseek", "granite", "chatglm", "command-r",
            "internlm", "minicpm", "bloom", "baichuan", "olmo", "gptneox", "rwkv",
            "stablelm", "orion", "persimmon", "jais"
    };

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

            // P2-CS2: walk the KV pairs to find general.architecture and
            // general.name. Bounded walk — never more than kvCount entries and
            // bail out cleanly on EOF or unreasonable counts.
            if (kvCount >= 0 && kvCount <= 10_000) {
                for (long i = 0; i < kvCount; i++) {
                    String key = readString(fis);
                    int type = readU32(fis);
                    if ("general.architecture".equals(key) && type == GGUF_TYPE_STRING) {
                        arch = readString(fis);
                    } else if ("general.name".equals(key) && type == GGUF_TYPE_STRING) {
                        name = readString(fis);
                    } else {
                        skipValue(fis, type);
                    }
                }
            }

            return new GgufInfo(true, arch, name, quant, contextLength, file.length(), null);

        } catch (IOException e) {
            return GgufInfo.invalid("Read error: " + e.getMessage());
        } catch (Exception e) {
            return GgufInfo.invalid("Malformed GGUF metadata: " + e.getMessage());
        }
    }

    /**
     * RISK-20 gate: true IFF the architecture is a known generative (chat)
     * arch, meaning the GGUF cannot serve as an embedding model.
     */
    public static boolean isLikelyGenerativeArch(String arch) {
        if (arch == null || arch.isEmpty() || "unknown".equals(arch)) return false;
        String lower = arch.toLowerCase(Locale.US);
        for (String generative : GENERATIVE_ARCHS) {
            if (lower.equals(generative) || lower.startsWith(generative + "-") || lower.startsWith(generative + "_")) {
                return true;
            }
        }
        return false;
    }

    private static String readString(FileInputStream fis) throws IOException {
        long len = readU64(fis);
        if (len < 0 || len > 10 * 1024 * 1024) throw new IOException("String length out of bounds");
        byte[] buf = new byte[(int) len];
        if (len > 0 && fis.read(buf) < len) throw new IOException("Unexpected EOF while reading string");
        return new String(buf, "UTF-8");
    }

    private static int readU32(FileInputStream fis) throws IOException {
        byte[] buf = new byte[4];
        if (fis.read(buf) < 4) throw new IOException("Unexpected EOF while reading U32");
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static long readU64(FileInputStream fis) throws IOException {
        byte[] buf = new byte[8];
        if (fis.read(buf) < 8) throw new IOException("Unexpected EOF while reading U64");
        return ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    private static void skipBytes(FileInputStream fis, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = fis.skip(remaining);
            if (skipped <= 0) {
                if (fis.read() < 0) throw new IOException("Unexpected EOF while skipping");
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }

    private static void skipValue(FileInputStream fis, int type) throws IOException {
        switch (type) {
            case GGUF_TYPE_UINT8:
            case GGUF_TYPE_INT8:
            case GGUF_TYPE_BOOL:
                skipBytes(fis, 1);
                break;
            case GGUF_TYPE_UINT16:
            case GGUF_TYPE_INT16:
                skipBytes(fis, 2);
                break;
            case GGUF_TYPE_UINT32:
            case GGUF_TYPE_INT32:
            case GGUF_TYPE_FLOAT32:
                skipBytes(fis, 4);
                break;
            case GGUF_TYPE_UINT64:
            case GGUF_TYPE_INT64:
            case GGUF_TYPE_FLOAT64:
                skipBytes(fis, 8);
                break;
            case GGUF_TYPE_STRING:
                readString(fis);
                break;
            case GGUF_TYPE_ARRAY: {
                int elemType = readU32(fis);
                long count = readU64(fis);
                if (count < 0 || count > 100_000_000L) throw new IOException("Array length out of bounds");
                for (long i = 0; i < count; i++) {
                    skipValue(fis, elemType);
                }
                break;
            }
            default:
                throw new IOException("Unknown GGUF value type: " + type);
        }
    }
}
