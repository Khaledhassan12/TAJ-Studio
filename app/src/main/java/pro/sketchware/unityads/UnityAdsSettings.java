package pro.sketchware.unityads;

import androidx.annotation.NonNull;
import mod.hey.studios.project.ProjectSettings;

/** Per-project Unity Ads Android game ID and exported-build test-mode setting. */
public final class UnityAdsSettings {
    public static final String KEY_GAME_ID = "unity_ads_game_id";
    public static final String KEY_TEST_MODE = "unity_ads_test_mode";
    private UnityAdsSettings() {}
    @NonNull public static String getGameId(@NonNull String scId) {
        return new ProjectSettings(scId).getValue(KEY_GAME_ID, "").trim();
    }
    public static boolean isTestMode(@NonNull String scId) {
        return ProjectSettings.SETTING_GENERIC_VALUE_TRUE.equals(new ProjectSettings(scId).getValue(KEY_TEST_MODE, ProjectSettings.SETTING_GENERIC_VALUE_FALSE));
    }
    public static boolean isConfigured(@NonNull String scId) { return isValidGameId(getGameId(scId)); }
    public static boolean isValidGameId(@NonNull String gameId) { return gameId.trim().matches("[0-9]{1,12}"); }
    public static void save(@NonNull String scId, @NonNull String gameId, boolean testMode) {
        ProjectSettings settings = new ProjectSettings(scId);
        settings.setValue(KEY_GAME_ID, gameId.trim());
        settings.setValue(KEY_TEST_MODE, testMode ? ProjectSettings.SETTING_GENERIC_VALUE_TRUE : ProjectSettings.SETTING_GENERIC_VALUE_FALSE);
    }
}
