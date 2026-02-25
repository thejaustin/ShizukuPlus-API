package rikka.shizuku;

import android.os.IBinder;
import androidx.annotation.Nullable;

/**
 * ShizukuPlusAPI provides extended features for Shizuku+, 
 * including Dhizuku (Device Owner) compatibility and enhanced server communication.
 */
public class ShizukuPlusAPI {

    /**
     * Check if the connected server supports Shizuku+ Enhanced API features.
     *
     * @return true if the server is Shizuku+ and has enhanced API enabled.
     */
    public static boolean isEnhancedApiSupported() {
        return Shizuku.isCustomApiEnabled();
    }

    /**
     * Compatibility layer for Dhizuku (Device Owner) features.
     */
    public static class Dhizuku {
        
        /**
         * Get the DevicePolicyManager binder shared by Shizuku+.
         * 
         * @return The DPM binder if available and Shizuku+ is in Dhizuku mode.
         */
        @Nullable
        public static IBinder getBinder() {
            return Shizuku.Dhizuku.getBinder();
        }

        /**
         * Check if Dhizuku mode is active on the current Shizuku+ server.
         */
        public static boolean isAvailable() {
            return getBinder() != null;
        }
    }
}
