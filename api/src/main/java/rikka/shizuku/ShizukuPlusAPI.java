package rikka.shizuku;

import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
     * Internal utility for safe command execution with legacy fallback.
     */
    private static class SafeShell {
        @NonNull
        static Shell.CommandResult run(@NonNull String[] cmd) {
            // If the server is Shizuku+, use the optimized synchronous path
            if (isEnhancedApiSupported()) {
                return Shell.executeCommand(cmd);
            }
            
            // Legacy Fallback: Manually manage Shizuku.newProcess (this allows the API to work on old Shizuku)
            try {
                java.lang.reflect.Method method = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                method.setAccessible(true);
                ShizukuRemoteProcess process = (ShizukuRemoteProcess) method.invoke(null, cmd, null, null);
                if (process == null) return new Shell.CommandResult(-1, "", "Legacy process creation failed");

                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                }
                int exitCode = process.waitFor();
                return new Shell.CommandResult(exitCode, output.toString().trim(), "");
            } catch (Exception e) {
                return new Shell.CommandResult(-1, "", "Fallback failed: " + e.getMessage());
            }
        }
    }

    /**
     * Synchronously execute a shell command through Shizuku and return the result.
     * This eliminates the boilerplate of managing streams and processes for simple tasks.
     */
    public static class Shell {

        public static class CommandResult {
            public final int exitCode;
            public final String output;
            public final String error;

            public CommandResult(int exitCode, String output, String error) {
                this.exitCode = exitCode;
                this.output = output;
                this.error = error;
            }

            public boolean isSuccess() {
                return exitCode == 0;
            }
        }

        /**
         * Execute a command string directly via `sh -c`.
         */
        @NonNull
        public static CommandResult executeCommand(@NonNull String command) {
            return executeCommand(new String[]{"sh", "-c", command});
        }

        /**
         * Execute an array of command arguments.
         */
        @NonNull
        public static CommandResult executeCommand(@NonNull String[] cmd) {
            try {
                java.lang.reflect.Method method = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                method.setAccessible(true);
                ShizukuRemoteProcess process = (ShizukuRemoteProcess) method.invoke(null, cmd, null, null);
                
                if (process == null) return new CommandResult(-1, "", "Process creation failed");

                StringBuilder output = new StringBuilder();
                StringBuilder error = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append('\n');
                    }
                }

                int exitCode = process.waitFor();
                return new CommandResult(exitCode, output.toString().trim(), error.toString().trim());
            } catch (Exception e) {
                return new CommandResult(-1, "", e.getMessage() != null ? e.getMessage() : "Unknown exception");
            }
        }
    }

    /**
     * Easy wrappers for managing Android System settings (system, secure, global) via Shizuku.
     */
    public static class Settings {

        public static boolean putSystem(@NonNull String key, @NonNull String value) {
            return SafeShell.run(new String[]{"settings", "put", "system", key, value}).isSuccess();
        }

        public static boolean putSecure(@NonNull String key, @NonNull String value) {
            return SafeShell.run(new String[]{"settings", "put", "secure", key, value}).isSuccess();
        }

        public static boolean putGlobal(@NonNull String key, @NonNull String value) {
            return SafeShell.run(new String[]{"settings", "put", "global", key, value}).isSuccess();
        }

        @NonNull
        public static String getSystem(@NonNull String key) {
            return SafeShell.run(new String[]{"settings", "get", "system", key}).output;
        }
        
        @NonNull
        public static String getSecure(@NonNull String key) {
            return SafeShell.run(new String[]{"settings", "get", "secure", key}).output;
        }

        @NonNull
        public static String getGlobal(@NonNull String key) {
            return SafeShell.run(new String[]{"settings", "get", "global", key}).output;
        }
    }

    /**
     * Easy wrappers for Package Manager operations.
     */
    public static class PackageManager {

        /**
         * Install an APK file using the pm command via Shizuku.
         * 
         * @param apkFilePath The absolute path to the APK file.
         * @return true if installation succeeded.
         */
        public static boolean installPackage(@NonNull String apkFilePath) {
            return SafeShell.run(new String[]{"pm", "install", "-r", apkFilePath}).isSuccess();
        }

        /**
         * Uninstall a package.
         * 
         * @param packageName The package name to uninstall.
         * @return true if uninstallation succeeded.
         */
        public static boolean uninstallPackage(@NonNull String packageName) {
            return SafeShell.run(new String[]{"pm", "uninstall", packageName}).isSuccess();
        }
        
        /**
         * Clear data for a specific package.
         */
        public static boolean clearPackageData(@NonNull String packageName) {
            return SafeShell.run(new String[]{"pm", "clear", packageName}).isSuccess();
        }
    }

    /**
     * Easy wrappers for managing System Overlays (RRO).
     */
    public static class OverlayManager {

        /**
         * Enable a system overlay.
         */
        public static boolean enableOverlay(@NonNull String packageName) {
            return SafeShell.run(new String[]{"cmd", "overlay", "enable", "--user", "current", packageName}).isSuccess();
        }

        /**
         * Disable a system overlay.
         */
        public static boolean disableOverlay(@NonNull String packageName) {
            return SafeShell.run(new String[]{"cmd", "overlay", "disable", "--user", "current", packageName}).isSuccess();
        }
        
        /**
         * Set the priority of an overlay.
         */
        public static boolean setPriority(@NonNull String packageName, @NonNull String parentPackageName) {
            return SafeShell.run(new String[]{"cmd", "overlay", "set-priority", packageName, parentPackageName}).isSuccess();
        }
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
