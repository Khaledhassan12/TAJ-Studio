package pro.sketchware.marketplace.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import pro.sketchware.R;
import pro.sketchware.databinding.SheetLibraryInfoBinding;

/**
 * بوتم شيت معلومات المافن - شرح مفصل للمطور عن طريقة عمل الإحداثيات.
 * Maven info sheet - detailed explanation for developers on how coordinates work.
 */
public class LibraryInfoBottomSheet extends BottomSheetDialogFragment {

    private SheetLibraryInfoBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetLibraryInfoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        if (view.getParent() instanceof View) {
            ((View) view.getParent()).setBackgroundResource(R.drawable.shape_rounded_bottom_sheet);
        }

        binding.btnOk.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
