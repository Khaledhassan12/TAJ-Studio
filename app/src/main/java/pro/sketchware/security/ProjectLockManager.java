package pro.sketchware.security;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import pro.sketchware.R;
import pro.sketchware.databinding.DialogProjectPinBinding;

public class ProjectLockManager {
    public static final String KEY_LOCKED = "project_locked";
    private static final String LOCK_FILE_NAME = "project_lock.json";

    private static final Set<String> unlockedSessions = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> attemptCounter = new ConcurrentHashMap<>();
    private static final Map<String, Long> cooldownTimers = new ConcurrentHashMap<>();

    private static final int MAX_ATTEMPTS = 5;
    private static final long COOLDOWN_MS = 30000;

    private static class LockData {
        int v = 1;
        String salt;
        String hash;
    }

    public static boolean isLocked(String scId) {
        File lockFile = getLockFile(scId);
        if (lockFile.exists()) return true;

        HashMap<String, Object> metadata = lC.b(scId);
        return metadata != null && yB.a(metadata, KEY_LOCKED, false);
    }

    public static boolean isSessionUnlocked(String scId) {
        return unlockedSessions.contains(scId);
    }

    public static void unlockSession(String scId) {
        unlockedSessions.add(scId);
    }

    public static boolean setLock(String scId, String pin) {
        if (pin == null || !pin.matches("^[ -~]{4,12}$")) return false;

        String salt = generateSalt();
        String hash = hashPin(pin, salt);

        LockData data = new LockData();
        data.salt = salt;
        data.hash = hash;

        boolean written = false;
        try {
            File lockFile = getLockFile(scId);
            if (!lockFile.getParentFile().exists()) lockFile.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(lockFile)) {
                fos.write(new Gson().toJson(data).getBytes(StandardCharsets.UTF_8));
            }
            written = lockFile.exists();
        } catch (Exception e) {
            Log.e("PINLOCK", "Write failed", e);
        }

        // Set metadata hint (travels if whitelisted, otherwise file is primary)
        HashMap<String, Object> metadata = lC.b(scId);
        if (metadata != null) {
            metadata.put(KEY_LOCKED, true);
            lC.b(scId, metadata);
        }

        Log.d("PINLOCK", "setLock scId=" + scId + " written=" + written);
        if (written) unlockSession(scId);
        return written;
    }

    public static void removeLock(String scId) {
        File lockFile = getLockFile(scId);
        if (lockFile.exists()) lockFile.delete();

        HashMap<String, Object> metadata = lC.b(scId);
        if (metadata != null) {
            metadata.remove(KEY_LOCKED);
            lC.b(scId, metadata);
        }

        unlockedSessions.remove(scId);
        attemptCounter.remove(scId);
        cooldownTimers.remove(scId);
        Log.d("PINLOCK", "removeLock scId=" + scId);
    }

    public static boolean verify(String scId, String pin) {
        LockData data = loadLockData(scId);
        if (data == null) return false;

        String inputHash = hashPin(pin, data.salt);
        boolean ok = MessageDigest.isEqual(hexToBytes(inputHash), hexToBytes(data.hash));
        Log.d("PINLOCK", "verify scId=" + scId + " result=" + ok);
        return ok;
    }

    private static File getLockFile(String scId) {
        return new File(wq.b(scId), LOCK_FILE_NAME);
    }

    private static LockData loadLockData(String scId) {
        File lockFile = getLockFile(scId);
        if (!lockFile.exists()) return null;
        try (FileInputStream fis = new FileInputStream(lockFile)) {
            byte[] bytes = new byte[(int) lockFile.length()];
            fis.read(bytes);
            return new Gson().fromJson(new String(bytes, StandardCharsets.UTF_8), LockData.class);
        } catch (Exception e) {
            Log.e("PINLOCK", "Read failed", e);
            return null;
        }
    }

    public interface OnSuccessListener {
        void onSuccess();
    }

    public static void requirePin(Activity activity, String scId, OnSuccessListener listener) {
        Log.d("PINLOCK", "requirePin gate scId=" + scId);
        if (!isLocked(scId) || isSessionUnlocked(scId)) {
            listener.onSuccess();
            return;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setCancelable(true);
        DialogProjectPinBinding binding = DialogProjectPinBinding.inflate(LayoutInflater.from(activity));
        builder.setView(binding.getRoot());

        binding.tvDialogTitle.setText(R.string.unlock_project);
        binding.tvDialogSubtitle.setText(R.string.pin_unlock_subtitle);
        binding.pinInputLayout.setHelperText(null);

        builder.setPositiveButton(R.string.unlock_button, null);
        builder.setNegativeButton(R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();

        Button unlockBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Handler handler = new Handler(Looper.getMainLooper());

        Runnable updateUI = new Runnable() {
            @Override
            public void run() {
                long cooldown = getRemainingCooldown(scId);
                if (cooldown > 0) {
                    unlockBtn.setEnabled(false);
                    binding.tvStatus.setText(activity.getString(R.string.pin_cooldown, (cooldown / 1000) + 1));
                    handler.postDelayed(this, 1000);
                } else {
                    int remaining = getRemainingAttempts(scId);
                    binding.tvStatus.setText(activity.getString(R.string.pin_attempts_remaining, remaining));
                    String input = binding.pinInput.getText().toString();
                    unlockBtn.setEnabled(input.matches("^[ -~]{4,12}$"));
                }
            }
        };

        binding.pinInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String input = s.toString();
                boolean ascii = isAsciiOnly(input);
                binding.tvLangError.setVisibility(ascii ? View.GONE : View.VISIBLE);
                if (!ascii) binding.pinInputLayout.setError(activity.getString(R.string.pin_lang_error_short));
                else binding.pinInputLayout.setError(null);
                updateUI.run();
            }
        });

        unlockBtn.setOnClickListener(v -> {
            String pin = binding.pinInput.getText().toString();
            if (verify(scId, pin)) {
                unlockSession(scId);
                dialog.dismiss();
                listener.onSuccess();
            } else {
                registerFailure(scId);
                binding.pinInputLayout.setError(activity.getString(R.string.incorrect_pin));
                updateUI.run();
            }
        });
        updateUI.run();
    }

    public static void showSetLockDialog(Activity activity, String scId, Runnable onComplete) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        DialogProjectPinBinding binding = DialogProjectPinBinding.inflate(LayoutInflater.from(activity));
        builder.setView(binding.getRoot());

        binding.tvDialogTitle.setText(R.string.lock_project);
        binding.tvDialogSubtitle.setText(R.string.pin_lock_subtitle);
        binding.tvStatus.setVisibility(View.GONE);

        builder.setPositiveButton(R.string.lock_project, null);
        builder.setNegativeButton(R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();

        Button lockBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        lockBtn.setEnabled(false);

        binding.pinInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String input = s.toString();
                boolean ascii = isAsciiOnly(input);
                binding.tvLangError.setVisibility(ascii ? View.GONE : View.VISIBLE);
                if (!ascii) binding.pinInputLayout.setError(activity.getString(R.string.pin_lang_error_short));
                else binding.pinInputLayout.setError(null);
                lockBtn.setEnabled(ascii && input.matches("^[ -~]{4,12}$"));
            }
        });

        lockBtn.setOnClickListener(v -> {
            String pin = binding.pinInput.getText().toString();
            boolean ok = setLock(scId, pin);
            Toast.makeText(activity, "Lock written: " + ok, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (ok && onComplete != null) onComplete.run();
        });
    }

    private static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return bytesToHex(salt);
    }

    private static String hashPin(String pin, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(hexToBytes(salt));
            byte[] hash = digest.digest(pin.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

    private static boolean isAsciiOnly(String s) {
        if (s == null) return true;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 127) return false;
        return true;
    }

    private static long getRemainingCooldown(String scId) {
        Long end = cooldownTimers.get(scId);
        if (end == null) return 0;
        long rem = end - System.currentTimeMillis();
        if (rem <= 0) { cooldownTimers.remove(scId); attemptCounter.remove(scId); return 0; }
        return rem;
    }

    private static int getRemainingAttempts(String scId) {
        Integer attempts = attemptCounter.get(scId);
        return attempts == null ? MAX_ATTEMPTS : Math.max(0, MAX_ATTEMPTS - attempts);
    }

    private static void registerFailure(String scId) {
        int attempts = attemptCounter.getOrDefault(scId, 0) + 1;
        attemptCounter.put(scId, attempts);
        if (attempts >= MAX_ATTEMPTS) cooldownTimers.put(scId, System.currentTimeMillis() + COOLDOWN_MS);
    }
}
