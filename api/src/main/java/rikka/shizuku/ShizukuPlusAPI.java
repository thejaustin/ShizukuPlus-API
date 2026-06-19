package rikka.shizuku;

import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.graphics.Bitmap;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import af.shizuku.server.IActivityManagerPlus;
import af.shizuku.server.IAICorePlus;
import af.shizuku.server.IContinuityBridge;
import af.shizuku.server.INetworkGovernorPlus;
import af.shizuku.server.IOverlayManagerPlus;
import af.shizuku.server.IShizukuService;
import af.shizuku.server.IStorageProxy;
import af.shizuku.server.IVirtualMachineManager;
import af.shizuku.server.IWindowManagerPlus;

/**
 * Shizuku+API — extended features available when the connected Shizuku server
 * is a Shizuku+ build with enhanced API enabled.
 *
 * <p>All methods that touch a remote binder are safe to call from any thread.
 * They return {@code null}/{@code false}/empty-list when Shizuku is not
 * connected, the enhanced API is not supported, or a transient IPC error occurs.
 */
public class ShizukuPlusAPI {
    private static final String TAG = "Shizuku+API";

    /** Timeout for blocking shell-command reads, in seconds. */
    private static final long SHELL_TIMEOUT_SECONDS = 30;

    // -------------------------------------------------------------------------
    // Core connection helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the connected server is a Shizuku+ build that
     * has the enhanced API enabled. Safe to call from any thread.
     */
    public static boolean isEnhancedApiSupported() {
        return Shizuku.isCustomApiEnabled();
    }

    /**
     * Returns a live {@link IShizukuService} proxy, or {@code null} if
     * Shizuku is not connected or the binder has died.
     */
    @Nullable
    private static IShizukuService getShizukuService() {
        try {
            IBinder binder = Shizuku.getBinder();
            if (binder == null || !binder.isBinderAlive()) return null;
            return IShizukuService.Stub.asInterface(binder);
        } catch (Exception e) {
            Log.w(TAG, "getShizukuService: failed to obtain binder", e);
            return null;
        }
    }

    /**
     * Returns a live {@link IShizukuService} proxy only when the enhanced API
     * is confirmed active, or {@code null} otherwise.
     */
    @Nullable
    private static IShizukuService requirePlusService() {
        if (!isEnhancedApiSupported()) return null;
        return getShizukuService();
    }

    // -------------------------------------------------------------------------
    // Shell
    // -------------------------------------------------------------------------

    /** Result of a synchronous shell command execution. */
    public static class CommandResult {
        public final int exitCode;
        @NonNull public final String output;
        @NonNull public final String error;

        public CommandResult(int exitCode, @NonNull String output, @NonNull String error) {
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }

        public boolean isSuccess() { return exitCode == 0; }
    }

    /**
     * Execute a shell command string (via {@code sh -c}) through Shizuku and
     * return the result synchronously. Blocks the calling thread for up to
     * {@link #SHELL_TIMEOUT_SECONDS} seconds before returning an error result.
     *
     * <p>Do not call on the main thread.
     */
    @NonNull
    public static CommandResult executeShell(@NonNull String command) {
        return executeShell(new String[]{"sh", "-c", command});
    }

    /**
     * Execute an argument array through Shizuku and return the result
     * synchronously. Blocks up to {@link #SHELL_TIMEOUT_SECONDS} seconds.
     *
     * <p>Do not call on the main thread.
     */
    @NonNull
    public static CommandResult executeShell(@NonNull String[] cmd) {
        try {
            // newProcess is the correct public API surface for Shizuku shell execution.
            ShizukuRemoteProcess process = Shizuku.newProcess(cmd, null, null);
            if (process == null) {
                return new CommandResult(-1, "", "Process creation returned null");
            }

            final StringBuilder output = new StringBuilder();
            final StringBuilder error  = new StringBuilder();

            // Drain stderr on a parallel thread: if stdout fills the OS pipe
            // buffer while we block reading it, stderr must drain or we deadlock.
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        error.append(line).append('\n');
                    }
                } catch (Exception ignored) {}
            }, "shizuku-stderr");
            stderrThread.setDaemon(true);
            stderrThread.start();

            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            stderrThread.join(TimeUnit.SECONDS.toMillis(SHELL_TIMEOUT_SECONDS));
            int exitCode = process.waitFor();
            return new CommandResult(exitCode, output.toString().trim(), error.toString().trim());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "", "Interrupted");
        } catch (Exception e) {
            return new CommandResult(-1, "", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    // -------------------------------------------------------------------------
    // Settings
    // -------------------------------------------------------------------------

    /** Wrappers for Android System settings (system / secure / global). */
    public static class Settings {

        public static boolean putSystem(@NonNull String key, @NonNull String value) {
            return executeShell(new String[]{"settings", "put", "system", key, value}).isSuccess();
        }

        public static boolean putSecure(@NonNull String key, @NonNull String value) {
            return executeShell(new String[]{"settings", "put", "secure", key, value}).isSuccess();
        }

        public static boolean putGlobal(@NonNull String key, @NonNull String value) {
            return executeShell(new String[]{"settings", "put", "global", key, value}).isSuccess();
        }

        @NonNull
        public static String getSystem(@NonNull String key) {
            return executeShell(new String[]{"settings", "get", "system", key}).output;
        }

        @NonNull
        public static String getSecure(@NonNull String key) {
            return executeShell(new String[]{"settings", "get", "secure", key}).output;
        }

        @NonNull
        public static String getGlobal(@NonNull String key) {
            return executeShell(new String[]{"settings", "get", "global", key}).output;
        }
    }

    // -------------------------------------------------------------------------
    // Package Manager
    // -------------------------------------------------------------------------

    /** Wrappers for package-manager operations via Shizuku. */
    public static class PackageManager {

        public static boolean installPackage(@NonNull String apkFilePath) {
            return executeShell(new String[]{"pm", "install", "-r", apkFilePath}).isSuccess();
        }

        public static boolean uninstallPackage(@NonNull String packageName) {
            return executeShell(new String[]{"pm", "uninstall", packageName}).isSuccess();
        }

        public static boolean clearPackageData(@NonNull String packageName) {
            return executeShell(new String[]{"pm", "clear", packageName}).isSuccess();
        }
    }

    // -------------------------------------------------------------------------
    // OverlayManager — requires enhanced API
    // -------------------------------------------------------------------------

    /** Runtime resource overlay (RRO) management via the Plus AIDL. */
    public static class OverlayManager {

        @Nullable
        private static IOverlayManagerPlus getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getOverlayManagerPlus(); }
            catch (RemoteException e) { Log.w(TAG, "getOverlayManagerPlus", e); return null; }
        }

        public static boolean enableOverlay(@NonNull String packageName) {
            IOverlayManagerPlus s = getService();
            if (s != null) {
                try { return s.setOverlayEnabled(packageName, true); }
                catch (RemoteException e) { Log.w(TAG, "enableOverlay " + packageName, e); }
            }
            return executeShell(new String[]{"cmd", "overlay", "enable", "--user", "current", packageName}).isSuccess();
        }

        public static boolean disableOverlay(@NonNull String packageName) {
            IOverlayManagerPlus s = getService();
            if (s != null) {
                try { return s.setOverlayEnabled(packageName, false); }
                catch (RemoteException e) { Log.w(TAG, "disableOverlay " + packageName, e); }
            }
            return executeShell(new String[]{"cmd", "overlay", "disable", "--user", "current", packageName}).isSuccess();
        }

        public static boolean setHighestPriority(@NonNull String packageName) {
            IOverlayManagerPlus s = getService();
            if (s != null) {
                try { return s.setHighestPriority(packageName); }
                catch (RemoteException e) { Log.w(TAG, "setHighestPriority " + packageName, e); }
            }
            return false;
        }

        @NonNull
        public static List<String> getAllOverlays() {
            IOverlayManagerPlus s = getService();
            if (s != null) {
                try { return s.getAllOverlays(); }
                catch (RemoteException e) { Log.w(TAG, "getAllOverlays", e); }
            }
            return Collections.emptyList();
        }

        public static boolean injectResourceOverlay(
                @NonNull String targetPackage, @NonNull String resourceName,
                int type, @NonNull String value) {
            IOverlayManagerPlus s = getService();
            if (s != null) {
                try { return s.injectResourceOverlay(targetPackage, resourceName, type, value); }
                catch (RemoteException e) { Log.w(TAG, "injectResourceOverlay " + targetPackage, e); }
            }
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // ActivityManager — requires enhanced API
    // -------------------------------------------------------------------------

    /** Advanced Activity Manager operations. */
    public static class ActivityManager {

        @Nullable
        private static IActivityManagerPlus getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getActivityManagerPlus(); }
            catch (RemoteException e) { Log.w(TAG, "getActivityManagerPlus", e); return null; }
        }

        public static boolean deepForceStop(@NonNull String packageName) {
            IActivityManagerPlus s = getService();
            if (s != null) {
                try { return s.deepForceStop(packageName); }
                catch (RemoteException e) { Log.w(TAG, "deepForceStop " + packageName, e); }
            }
            return executeShell(new String[]{"am", "force-stop", packageName}).isSuccess();
        }

        public static boolean killAllBackgroundProcesses() {
            IActivityManagerPlus s = getService();
            if (s == null) return false;
            try { return s.killAllBackgroundProcesses(); }
            catch (RemoteException e) { Log.w(TAG, "killAllBackgroundProcesses", e); return false; }
        }

        public static boolean setAppStandbyBucket(@NonNull String packageName, int bucket) {
            IActivityManagerPlus s = getService();
            if (s == null) return false;
            try { return s.setAppStandbyBucket(packageName, bucket); }
            catch (RemoteException e) { Log.w(TAG, "setAppStandbyBucket " + packageName, e); return false; }
        }
    }

    // -------------------------------------------------------------------------
    // WindowManager — requires enhanced API
    // -------------------------------------------------------------------------

    /** Window Manager and desktop-mode features. */
    public static class WindowManager {

        @Nullable
        private static IWindowManagerPlus getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getWindowManagerPlus(); }
            catch (RemoteException e) { Log.w(TAG, "getWindowManagerPlus", e); return null; }
        }

        public static void forceResizable(@NonNull String packageName, boolean enabled) {
            IWindowManagerPlus s = getService();
            if (s == null) return;
            try { s.forceResizable(packageName, enabled); }
            catch (RemoteException e) { Log.w(TAG, "forceResizable " + packageName, e); }
        }

        public static void setAlwaysOnTop(int taskId, boolean enabled) {
            IWindowManagerPlus s = getService();
            if (s == null) return;
            try { s.setAlwaysOnTop(taskId, enabled); }
            catch (RemoteException e) { Log.w(TAG, "setAlwaysOnTop task=" + taskId, e); }
        }
    }

    // -------------------------------------------------------------------------
    // NetworkGovernor — requires enhanced API
    // -------------------------------------------------------------------------

    /** Privileged network and DNS management. */
    public static class NetworkGovernor {

        @Nullable
        private static INetworkGovernorPlus getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getNetworkGovernorPlus(); }
            catch (RemoteException e) { Log.w(TAG, "getNetworkGovernorPlus", e); return null; }
        }

        public static boolean setPrivateDns(@Nullable String mode, @Nullable String hostname) {
            INetworkGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.setPrivateDns(mode, hostname); }
            catch (RemoteException e) { Log.w(TAG, "setPrivateDns", e); return false; }
        }

        public static boolean restrictAppNetwork(@NonNull String packageName, boolean restricted) {
            INetworkGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.restrictAppNetwork(packageName, restricted); }
            catch (RemoteException e) { Log.w(TAG, "restrictAppNetwork " + packageName, e); return false; }
        }

        public static boolean isAppNetworkRestricted(@NonNull String packageName) {
            INetworkGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.isAppNetworkRestricted(packageName); }
            catch (RemoteException e) { Log.w(TAG, "isAppNetworkRestricted " + packageName, e); return false; }
        }
    }

    // -------------------------------------------------------------------------
    // AICore — requires enhanced API
    // -------------------------------------------------------------------------

    /** AI and screen-aware features (pixel inspection, input simulation, etc.). */
    public static class AICore {

        @Nullable
        private static IAICorePlus getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getAICorePlus(); }
            catch (RemoteException e) { Log.w(TAG, "getAICorePlus", e); return null; }
        }

        public static int getPixelColor(int x, int y) {
            IAICorePlus s = getService();
            if (s == null) return 0;
            try { return s.getPixelColor(x, y); }
            catch (RemoteException e) { Log.w(TAG, "getPixelColor", e); return 0; }
        }

        @Nullable
        public static Bundle scheduleNPULoad(@NonNull Bundle taskData) {
            IAICorePlus s = getService();
            if (s == null) return null;
            try { return s.scheduleNPULoad(taskData); }
            catch (RemoteException e) { Log.w(TAG, "scheduleNPULoad", e); return null; }
        }

        @Nullable
        public static Bitmap captureLayer(int layerId) {
            IAICorePlus s = getService();
            if (s == null) return null;
            try { return s.captureLayer(layerId); }
            catch (RemoteException e) { Log.w(TAG, "captureLayer " + layerId, e); return null; }
        }

        @Nullable
        public static Bundle getSystemContext() {
            IAICorePlus s = getService();
            if (s == null) return null;
            try { return s.getSystemContext(); }
            catch (RemoteException e) { Log.w(TAG, "getSystemContext", e); return null; }
        }

        public static boolean simulateTouch(float x, float y) {
            IAICorePlus s = getService();
            if (s == null) return false;
            try { return s.simulateTouch(x, y); }
            catch (RemoteException e) { Log.w(TAG, "simulateTouch", e); return false; }
        }

        public static boolean simulateSwipe(float x1, float y1, float x2, float y2, int durationMs) {
            IAICorePlus s = getService();
            if (s == null) return false;
            try { return s.simulateSwipe(x1, y1, x2, y2, durationMs); }
            catch (RemoteException e) { Log.w(TAG, "simulateSwipe", e); return false; }
        }

        public static boolean simulateText(@NonNull String text) {
            IAICorePlus s = getService();
            if (s == null) return false;
            try { return s.simulateText(text); }
            catch (RemoteException e) { Log.w(TAG, "simulateText", e); return false; }
        }

        @Nullable
        public static String getWindowHierarchy() {
            IAICorePlus s = getService();
            if (s == null) return null;
            try { return s.getWindowHierarchy(); }
            catch (RemoteException e) { Log.w(TAG, "getWindowHierarchy", e); return null; }
        }
    }

    // -------------------------------------------------------------------------
    // Continuity — requires enhanced API
    // -------------------------------------------------------------------------

    /** Multi-device privileged continuity features. */
    public static class Continuity {

        @Nullable
        private static IContinuityBridge getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getContinuityBridge(); }
            catch (RemoteException e) { Log.w(TAG, "getContinuityBridge", e); return null; }
        }

        @NonNull
        public static List<String> listEligibleDevices() {
            IContinuityBridge s = getService();
            if (s == null) return Collections.emptyList();
            try { return s.listEligibleDevices(); }
            catch (RemoteException e) { Log.w(TAG, "listEligibleDevices", e); return Collections.emptyList(); }
        }
    }

    // -------------------------------------------------------------------------
    // VirtualMachine — requires enhanced API
    // -------------------------------------------------------------------------

    /** Android Virtualization Framework (AVF) / Microdroid VM management. */
    public static class VirtualMachine {

        @Nullable
        private static IVirtualMachineManager getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getVirtualMachineManager(); }
            catch (RemoteException e) { Log.w(TAG, "getVirtualMachineManager", e); return null; }
        }

        @NonNull
        public static List<String> list() {
            IVirtualMachineManager s = getService();
            if (s == null) return Collections.emptyList();
            try { return s.list(); }
            catch (RemoteException e) { Log.w(TAG, "vm list", e); return Collections.emptyList(); }
        }

        public static boolean start(@NonNull String name) {
            IVirtualMachineManager s = getService();
            if (s == null) return false;
            try { return s.start(name); }
            catch (RemoteException e) { Log.w(TAG, "vm start " + name, e); return false; }
        }

        public static boolean stop(@NonNull String name) {
            IVirtualMachineManager s = getService();
            if (s == null) return false;
            try { return s.stop(name); }
            catch (RemoteException e) { Log.w(TAG, "vm stop " + name, e); return false; }
        }

        public static boolean create(@NonNull String name, @NonNull Bundle config) {
            IVirtualMachineManager s = getService();
            if (s == null) return false;
            try { return s.create(name, config); }
            catch (RemoteException e) { Log.w(TAG, "vm create " + name, e); return false; }
        }

        public static boolean delete(@NonNull String name) {
            IVirtualMachineManager s = getService();
            if (s == null) return false;
            try { return s.delete(name); }
            catch (RemoteException e) { Log.w(TAG, "vm delete " + name, e); return false; }
        }

        @Nullable
        public static String getStatus(@NonNull String name) {
            IVirtualMachineManager s = getService();
            if (s == null) return null;
            try { return s.getStatus(name); }
            catch (RemoteException e) { Log.w(TAG, "vm status " + name, e); return null; }
        }
    }

    // -------------------------------------------------------------------------
    // StorageProxy — requires enhanced API
    // -------------------------------------------------------------------------

    /** Privileged file-system operations via the Plus storage bridge. */
    public static class StorageProxy {

        @Nullable
        private static IStorageProxy getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getStorageProxy(); }
            catch (RemoteException e) { Log.w(TAG, "getStorageProxy", e); return null; }
        }

        public static boolean exists(@NonNull String path) {
            IStorageProxy s = getService();
            if (s == null) return false;
            try { return s.exists(path); }
            catch (RemoteException e) { Log.w(TAG, "exists " + path, e); return false; }
        }

        public static boolean delete(@NonNull String path) {
            IStorageProxy s = getService();
            if (s == null) return false;
            try { return s.delete(path); }
            catch (RemoteException e) { Log.w(TAG, "delete " + path, e); return false; }
        }

        @Nullable
        public static ParcelFileDescriptor openFile(@NonNull String path, int mode) {
            IStorageProxy s = getService();
            if (s == null) return null;
            try { return s.openFile(path, mode); }
            catch (RemoteException e) { Log.w(TAG, "openFile " + path, e); return null; }
        }

        @Nullable
        public static List<String> listFiles(@NonNull String path) {
            IStorageProxy s = getService();
            if (s == null) return null;
            try { return s.listFiles(path); }
            catch (RemoteException e) { Log.w(TAG, "listFiles " + path, e); return null; }
        }

        @Nullable
        public static Bundle getFileInfo(@NonNull String path) {
            IStorageProxy s = getService();
            if (s == null) return null;
            try { return s.getFileInfo(path); }
            catch (RemoteException e) { Log.w(TAG, "getFileInfo " + path, e); return null; }
        }
    }

    // -------------------------------------------------------------------------
    // Dhizuku — Device Owner compatibility
    // -------------------------------------------------------------------------

    /** Dhizuku (Device Owner) compatibility layer exposed by the Plus server. */
    public static class Dhizuku {

        @Nullable
        public static IBinder getBinder() {
            return Shizuku.Dhizuku.getBinder();
        }

        public static boolean isAvailable() {
            return getBinder() != null;
        }
    }
}
