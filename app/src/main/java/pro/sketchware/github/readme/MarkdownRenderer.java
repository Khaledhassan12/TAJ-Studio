package pro.sketchware.github.readme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.R;

/**
 * مصيّر Markdown مخصص (renderedReadmePreview) — يحول نص Markdown إلى عناصر واجهة M3.
 * Custom Markdown Renderer — converts Markdown text into M3 UI elements.
 * 
 * [AR] هذا الكلاس يحول كود Markdown إلى بلوكات مرئية (عناوين، نصوص، صور، روابط) بأسلوب GitHub.
 * [EN] This class converts Markdown code into visual blocks (headers, text, images, links) GitHub-style.
 */
public class MarkdownRenderer {

    /**
     * [AR] الدالة الأساسية لتحويل Markdown إلى واجهة مستخدم، مع معالجة وسوم HTML.
     * [EN] Main function to convert Markdown into UI, with HTML tag processing.
     */
    public static void render(Context context, LinearLayout container, String markdown, java.util.List<File> localFiles) {
        container.removeAllViews();
        if (markdown == null || markdown.isEmpty()) return;

        // 1. Extract <img> tags first -> Visual blocks
        // [AR] استخراج وسوم الصور أولاً وتحويلها إلى بلوكات مرئية (محلي: Bitmap، شارات: Chips).
        // [EN] Extract img tags first and convert them into visual blocks (local: Bitmap, badges: Chips).
        Pattern imgPattern = Pattern.compile("<img\\s+[^>]*src=\"([^\"]+)\"[^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher imgMatcher = imgPattern.matcher(markdown);
        while (imgMatcher.find()) {
            renderImageLine(context, container, imgMatcher.group(0), localFiles);
        }
        markdown = imgMatcher.replaceAll("");

        // 2. <h1 align="center">X</h1> -> Centered Header block
        // [AR] تحويل وسوم العناوين الموسطة HTML إلى بلوكات عناوين مركّزة.
        // [EN] Convert HTML centered header tags into focused header blocks.
        Pattern h1CenterPattern = Pattern.compile("<h1\\s+align=\"center\">\\s*(.*?)\\s*</h1>", Pattern.CASE_INSENSITIVE);
        Matcher h1Matcher = h1CenterPattern.matcher(markdown);
        while (h1Matcher.find()) {
            addHeader(context, container, h1Matcher.group(1), 22, true, true);
        }
        markdown = h1Matcher.replaceAll("");

        // 3. <p align="center"> -> Centering state markers
        // [AR] معالجة وسم التوسيط للفقرات عبر علامات مؤقتة للتحكم في الحالة.
        // [EN] Process paragraph centering tags using temporary state markers.
        markdown = markdown.replaceAll("(?i)<p\\s+align=\"center\">", "\n[CENTER_ON]\n");
        markdown = markdown.replaceAll("(?i)</p>", "\n[CENTER_OFF]\n");

        // 4. Clear any remaining tags via regex <[^>]+>
        // [AR] مسح كافة وسوم HTML المتبقية لضمان عدم ظهور أي وسم خام في المعاينة.
        // [EN] Clear all remaining HTML tags to ensure no raw tags appear in the preview.
        markdown = markdown.replaceAll("<[^>]+>", "");

        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;
        StringBuilder codeContent = new StringBuilder();
        boolean isCentered = false;

        for (String line : lines) {
            String trimmedLine = line.trim();

            if (trimmedLine.equals("[CENTER_ON]")) {
                isCentered = true;
                continue;
            }
            if (trimmedLine.equals("[CENTER_OFF]")) {
                isCentered = false;
                continue;
            }

            // Code Blocks
            if (trimmedLine.startsWith("```")) {
                if (inCodeBlock) {
                    addCodeBlock(context, container, codeContent.toString());
                    codeContent.setLength(0);
                    inCodeBlock = false;
                } else {
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                codeContent.append(line).append("\n");
                continue;
            }

            // Headers
            if (trimmedLine.startsWith("# ")) {
                addHeader(context, container, trimmedLine.substring(2), 24, true, isCentered);
                continue;
            } else if (trimmedLine.startsWith("## ")) {
                addHeader(context, container, trimmedLine.substring(3), 20, true, isCentered);
                continue;
            } else if (trimmedLine.startsWith("### ")) {
                addHeader(context, container, trimmedLine.substring(4), 18, true, isCentered);
                continue;
            }

            // Horizontal Rule
            if (trimmedLine.equals("---")) {
                addDivider(context, container);
                continue;
            }

            // Markdown Images (legacy check if any left)
            if (trimmedLine.contains("![")) {
                renderImageLine(context, container, trimmedLine, localFiles);
                continue;
            }

            // List Items & Normal Text
            if (!trimmedLine.isEmpty()) {
                addTextLine(context, container, line, isCentered);
            } else {
                addSpacer(container, 8);
            }
        }
    }

    private static void addHeader(Context context, LinearLayout container, String text, int size, boolean bold, boolean center) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_on_surface));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        if (center) tv.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 24, 0, 8);
        container.addView(tv, lp);
    }

    /**
     * [AR] يعرض أسطر النصوص مع دعم الخط العريض والروابط والرموز النقطية والتوسيط.
     * [EN] Renders text lines with support for bold, links, bullet points, and centering.
     */
    private static void addTextLine(Context context, LinearLayout container, String line, boolean center) {
        TextView tv = new TextView(context);
        tv.setText(parseSpans(line));
        tv.setTextSize(14);
        tv.setLineSpacing(4, 1.1f);
        tv.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_on_surface_variant));
        if (center) tv.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 4, 0, 4);
        container.addView(tv, lp);
    }

    private static void addCodeBlock(Context context, LinearLayout container, String code) {
        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_light_surface_container_highest));
        card.setRadius(12 * context.getResources().getDisplayMetrics().density);
        card.setCardElevation(0);
        card.setStrokeWidth(0);

        TextView tv = new TextView(context);
        tv.setText(code.trim());
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(12);
        tv.setPadding(32, 32, 32, 32);
        tv.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_on_surface_variant));

        card.addView(tv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 16, 0, 16);
        container.addView(card, lp);
    }

    private static void addDivider(Context context, LinearLayout container) {
        View v = new View(context);
        v.setBackgroundColor(ContextCompat.getColor(context, R.color.md_theme_light_outline_variant));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int) (1 * context.getResources().getDisplayMetrics().density));
        lp.setMargins(0, 16, 0, 16);
        container.addView(v, lp);
    }

    private static void addSpacer(LinearLayout container, int dp) {
        View v = new View(container.getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int) (dp * container.getContext().getResources().getDisplayMetrics().density));
        container.addView(v, lp);
    }

    /**
     * [AR] يعالج أسطر الصور؛ يعرض الصور المحلية كـ Bitmaps، ويحول الشارات الخارجية (Shields) إلى Chips بأسلوب Tonal.
     * [EN] Handles image lines; renders local images as Bitmaps, and converts external badges (Shields) into Tonal Chips.
     */
    private static void renderImageLine(Context context, LinearLayout container, String line, java.util.List<File> localFiles) {
        // Simple regex for src="..." or ![] (path)
        Pattern p = Pattern.compile("(?:src|\\()\"?([^\"]+\\.(?:png|jpg|jpeg|gif|svg))\"?\\)?");
        Matcher m = p.matcher(line);

        while (m.find()) {
            String path = m.group(1);
            if (path == null) continue;

            if (path.startsWith("http")) {
                // External Badge (shields.io)
                if (path.contains("shields.io")) {
                    addBadgeChip(context, container, path);
                } else {
                    addTextLine(context, container, "🖼 " + path, true); // Center by default for images
                }
            } else {
                // Local File (banner or attachment)
                File found = null;
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                
                if (localFiles != null) {
                    for (File f : localFiles) {
                        if (f.getName().equals(fileName) || (fileName.equals("banner.png") && f.getName().startsWith("banner_"))) {
                            found = f;
                            break;
                        }
                    }
                }

                if (found != null && found.exists()) {
                    addImageBlock(context, container, found);
                } else {
                    addTextLine(context, container, "🖼 " + path, true);
                }
            }
        }
    }

    private static void addBadgeChip(Context context, LinearLayout container, String url) {
        // Extract info from Shields.io URL: .../badge/Label-Value-Color
        String label = "Badge";
        try {
            String sub = url.substring(url.indexOf("/badge/") + 7);
            if (sub.contains("?")) sub = sub.substring(0, sub.indexOf('?'));
            String[] parts = sub.split("-");
            if (parts.length >= 2) {
                label = parts[0].replace("%20", " ") + ": " + parts[1].replace("%20", " ");
            }
        } catch (Exception ignored) {}

        Chip chip = new Chip(context);
        chip.setText(label);
        chip.setChipMinHeight(24 * context.getResources().getDisplayMetrics().density);
        chip.setClickable(false);
        chip.setTextSize(11); // Professional small text
        chip.setChipBackgroundColorResource(R.color.md_theme_light_primary_container);
        chip.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_on_primary_container));
        chip.setChipStrokeWidth(0);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.setMargins(0, 4, 0, 4);
        container.addView(chip, lp);
    }

    private static void addImageBlock(Context context, LinearLayout container, File file) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(16 * context.getResources().getDisplayMetrics().density);
        card.setCardElevation(0);
        card.setStrokeWidth(0);

        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setAdjustViewBounds(true);
        
        // Load with downsampling
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 2;
        Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        if (bmp != null) {
            iv.setImageBitmap(bmp);
        }

        card.addView(iv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (250 * context.getResources().getDisplayMetrics().density), -2);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.setMargins(0, 16, 0, 16);
        container.addView(card, lp);
    }

    private static CharSequence parseSpans(String text) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(text);

        // Bold: **text**
        Pattern boldPattern = Pattern.compile("\\*\\*(.*?)\\*\\*");
        Matcher boldMatcher = boldPattern.matcher(ssb);
        int offset = 0;
        while (boldMatcher.find()) {
            int start = boldMatcher.start() - offset;
            int end = boldMatcher.end() - offset;
            ssb.replace(start, end, boldMatcher.group(1));
            ssb.setSpan(new StyleSpan(Typeface.BOLD), start, start + boldMatcher.group(1).length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            offset += 4;
        }

        // Links: [text](url)
        Pattern linkPattern = Pattern.compile("\\[(.*?)\\]\\((.*?)\\)");
        Matcher linkMatcher = linkPattern.matcher(ssb);
        offset = 0;
        while (linkMatcher.find()) {
            int start = linkMatcher.start() - offset;
            int end = linkMatcher.end() - offset;
            String linkText = linkMatcher.group(1);
            String url = linkMatcher.group(2);
            ssb.replace(start, end, linkText);
            ssb.setSpan(new URLSpan(url), start, start + linkText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            offset += (end - start - linkText.length());
        }

        // Bullet: "- " -> "• "
        if (text.trim().startsWith("- ")) {
            ssb.replace(0, text.indexOf("- ") + 2, "• ");
        }

        return ssb;
    }
}
