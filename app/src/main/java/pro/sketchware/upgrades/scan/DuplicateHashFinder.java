package pro.sketchware.upgrades.scan;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.upgrades.model.Finding;

/**
 * WHAT: DuplicateHashFinder - Identifies exact duplicate files using MD5 hashing.
 * WHY: File names might differ, but content parity causes project bloat; hashing is the only source of truth.
 * (عربي) مكتشف الهاش المكرر - يحدد الملفات المتطابقة تماماً باستخدام تشفير MD5.
 * قد تختلف أسماء الملفات، لكن تماثل المحتوى يسبب تضخماً في المشروع؛ التشفير هو المصدر الوحيد للحقيقة.
 */
public class DuplicateHashFinder {

    public List<Finding> scan(List<String> resDirs, List<String> assetsDirs) {
        Map<String, List<String>> hashMap = new HashMap<>();
        List<Finding> findings = new ArrayList<>();
        
        for (String resPath : resDirs) scanDir(new File(resPath), hashMap);
        for (String assetsPath : assetsDirs) scanDir(new File(assetsPath), hashMap);

        for (Map.Entry<String, List<String>> entry : hashMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                findings.add(new Finding(
                    "duplicate_" + entry.getKey().substring(0, 8),
                    Finding.Category.DUPLICATE_FILE,
                    "Duplicate Files Found",
                    "Multiple files have the exact same content",
                    entry.getValue(),
                    true,
                    "Keep one instance and delete duplicates"
                ));
            }
        }
        return findings;
    }

    private void scanDir(File dir, Map<String, List<String>> hashMap) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDir(file, hashMap);
            } else if (file.length() > 0) {
                String hash = getFileHash(file);
                if (hash != null) {
                    if (!hashMap.containsKey(hash)) hashMap.put(hash, new ArrayList<>());
                    hashMap.get(hash).add(file.getAbsolutePath());
                }
            }
        }
    }

    private String getFileHash(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
