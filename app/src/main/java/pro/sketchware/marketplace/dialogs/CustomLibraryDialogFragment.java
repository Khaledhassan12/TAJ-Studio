package pro.sketchware.marketplace.dialogs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pro.sketchware.R;
import pro.sketchware.databinding.SheetCustomLibraryBinding;
import pro.sketchware.marketplace.services.LibraryInstallService;
import pro.sketchware.marketplace.utils.CoordinateExtractor;
import pro.sketchware.utility.SketchwareUtil;

/**
 * دايالوج مخصص لإضافة مكتبات مخصصة - يدعم الاستخراج الذكي للصيغ المتعددة.
 * Custom library dialog - supports smart extraction of multiple formats.
 *
 * WHAT: Multi-format coordinate extraction and catalog version fallback.
 * HOW: Using CoordinateExtractor to parse messy input and resolve versions from curators.
 * WHY: Improves UX by accepting code snippets, URLs, and incomplete coordinates.
 */
public class CustomLibraryDialogFragment extends BottomSheetDialogFragment {

    private SheetCustomLibraryBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetCustomLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        if (view.getParent() instanceof View) {
            ((View) view.getParent()).setBackgroundResource(R.drawable.shape_rounded_bottom_sheet);
        }

        binding.btnCancel.setOnClickListener(v -> dismiss());
        
        binding.btnMoreInfo.setOnClickListener(v -> {
            LibraryInfoBottomSheet infoSheet = new LibraryInfoBottomSheet();
            infoSheet.show(getChildFragmentManager(), "LibraryInfo");
        });

        binding.btnInstallCustom.setOnClickListener(v -> onInstallClicked());
    }

    private void onInstallClicked() {
        String inputText = binding.etCoordinate.getText().toString().trim();

        // multiFormatCoordinateExtractor: Handle Gradle, purl, GitHub, and Repo URLs.
        List<CoordinateExtractor.Extracted> found = CoordinateExtractor.extract(inputText);
        
        // catalogVersionResolver: Try to fill missing/variable versions using the marketplace catalog.
        CoordinateExtractor.resolveVersionsFromCatalog(found);

        if (found.isEmpty()) {
            showNoCoordinateDialog();
            return;
        }

        List<CoordinateExtractor.Extracted> complete = new ArrayList<>();
        List<CoordinateExtractor.Extracted> needVer = new ArrayList<>();
        for (CoordinateExtractor.Extracted e : found) {
            if (CoordinateExtractor.needsResolution(e.version)) {
                needVer.add(e);
            } else {
                complete.add(e);
            }
        }

        if (needVer.isEmpty()) {
            install(complete);
        } else {
            // missingVersionPrompt: Ask user for versions that couldn't be resolved.
            showEnterVersionsDialog(needVer, complete);
        }
    }

    private void showNoCoordinateDialog() {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lib_extract_none_title)
            .setMessage(R.string.lib_extract_none_body)
            .setPositiveButton("OK", null)
            .setNeutralButton(R.string.lib_custom_copy_example, (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("maven_coordinate", "com.github.bumptech.glide:glide:4.16.0");
                clipboard.setPrimaryClip(clip);
                SketchwareUtil.toast("Example copied to clipboard");
            })
            .show();
    }

    private void showEnterVersionsDialog(List<CoordinateExtractor.Extracted> needVer, List<CoordinateExtractor.Extracted> complete) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 0);

        Map<CoordinateExtractor.Extracted, TextInputEditText> fields = new HashMap<>();

        for (CoordinateExtractor.Extracted e : needVer) {
            TextView label = new TextView(requireContext());
            String displayRaw = e.raw.length() > 40 ? e.raw.substring(0, 37) + "..." : e.raw;
            label.setText(e.groupArtifact() + "\n(From: " + displayRaw + ")");
            label.setTextSize(13);
            label.setPadding(0, 20, 0, 8);
            layout.addView(label);

            TextInputLayout til = new TextInputLayout(requireContext());
            til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
            til.setBoxCornerRadii(12, 12, 12, 12);
            TextInputEditText diet = new TextInputEditText(til.getContext());
            diet.setHint("version, e.g. 1.0.0");
            diet.setTextSize(14);
            til.addView(diet);
            layout.addView(til);
            fields.put(e, diet);
        }

        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lib_extract_versions_title)
            .setMessage(R.string.lib_extract_versions_body)
            .setView(layout)
            .setPositiveButton(R.string.lib_install, (dialog, which) -> {
                boolean allFilled = true;
                for (Map.Entry<CoordinateExtractor.Extracted, TextInputEditText> entry : fields.entrySet()) {
                    String v = entry.getValue().getText().toString().trim();
                    if (v.isEmpty()) {
                        allFilled = false;
                        SketchwareUtil.toast("Please fill all versions");
                        break;
                    }
                    entry.getKey().version = v;
                }
                if (allFilled) {
                    List<CoordinateExtractor.Extracted> all = new ArrayList<>(complete);
                    all.addAll(needVer);
                    install(all);
                }
            })
            .setNegativeButton(R.string.common_word_cancel, null)
            .show();
    }

    private void install(List<CoordinateExtractor.Extracted> list) {
        List<String> coords = new ArrayList<>();
        for (CoordinateExtractor.Extracted e : list) {
            coords.add(e.group + ":" + e.artifact + ":" + e.version);
        }
        startInstallService(coords);
    }

    private void startInstallService(List<String> coordinates) {
        Intent intent = new Intent(requireContext(), LibraryInstallService.class);
        intent.setAction(LibraryInstallService.ACTION_INSTALL);
        intent.putStringArrayListExtra(LibraryInstallService.EXTRA_COORDINATES, new ArrayList<>(coordinates));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent);
        } else {
            requireContext().startService(intent);
        }
        
        dismissAllowingStateLoss();
        SketchwareUtil.toast(getString(R.string.lib_install_check_notif));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
