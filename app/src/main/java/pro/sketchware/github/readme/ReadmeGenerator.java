package pro.sketchware.github.readme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import a.a.a.wq;
import pro.sketchware.github.GitHubManager;

/**
 * مولّد README الاحترافي (Upload Studio) — يبني ملف Markdown غنياً بالبيانات الحقيقية.
 * Professional README Generator — builds a rich Markdown file using real project data.
 * 
 * [AR] هذا الكلاس مسؤول عن توليد ملف README.md احترافي وشامل بناءً على بيانات المشروع الحقيقية.
 * [EN] This class is responsible for generating a professional and comprehensive README.md file based on real project data.
 */
public class ReadmeGenerator {

    public static class ReadmeResult {
        public String markdown;
        public List<File> banners;

        public ReadmeResult(String markdown, List<File> banners) {
            this.markdown = markdown;
            this.banners = banners;
        }
    }

    public static ReadmeResult generate(Context context, String projectTitle, String projectId, 
                                       String rootPath, String userLogin, String userAvatar, 
                                       String license, List<File> attachments) {
        
        StringBuilder sb = new StringBuilder();
        List<File> banners = new ArrayList<>();

        // 1. هيدر احترافي بـ Badges
        sb.append("<p align=\"center\">\n");
        sb.append("  <img src=\"docs/app-icon.png\" width=\"128\" height=\"128\">\n");
        sb.append("</p>\n\n");
        
        sb.append("<h1 align=\"center\">").append(projectTitle).append("</h1>\n\n");
        
        sb.append("<p align=\"center\">\n");
        sb.append("  <img src=\"https://img.shields.io/badge/Language-Java-orange?style=for-the-badge&logo=java\">\n");
        sb.append("  <img src=\"https://img.shields.io/badge/Platform-Android-green?style=for-the-badge&logo=android\">\n");
        sb.append("  <img src=\"https://img.shields.io/badge/License-").append(license.replace("-", "--")).append("-blue?style=for-the-badge\">\n");
        sb.append("  <img src=\"https://img.shields.io/badge/Developed%20with-Sketchware%20Pro-teal?style=for-the-badge\">\n");
        sb.append("</p>\n\n");

        sb.append("A professional Android project developed using **TAJ Studio** (Sketchware Pro based).\n\n");

        // 2. Screenshots / Banners
        sb.append("## 📱 Screenshots\n\n");
        sb.append("<p align=\"center\">\n");
        
        boolean screenshotsFound = false;
        if (attachments != null) {
            for (File file : attachments) {
                if (GitHubManager.isBinaryByExtension(file.getName()) && !file.getName().toLowerCase().endsWith(".apk")) {
                    sb.append("  <img src=\"attachments/").append(file.getName()).append("\" width=\"250\">\n");
                    screenshotsFound = true;
                }
            }
        }

        if (!screenshotsFound) {
            // توليد بانرات تمثيلية إن لم توجد صور
            File banner = generateBanner(context, projectTitle, projectId);
            if (banner != null) {
                banners.add(banner);
                sb.append("  <img src=\"screenshots/banner.png\" width=\"600\">\n");
            }
        }
        sb.append("</p>\n\n");

        // 3. Features
        sb.append("## ✨ Features\n\n");
        List<String> features = detectFeatures(new File(rootPath));
        if (features.isEmpty()) {
            sb.append("_No specific features detected yet._\n");
        } else {
            for (String f : features) {
                sb.append("- ").append(f).append("\n");
            }
        }
        sb.append("\n");

        // 4. Tech Stack
        sb.append("## 🛠 Tech Stack\n\n");
        sb.append("- **Language**: Java (Sketchware-generated)\n");
        sb.append("- **UI**: XML-based Material Design\n");
        sb.append("- **Development Environment**: TAJ Studio / Sketchware Pro\n");
        sb.append("\n");

        // 5. Project Structure
        sb.append("## 📂 Project Structure\n\n");
        sb.append("```text\n");
        sb.append(buildTree(new File(rootPath), 0, 2));
        sb.append("```\n\n");

        // 6. Installation
        sb.append("## 🚀 Installation\n\n");
        sb.append("1. Clone this repository or download the ZIP.\n");
        sb.append("2. Open **TAJ Studio** or **Sketchware Pro** on your Android device.\n");
        sb.append("3. Use the **Restore/Import** feature and select the project folder.\n");
        sb.append("4. Click **Run** to build and install the APK.\n\n");

        // 7. APK Download
        sb.append("## 📦 APK Download\n\n");
        sb.append("> [!NOTE]\n");
        sb.append("> No APK released yet — check the [Releases](https://github.com/").append(userLogin).append("/").append(projectTitle.replaceAll("[^a-zA-Z0-9._-]", "-")).append("/releases) section later.\n\n");

        // 8. Testing & TODO
        sb.append("## 🧪 Testing\n\n");
        sb.append("- Manual on-device testing.\n\n");

        sb.append("## 📝 TODO\n\n");
        sb.append("- [ ] Add more features.\n");
        sb.append("- [ ] Improve UI/UX.\n");
        sb.append("- [ ] Optimize performance.\n\n");

        // 9. Contributing & License
        sb.append("## 🤝 Contributing\n\n");
        sb.append("Contributions, issues, and feature requests are welcome! Feel free to check the [issues page].\n\n");

        sb.append("## 📜 License\n\n");
        sb.append("This project is licensed under the **").append(license).append("** License.\n\n");

        // 10. Author & Support
        sb.append("---\n\n");
        sb.append("### 👤 Author\n\n");
        if (userAvatar != null) {
            sb.append("<img src=\"").append(userAvatar).append("\" width=\"48\" height=\"48\" style=\"border-radius:50%\">\n\n");
        }
        sb.append("**").append(userLogin).append("**\n");
        sb.append("- GitHub: [@").append(userLogin).append("](https://github.com/").append(userLogin).append(")\n\n");

        sb.append("### ⭐️ Support\n\n");
        sb.append("Give a ⭐️ if this project helped you!\n\n");

        sb.append("<p align=\"center\">\n");
        sb.append("  <i>Developed with ❤️ by ").append(userLogin).append(" using TAJ Studio</i>\n");
        sb.append("</p>\n");

        return new ReadmeResult(sb.toString(), banners);
    }

    private static List<String> detectFeatures(File root) {
        List<String> features = new ArrayList<>();
        List<File> javaFiles = new ArrayList<>();
        findFilesByExtension(root, ".java", javaFiles);
        
        boolean hasNet = false, hasDb = false, hasPrefs = false, hasCam = false, hasMedia = false;
        
        for (File f : javaFiles) {
            String content = readFileHeader(f);
            if (content.contains("HttpURLConnection") || content.contains("OkHttpClient")) hasNet = true;
            if (content.contains("SQLiteOpenHelper") || content.contains("Room")) hasDb = true;
            if (content.contains("SharedPreferences")) hasPrefs = true;
            if (content.contains("Camera") || content.contains("CameraX")) hasCam = true;
            if (content.contains("MediaPlayer") || content.contains("ExoPlayer")) hasMedia = true;
        }

        if (hasNet) features.add("🌐 Networking capabilities");
        if (hasDb) features.add("💾 Local database storage (SQLite)");
        if (hasPrefs) features.add("⚙️ Offline settings & preferences");
        if (hasCam) features.add("📸 Camera integration");
        if (hasMedia) features.add("🎵 Media playback support");
        
        File resDir = new File(root, "src/main/res");
        if (new File(resDir, "values-night").exists() || new File(resDir, "values/themes.xml").exists()) {
            features.add("🌓 Dark/Light mode support");
        }
        
        features.add("📱 Modern Material Design UI");
        
        return features;
    }

    private static String buildTree(File dir, int level, int maxLevel) {
        if (level > maxLevel) return "";
        StringBuilder sb = new StringBuilder();
        File[] children = dir.listFiles();
        if (children == null) return "";
        
        for (File child : children) {
            String name = child.getName();
            if (name.startsWith(".") || name.equals("build") || name.equals("bin") || name.equals("gen")) continue;
            
            for (int i = 0; i < level; i++) sb.append("  ");
            sb.append(child.isDirectory() ? "📁 " : "📄 ").append(name).append("\n");
            
            if (child.isDirectory()) {
                sb.append(buildTree(child, level + 1, maxLevel));
            }
        }
        return sb.toString();
    }

    private static File generateBanner(Context context, String title, String id) {
        try {
            int width = 1200;
            int height = 630;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            int colorStart = Color.parseColor("#E0F2F1");
            int colorEnd = Color.parseColor("#B2DFDB");
            Paint bgPaint = new Paint();
            bgPaint.setShader(new android.graphics.LinearGradient(0, 0, width, height, colorStart, colorEnd, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, bgPaint);

            Paint decorPaint = new Paint();
            decorPaint.setColor(Color.WHITE);
            decorPaint.setAlpha(40);
            canvas.drawCircle(width * 0.9f, height * 0.2f, 200, decorPaint);
            canvas.drawCircle(width * 0.1f, height * 0.8f, 150, decorPaint);

            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.parseColor("#004D40"));
            textPaint.setTextSize(80);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(title, width / 2f, height / 2f, textPaint);

            textPaint.setTextSize(30);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            textPaint.setAlpha(180);
            canvas.drawText("Project ID: " + id + " • Built with TAJ Studio", width / 2f, height / 2f + 80, textPaint);

            File iconFile = new File(wq.e() + File.separator + id, "icon.png");
            if (iconFile.exists()) {
                Bitmap icon = android.graphics.BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                if (icon != null) {
                    Rect dst = new Rect(width / 2 - 64, height / 2 - 250, width / 2 + 64, height / 2 - 122);
                    canvas.drawBitmap(icon, null, dst, null);
                }
            }

            File out = new File(context.getCacheDir(), "banner_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static void findFilesByExtension(File dir, String ext, List<File> result) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                String name = child.getName();
                if (name.equals("src") || name.equals("main") || name.equals("java")) {
                    findFilesByExtension(child, ext, result);
                } else if (result.size() < 20) {
                     findFilesByExtension(child, ext, result);
                }
            } else if (child.getName().toLowerCase().endsWith(ext)) {
                result.add(child);
            }
        }
    }

    private static String readFileHeader(File file) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] buf = new byte[2048];
            int len = fis.read(buf);
            if (len > 0) return new String(buf, 0, len, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        return "";
    }
}
