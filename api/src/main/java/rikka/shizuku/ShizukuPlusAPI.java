package rikka.shizuku;

import android.os.IBinder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.RemoteException;
import android.graphics.Rect;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Collections;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;

import af.shizuku.server.IActivityManagerPlus;
import af.shizuku.server.IWindowManagerPlus;
import af.shizuku.server.IOverlayManagerPlus;
import af.shizuku.server.INetworkGovernorPlus;
import af.shizuku.server.IAICorePlus;
import af.shizuku.server.IContinuityBridge;
import af.shizuku.server.IVirtualMachineManager;
import af.shizuku.server.IStorageProxy;

/**
 * Shizuku+API provides extended features for Shizuku+,
 * including Dhizuku (Device Owner) compatibility and enhanced server communication.
 */
public class ShizukuPlusAPI {
    private static final String TAG = "Shizuku+API";

    /**
     * Check if the connected server supports Shizuku+ Enhanced API features.
     *
     * @return true if the server is Shizuku+ and has enhanced API enabled.
     */
    public static boolean isEnhancedApiSupported() {
        return Shizuku.isCustomApiEnabled();
    }

    private static <T> T getPlusInterface(int code, android.os.IInterface creator) {
        if (!isEnhancedApiSupported()) return null;
        IBinder serviceBinder = Shizuku.getBinder();
        if (serviceBinder == null) return null; // Shizuku not connected
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("af.shizuku.server.IShizukuService");
            if (serviceBinder.transact(code, data, reply, 0)) {
                reply.readException();
                IBinder binder = reply.readStrongBinder();
                if (binder != null) {
                    return (T) binder;
                }
            }
        } catch (RemoteException e) {
            // Transaction failed — binder died
        } finally {
            reply.recycle();
            data.recycle();
        }
        return null;
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

                final StringBuilder output = new StringBuilder();
                final StringBuilder error = new StringBuilder();

                // Drain stderr on a separate thread to prevent pipe deadlock:
                // if the process fills the stderr OS buffer while we block on stdout, it hangs.
                Thread stderrThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            error.append(line).append('\n');
                        }
                    } catch (Exception e) {
                        Log.w("ShizukuPlusAPI", "Failed to read stderr from shell command", e);
                    }
                });
                stderrThread.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                }

                stderrThread.join();
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

        @Nullable
        private static IOverlayManagerPlus getService() {
            IBinder binder = getPlusInterface(113, null);
            return binder != null ? IOverlayManagerPlus.Stub.asInterface(binder) : null;
        }

        /**
         * Enable a system overlay.
         */
        public static boolean enableOverlay(@NonNull String packageName) {
            IOverlayManagerPlus service = getService();
            if (service != null) {
                try {
                    return service.setOverlayEnabled(packageName, true);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to enable overlay " + packageName, e);
                }
            }
            return SafeShell.run(new String[]{"cmd", "overlay", "enable", "--user", "current", packageName}).isSuccess();
        }

        /**
         * Disable a system overlay.
         */
        public static boolean disableOverlay(@NonNull String packageName) {
            IOverlayManagerPlus service = getService();
            if (service != null) {
                try {
                    return service.setOverlayEnabled(packageName, false);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to disable overlay " + packageName, e);
                }
            }
            return SafeShell.run(new String[]{"cmd", "overlay", "disable", "--user", "current", packageName}).isSuccess();
        }
        
        /**
         * Set the priority of an overlay.
         */
        public static boolean setPriority(@NonNull String packageName, @NonNull String parentPackageName) {
            IOverlayManagerPlus service = getService();
            if (service != null) {
                try {
                    // Note: setHighestPriority only takes one arg in AIDL for now
                    return service.setHighestPriority(packageName);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to set priority for overlay " + packageName, e);
                }
            }
            return SafeShell.run(new String[]{"cmd", "overlay", "set-priority", packageName, parentPackageName}).isSuccess();
        }

        @NonNull
        public static List<String> getAllOverlays() {
            IOverlayManagerPlus service = getService();
            if (service != null) {
                try {
                    return service.getAllOverlays();
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to get all overlays", e);
                }
            }
            return Collections.emptyList();
        }

        /**
         * Inject a dynamic resource overlay (Android 12+).
         */
        public static boolean injectResourceOverlay(@NonNull String targetPackage, @NonNull String resourceName, int type, @NonNull String value) {
            IOverlayManagerPlus service = getService();
            if (service != null) {
                try {
                    return service.injectResourceOverlay(targetPackage, resourceName, type, value);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to inject resource overlay for " + targetPackage, e);
                }
            }
            return false;
        }
    }

    /**
     * Advanced Activity Manager features.
     */
    public static class ActivityManager {
        @Nullable
        private static IActivityManagerPlus getService() {
            IBinder binder = getPlusInterface(115, null);
            return binder != null ? IActivityManagerPlus.Stub.asInterface(binder) : null;
        }

        public static boolean deepForceStop(@NonNull String packageName) {
            IActivityManagerPlus service = getService();
            if (service != null) {
                try {
                    return service.deepForceStop(packageName);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to deep force stop " + packageName, e);
                }
            }
            return SafeShell.run(new String[]{"am", "force-stop", packageName}).isSuccess();
        }

        public static boolean killAllBackgroundProcesses() {
            IActivityManagerPlus service = getService();
            if (service != null) {
                try {
                    return service.killAllBackgroundProcesses();
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to kill all background processes", e);
                }
            }
            return false;
        }

        /**
         * Set the standby bucket for an app (e.g., ACTIVE, WORKING_SET, FREQUENT, RARE, RESTRICTED).
         */
        public static boolean setAppStandbyBucket(@NonNull String packageName, int bucket) {
            IActivityManagerPlus service = getService();
            if (service != null) {
                try {
                    return service.setAppStandbyBucket(packageName, bucket);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to set standby bucket for " + packageName, e);
                }
            }
            return false;
        }
    }

    /**
     * Advanced Window Manager and Desktop Mode features.
     */
    public static class WindowManager {
        @Nullable
        private static IWindowManagerPlus getService() {
            IBinder binder = getPlusInterface(110, null);
            return binder != null ? IWindowManagerPlus.Stub.asInterface(binder) : null;
        }

        public static void forceResizable(@NonNull String packageName, boolean enabled) {
            IWindowManagerPlus service = getService();
            if (service != null) {
                try {
                    service.forceResizable(packageName, enabled);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to force resizable for " + packageName, e);
                }
            }
        }

        public static void setAlwaysOnTop(int taskId, boolean enabled) {
            IWindowManagerPlus service = getService();
            if (service != null) {
                try {
                    service.setAlwaysOnTop(taskId, enabled);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to set always on top for task " + taskId, e);
                }
            }
        }
    }

    /**
     * Privileged Network and DNS management.
     */
    public static class NetworkGovernor {
        @Nullable
        private static INetworkGovernorPlus getService() {
            IBinder binder = getPlusInterface(114, null);
            return binder != null ? INetworkGovernorPlus.Stub.asInterface(binder) : null;
        }

        public static boolean setPrivateDns(@Nullable String mode, @Nullable String hostname) {
            INetworkGovernorPlus service = getService();
            if (service != null) {
                try {
                    return service.setPrivateDns(mode, hostname);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to set private DNS mode=" + mode + " hostname=" + hostname, e);
                }
            }
            return false;
        }

        /**
         * Add a package to the system's restricted network list (firewall).
         */
        public static boolean restrictAppNetwork(@NonNull String packageName, boolean restricted) {
            INetworkGovernorPlus service = getService();
            if (service != null) {
                try {
                    return service.restrictAppNetwork(packageName, restricted);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to restrict app network for " + packageName, e);
                }
            }
            return false;
        }

        /**
         * Get the current network restriction state for a package.
         */
        public static boolean isAppNetworkRestricted(@NonNull String packageName) {
            INetworkGovernorPlus service = getService();
            if (service != null) {
                try {
                    return service.isAppNetworkRestricted(packageName);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to get app network restricted state for " + packageName, e);
                }
            }
            return false;
        }
    }

    /**
     * Intelligence Bridge (AI) and screen-aware features.
     */
    public static class AICore {
        @Nullable
        private static IAICorePlus getService() {
            IBinder binder = getPlusInterface(109, null);
            return binder != null ? IAICorePlus.Stub.asInterface(binder) : null;
        }

        public static int getPixelColor(int x, int y) {
            IAICorePlus service = getService();
            if (service != null) {
                try {
                    return service.getPixelColor(x, y);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to get pixel color at (" + x + ", " + y + ")", e);
                }
            }
            return 0;
        }

        /**
         * Schedule a high-priority task on the Neural Processing Unit (NPU).
         */
        public static boolean scheduleNPULoad(@NonNull Bundle taskData) {
            IAICorePlus service = getService();
            if (service != null) {
                try {
                    return service.scheduleNPULoad(taskData);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to schedule NPU load", e);
                }
            }
            return false;
        }

        /**
         * Capture a privileged screenshot of a specific window/layer for AI analysis.
         */
        @Nullable
        public static Bitmap captureLayer(int layerId) {
            IAICorePlus service = getService();
            if (service != null) {
                try {
                    return service.captureLayer(layerId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to capture layer " + layerId, e);
                }
            }
            return null;
        }

        /**
         * Get current system intelligence context (detected entities, screen text).
         */
        @Nullable
        public static Bundle getSystemContext() {
            IAICorePlus service = getService();
            if (service != null) {
                try {
                    return service.getSystemContext();
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to get system context", e);
                }
            }
            return null;
        }

        /**
         * Simulate a physical touch on the screen.
         */
        public static boolean simulateTouch(float x, float y) {
            IAICorePlus service = getService();
            if (service != null) {
                try {
                    return service.simulateTouch(x, y);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to simulate touch", e);
                }
            }
            return false;
        }

        /**
         * Simulate a swipe gesture on the screen.
         */
        public static boolean simulateSwipe(float x1, float y1, float x2, float y2, int duration) {
            IAICorePlus service = getService();
            if (service != null) {
                try {
                    return service.simulateSwipe(x1, y1, x2, y2, duration);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to simulate swipe", e);
                }
            }
            return false;
        }

        /**
         * Simulate typing text input.
         */
        public static boolean simulateText(@NonNull String text) {
            IAICorePlus service = getService();
            if (service != null) {
                try {
                    return service.simulateText(text);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to simulate text", e);
                }
            }
            return false;
        }

        /**
         * Get the current window hierarchy (UI elements and text) for AI parsing.
         */
        @Nullable
        public static String getWindowHierarchy() {
            IAICorePlus service = getService();
            if (service != null) {
                try {
                    return service.getWindowHierarchy();
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to get window hierarchy", e);
                }
            }
            return null;
        }
    }

    /**
     * Multi-device privileged continuity features.
     */
    public static class Continuity {
        @Nullable
        private static IContinuityBridge getService() {
            IBinder binder = getPlusInterface(111, null);
            return binder != null ? IContinuityBridge.Stub.asInterface(binder) : null;
        }

        @NonNull
        public static List<String> listEligibleDevices() {
            IContinuityBridge service = getService();
            if (service != null) {
                try {
                    return service.listEligibleDevices();
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to list eligible devices", e);
                }
            }
            return Collections.emptyList();
        }
    }

    /**
     * Virtual Machine Manager (AVF) for Linux/Microdroid.
     */
    public static class VirtualMachine {
        @Nullable
        private static IVirtualMachineManager getService() {
            IBinder binder = getPlusInterface(107, null);
            return binder != null ? IVirtualMachineManager.Stub.asInterface(binder) : null;
        }

        @NonNull
        public static List<String> list() {
            IVirtualMachineManager service = getService();
            if (service != null) {
                try {
                    return service.list();
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to list virtual machines", e);
                }
            }
            return Collections.emptyList();
        }

        public static boolean start(@NonNull String name) {
            IVirtualMachineManager service = getService();
            if (service != null) {
                try {
                    return service.start(name);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to start virtual machine " + name, e);
                }
            }
            return false;
        }

        public static boolean stop(@NonNull String name) {
            IVirtualMachineManager service = getService();
            if (service != null) {
                try {
                    return service.stop(name);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to stop virtual machine " + name, e);
                }
            }
            return false;
        }

        public static boolean create(@NonNull String name, @NonNull Bundle config) {
            IVirtualMachineManager service = getService();
            if (service != null) {
                try {
                    return service.create(name, config);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to create virtual machine " + name, e);
                }
            }
            return false;
        }

        public static boolean delete(@NonNull String name) {
            IVirtualMachineManager service = getService();
            if (service != null) {
                try {
                    return service.delete(name);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to delete virtual machine " + name, e);
                }
            }
            return false;
        }

        @Nullable
        public static String getStatus(@NonNull String name) {
            IVirtualMachineManager service = getService();
            if (service != null) {
                try {
                    return service.getStatus(name);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to get status of virtual machine " + name, e);
                }
            }
            return null;
        }
    }

    /**
     * Storage Bridge for bypassing Android 16/17 storage restrictions.
     */
    public static class StorageProxy {
        @Nullable
        private static IStorageProxy getService() {
            IBinder binder = getPlusInterface(108, null); // 108 is getStorageProxy
            return binder != null ? IStorageProxy.Stub.asInterface(binder) : null;
        }

        public static boolean exists(@NonNull String path) {
            IStorageProxy service = getService();
            if (service != null) {
                try {
                    return service.exists(path);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to check if path exists: " + path, e);
                }
            }
            return false;
        }

        public static boolean delete(@NonNull String path) {
            IStorageProxy service = getService();
            if (service != null) {
                try {
                    return service.delete(path);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to delete path: " + path, e);
                }
            }
            return false;
        }

        @Nullable
        public static ParcelFileDescriptor openFile(@NonNull String path, int mode) {
            IStorageProxy service = getService();
            if (service != null) {
                try {
                    return service.openFile(path, mode);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to open file: " + path, e);
                }
            }
            return null;
        }

        @Nullable
        public static List<String> listFiles(@NonNull String path) {
            IStorageProxy service = getService();
            if (service != null) {
                try {
                    return service.listFiles(path);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to list files in: " + path, e);
                }
            }
            return null;
        }

        @Nullable
        public static Bundle getFileInfo(@NonNull String path) {
            IStorageProxy service = getService();
            if (service != null) {
                try {
                    return service.getFileInfo(path);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to get file info for: " + path, e);
                }
            }
            return null;
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
