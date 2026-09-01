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
import af.shizuku.server.IStatusBarGovernorPlus;
import af.shizuku.server.IPackageGovernorPlus;
import af.shizuku.server.IDisplayTunerPlus;
import af.shizuku.server.IAppInspector;
import af.shizuku.server.IPrivilegedDataSource;
import af.shizuku.server.IBackupRestorePlus;
import moe.shizuku.server.IShizukuService;
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
    // StatusBarGovernor — requires enhanced API
    // -------------------------------------------------------------------------

    /** Privileged status bar control: expand/collapse shade, click and manage Quick Settings tiles. */
    public static class StatusBarGovernor {

        @Nullable
        private static IStatusBarGovernorPlus getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getStatusBarGovernorPlus(); }
            catch (RemoteException e) { Log.w(TAG, "getStatusBarGovernorPlus", e); return null; }
        }

        public static boolean disableExpansion() {
            IStatusBarGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.disableExpansion(); }
            catch (RemoteException e) { Log.w(TAG, "disableExpansion", e); return false; }
        }

        public static boolean enableExpansion() {
            IStatusBarGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.enableExpansion(); }
            catch (RemoteException e) { Log.w(TAG, "enableExpansion", e); return false; }
        }

        public static boolean clickTile(@NonNull String component) {
            IStatusBarGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.clickTile(component); }
            catch (RemoteException e) { Log.w(TAG, "clickTile " + component, e); return false; }
        }

        @Nullable
        public static String getCurrentTiles() {
            IStatusBarGovernorPlus s = getService();
            if (s == null) return null;
            try { return s.getCurrentTiles(); }
            catch (RemoteException e) { Log.w(TAG, "getCurrentTiles", e); return null; }
        }

        public static boolean setTiles(@NonNull String tileList) {
            IStatusBarGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.setTiles(tileList); }
            catch (RemoteException e) { Log.w(TAG, "setTiles", e); return false; }
        }

        public static boolean collapse() {
            IStatusBarGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.collapse(); }
            catch (RemoteException e) { Log.w(TAG, "collapse", e); return false; }
        }

        public static boolean expandSettings() {
            IStatusBarGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.expandSettings(); }
            catch (RemoteException e) { Log.w(TAG, "expandSettings", e); return false; }
        }

        /**
         * Add a tile to the Quick Settings panel.
         * System tile specs: "wifi", "bt", "airplane", "dnd", "flashlight", "rotation", "nfc", "internet"
         * Custom tile specs: "custom(com.pkg/.TileService)"
         */
        public static boolean addTile(@NonNull String tileSpec) {
            IStatusBarGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.addTile(tileSpec); }
            catch (RemoteException e) { Log.w(TAG, "addTile " + tileSpec, e); return false; }
        }

        /** Remove a tile from the Quick Settings panel. */
        public static boolean removeTile(@NonNull String tileSpec) {
            IStatusBarGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.removeTile(tileSpec); }
            catch (RemoteException e) { Log.w(TAG, "removeTile " + tileSpec, e); return false; }
        }

        /** Move a tile to a specific zero-based position in the QS panel. */
        public static boolean moveTileToPosition(@NonNull String tileSpec, int position) {
            IStatusBarGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.moveTileToPosition(tileSpec, position); }
            catch (RemoteException e) { Log.w(TAG, "moveTileToPosition " + tileSpec, e); return false; }
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

        public static boolean copyFile(@NonNull String srcPath, @NonNull String destPath) {
            IStorageProxy s = getService();
            if (s == null) return false;
            try { return s.copyFile(srcPath, destPath); }
            catch (RemoteException e) { Log.w(TAG, "copyFile " + srcPath, e); return false; }
        }

        @Nullable
        public static ParcelFileDescriptor openContentUri(@NonNull String contentUri) {
            IStorageProxy s = getService();
            if (s == null) return null;
            try { return s.openContentUri(contentUri); }
            catch (RemoteException e) { Log.w(TAG, "openContentUri " + contentUri, e); return null; }
        }

        /** Stream a gzip-compressed tar of {@code dirPath}. Pass {@code packageName} when
         *  targeting a debuggable app's private data dir so run-as can be used. */
        @Nullable
        public static ParcelFileDescriptor tarDirectory(@NonNull String dirPath, @Nullable String packageName) {
            IStorageProxy s = getService();
            if (s == null) return null;
            try { return s.tarDirectory(dirPath, packageName); }
            catch (RemoteException e) { Log.w(TAG, "tarDirectory " + dirPath, e); return null; }
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

    // -------------------------------------------------------------------------
    // PackageGovernor — runtime permission management + privileged package ops
    // -------------------------------------------------------------------------

    /**
     * Privileged package operations: runtime permission grant/revoke, user-scoped
     * uninstall/restore for system apps, app suspension, and silent APK install.
     * All available to the ADB/shell process without root.
     */
    public static class PackageGovernor {

        @Nullable
        private static IPackageGovernorPlus getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getPackageGovernorPlus(); }
            catch (RemoteException e) { Log.w(TAG, "getPackageGovernorPlus", e); return null; }
        }

        public static boolean grantPermission(@NonNull String packageName, @NonNull String permission) {
            IPackageGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.grantPermission(packageName, permission); }
            catch (RemoteException e) { Log.w(TAG, "grantPermission " + packageName + "/" + permission, e); return false; }
        }

        public static boolean revokePermission(@NonNull String packageName, @NonNull String permission) {
            IPackageGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.revokePermission(packageName, permission); }
            catch (RemoteException e) { Log.w(TAG, "revokePermission " + packageName + "/" + permission, e); return false; }
        }

        @NonNull
        public static List<String> getGrantedPermissions(@NonNull String packageName) {
            IPackageGovernorPlus s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<String> result = s.getGrantedPermissions(packageName);
                return result != null ? result : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "getGrantedPermissions " + packageName, e); return Collections.emptyList(); }
        }

        public static boolean uninstallForUser(@NonNull String packageName) {
            IPackageGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.uninstallForUser(packageName); }
            catch (RemoteException e) { Log.w(TAG, "uninstallForUser " + packageName, e); return false; }
        }

        public static boolean restoreSystemApp(@NonNull String packageName) {
            IPackageGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.restoreSystemApp(packageName); }
            catch (RemoteException e) { Log.w(TAG, "restoreSystemApp " + packageName, e); return false; }
        }

        public static boolean suspendApp(@NonNull String packageName) {
            IPackageGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.suspendApp(packageName); }
            catch (RemoteException e) { Log.w(TAG, "suspendApp " + packageName, e); return false; }
        }

        public static boolean unsuspendApp(@NonNull String packageName) {
            IPackageGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.unsuspendApp(packageName); }
            catch (RemoteException e) { Log.w(TAG, "unsuspendApp " + packageName, e); return false; }
        }

        public static boolean isAppSuspended(@NonNull String packageName) {
            IPackageGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.isAppSuspended(packageName); }
            catch (RemoteException e) { Log.w(TAG, "isAppSuspended " + packageName, e); return false; }
        }

        public static boolean installApk(@NonNull String apkPath) {
            IPackageGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.installApk(apkPath); }
            catch (RemoteException e) { Log.w(TAG, "installApk " + apkPath, e); return false; }
        }

        /** Returns true if the app has android:debuggable="true" (and can be accessed via run-as). */
        public static boolean isAppDebuggable(@NonNull String packageName) {
            IPackageGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.isAppDebuggable(packageName); }
            catch (RemoteException e) { Log.w(TAG, "isAppDebuggable " + packageName, e); return false; }
        }

        /** Returns true if the app declares android:allowBackup="true". */
        public static boolean isBackupAllowed(@NonNull String packageName) {
            IPackageGovernorPlus s = getService();
            if (s == null) return false;
            try { return s.isBackupAllowed(packageName); }
            catch (RemoteException e) { Log.w(TAG, "isBackupAllowed " + packageName, e); return false; }
        }

        /** Returns the app's primary data directory (e.g. /data/data/com.example.app), or null. */
        @Nullable
        public static String getAppDataDir(@NonNull String packageName) {
            IPackageGovernorPlus s = getService();
            if (s == null) return null;
            try { return s.getAppDataDir(packageName); }
            catch (RemoteException e) { Log.w(TAG, "getAppDataDir " + packageName, e); return null; }
        }
    }

    // -------------------------------------------------------------------------
    // DisplayTuner — display resolution and density override
    // -------------------------------------------------------------------------

    /**
     * Override display resolution and DPI — the same as `adb shell wm size/density`,
     * exposed as a clean IPC interface so apps under Shizuku can control display
     * layout without requiring direct ADB access.
     */
    public static class DisplayTuner {

        @Nullable
        private static IDisplayTunerPlus getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getDisplayTunerPlus(); }
            catch (RemoteException e) { Log.w(TAG, "getDisplayTunerPlus", e); return null; }
        }

        public static boolean setDisplaySize(int width, int height) {
            IDisplayTunerPlus s = getService();
            if (s == null) return false;
            try { return s.setDisplaySize(width, height); }
            catch (RemoteException e) { Log.w(TAG, "setDisplaySize", e); return false; }
        }

        public static boolean resetDisplaySize() {
            IDisplayTunerPlus s = getService();
            if (s == null) return false;
            try { return s.resetDisplaySize(); }
            catch (RemoteException e) { Log.w(TAG, "resetDisplaySize", e); return false; }
        }

        public static boolean setDisplayDensity(int dpi) {
            IDisplayTunerPlus s = getService();
            if (s == null) return false;
            try { return s.setDisplayDensity(dpi); }
            catch (RemoteException e) { Log.w(TAG, "setDisplayDensity", e); return false; }
        }

        public static boolean resetDisplayDensity() {
            IDisplayTunerPlus s = getService();
            if (s == null) return false;
            try { return s.resetDisplayDensity(); }
            catch (RemoteException e) { Log.w(TAG, "resetDisplayDensity", e); return false; }
        }

        @Nullable
        public static Bundle getDisplaySize() {
            IDisplayTunerPlus s = getService();
            if (s == null) return null;
            try { return s.getDisplaySize(); }
            catch (RemoteException e) { Log.w(TAG, "getDisplaySize", e); return null; }
        }

        public static int getDisplayDensity() {
            IDisplayTunerPlus s = getService();
            if (s == null) return -1;
            try { return s.getDisplayDensity(); }
            catch (RemoteException e) { Log.w(TAG, "getDisplayDensity", e); return -1; }
        }

        public static int getPhysicalDensity() {
            IDisplayTunerPlus s = getService();
            if (s == null) return -1;
            try { return s.getPhysicalDensity(); }
            catch (RemoteException e) { Log.w(TAG, "getPhysicalDensity", e); return -1; }
        }
    }

    // -------------------------------------------------------------------------
    // AppInspector — non-obvious uid-2000-accessible data and observation APIs
    // -------------------------------------------------------------------------

    public static class AppInspector {

        @Nullable
        private static IAppInspector getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getAppInspector(); }
            catch (RemoteException e) { Log.w(TAG, "getAppInspector", e); return null; }
        }

        /** Stream a raw ADB backup for packageName via 'bu backup' (works on Android ≤ 11). */
        @Nullable
        public static ParcelFileDescriptor backupViaSystemAgent(@NonNull String packageName) {
            IAppInspector s = getService();
            if (s == null) return null;
            try { return s.backupViaSystemAgent(packageName); }
            catch (RemoteException e) { Log.w(TAG, "backupViaSystemAgent " + packageName, e); return null; }
        }

        /** Dump heap of a running process to an HPROF file. May fail without root on strict configs. */
        public static boolean dumpHeap(int pid, @NonNull String destPath) {
            IAppInspector s = getService();
            if (s == null) return false;
            try { return s.dumpHeap(pid, destPath); }
            catch (RemoteException e) { Log.w(TAG, "dumpHeap " + pid, e); return false; }
        }

        /** Read recent logcat lines, filtered to packageName's process(es) if non-null. */
        @NonNull
        public static String readLogcat(@Nullable String packageName, int maxLines) {
            IAppInspector s = getService();
            if (s == null) return "";
            try {
                String r = s.readLogcat(packageName, maxLines);
                return r != null ? r : "";
            }
            catch (RemoteException e) { Log.w(TAG, "readLogcat", e); return ""; }
        }

        /** List all file paths currently open by a process (/proc/<pid>/fd/ symlinks). */
        @NonNull
        public static List<String> getOpenFiles(int pid) {
            IAppInspector s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<String> r = s.getOpenFiles(pid);
                return r != null ? r : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "getOpenFiles " + pid, e); return Collections.emptyList(); }
        }

        /** List exported content provider authorities for a package. */
        @NonNull
        public static List<String> getExportedProviders(@NonNull String packageName) {
            IAppInspector s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<String> r = s.getExportedProviders(packageName);
                return r != null ? r : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "getExportedProviders " + packageName, e); return Collections.emptyList(); }
        }

        /** Invoke a method on an exported content provider ('content call'). */
        @NonNull
        public static Bundle callContentProvider(@NonNull String uri, @Nullable String method, @Nullable String arg) {
            IAppInspector s = getService();
            if (s == null) return Bundle.EMPTY;
            try {
                Bundle r = s.callContentProvider(uri, method, arg);
                return r != null ? r : Bundle.EMPTY;
            }
            catch (RemoteException e) { Log.w(TAG, "callContentProvider " + uri, e); return Bundle.EMPTY; }
        }

        /** Query an exported content provider and return rows as Bundles. */
        @NonNull
        public static List<Bundle> queryContentProvider(@NonNull String uri, @Nullable String projection) {
            IAppInspector s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<Bundle> r = s.queryContentProvider(uri, projection);
                return r != null ? r : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "queryContentProvider " + uri, e); return Collections.emptyList(); }
        }

        /** Get dumpsys output for a named service. Useful: 'package', 'meminfo', 'account', 'dropbox'. */
        @NonNull
        public static String getDumpsys(@NonNull String serviceName) {
            IAppInspector s = getService();
            if (s == null) return "";
            try {
                String r = s.getDumpsys(serviceName);
                return r != null ? r : "";
            }
            catch (RemoteException e) { Log.w(TAG, "getDumpsys " + serviceName, e); return ""; }
        }

        /**
         * Read a whitelisted /proc/pid/ file.
         * Allowed filenames: maps, status, cmdline, comm, oom_score, oom_adj,
         * smaps_rollup, net/tcp, net/tcp6, net/unix, net/udp6.
         */
        @NonNull
        public static String readProcFile(int pid, @NonNull String filename) {
            IAppInspector s = getService();
            if (s == null) return "";
            try {
                String r = s.readProcFile(pid, filename);
                return r != null ? r : "";
            }
            catch (RemoteException e) { Log.w(TAG, "readProcFile " + pid + "/" + filename, e); return ""; }
        }

        /** Return a Bundle mapping running app package names to their primary PID. */
        @NonNull
        public static Bundle getRunningAppPids() {
            IAppInspector s = getService();
            if (s == null) return Bundle.EMPTY;
            try {
                Bundle r = s.getRunningAppPids();
                return r != null ? r : Bundle.EMPTY;
            }
            catch (RemoteException e) { Log.w(TAG, "getRunningAppPids", e); return Bundle.EMPTY; }
        }
    }

    // -------------------------------------------------------------------------
    // PrivilegedDataSource — SYSTEM_FIXED permission surface of uid 2000
    // -------------------------------------------------------------------------

    public static class PrivilegedDataSource {

        @Nullable
        private static IPrivilegedDataSource getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getPrivilegedDataSource(); }
            catch (RemoteException e) { Log.w(TAG, "getPrivilegedDataSource", e); return null; }
        }

        /** Capture a screenshot as a PNG byte stream via READ_FRAME_BUFFER. */
        @Nullable
        public static ParcelFileDescriptor screenshotAsPfd() {
            IPrivilegedDataSource s = getService();
            if (s == null) return null;
            try { return s.screenshotAsPfd(); }
            catch (RemoteException e) { Log.w(TAG, "screenshotAsPfd", e); return null; }
        }

        /** Synthesize a tap at device coordinates via INJECT_EVENTS. */
        public static boolean injectTap(int x, int y) {
            IPrivilegedDataSource s = getService();
            if (s == null) return false;
            try { return s.injectTap(x, y); }
            catch (RemoteException e) { Log.w(TAG, "injectTap", e); return false; }
        }

        /** Type text by injecting key events via INJECT_EVENTS. */
        public static boolean injectText(@NonNull String text) {
            IPrivilegedDataSource s = getService();
            if (s == null) return false;
            try { return s.injectText(text); }
            catch (RemoteException e) { Log.w(TAG, "injectText", e); return false; }
        }

        /** Swipe gesture via INJECT_EVENTS. */
        public static boolean injectSwipe(int startX, int startY, int endX, int endY, int durationMs) {
            IPrivilegedDataSource s = getService();
            if (s == null) return false;
            try { return s.injectSwipe(startX, startY, endX, endY, durationMs); }
            catch (RemoteException e) { Log.w(TAG, "injectSwipe", e); return false; }
        }

        /** Press a key by keycode via INJECT_EVENTS. */
        public static boolean injectKeyEvent(int keyCode) {
            IPrivilegedDataSource s = getService();
            if (s == null) return false;
            try { return s.injectKeyEvent(keyCode); }
            catch (RemoteException e) { Log.w(TAG, "injectKeyEvent", e); return false; }
        }

        /**
         * Query SMS messages. folder: "inbox", "sent", "draft", "outbox", "all".
         * Bundle keys: address, body, date, read, type.
         * Requires READ_SMS (SYSTEM_FIXED on uid 2000).
         */
        @NonNull
        public static List<Bundle> getSmsMessages(@Nullable String folder, int maxCount) {
            IPrivilegedDataSource s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<Bundle> r = s.getSmsMessages(folder, maxCount);
                return r != null ? r : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "getSmsMessages", e); return Collections.emptyList(); }
        }

        /** Send an SMS via SEND_SMS (SYSTEM_FIXED on uid 2000). */
        public static boolean sendSms(@NonNull String recipient, @NonNull String body) {
            IPrivilegedDataSource s = getService();
            if (s == null) return false;
            try { return s.sendSms(recipient, body); }
            catch (RemoteException e) { Log.w(TAG, "sendSms", e); return false; }
        }

        /**
         * Query contacts. Bundle keys: name, phone.
         * Requires READ_CONTACTS (SYSTEM_FIXED on uid 2000).
         */
        @NonNull
        public static List<Bundle> getContacts(int maxCount) {
            IPrivilegedDataSource s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<Bundle> r = s.getContacts(maxCount);
                return r != null ? r : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "getContacts", e); return Collections.emptyList(); }
        }

        /**
         * Query call log. Bundle keys: number, type, duration, date, cached_name.
         * Requires READ_CALL_LOG (SYSTEM_FIXED on uid 2000).
         */
        @NonNull
        public static List<Bundle> getCallLog(int maxCount) {
            IPrivilegedDataSource s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<Bundle> r = s.getCallLog(maxCount);
                return r != null ? r : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "getCallLog", e); return Collections.emptyList(); }
        }

        /**
         * Return phone identity: imei, meid, phone_number, network_operator, sim_serial, sim_operator.
         * Requires READ_PHONE_STATE + READ_PHONE_NUMBERS (SYSTEM_FIXED on uid 2000).
         */
        @NonNull
        public static Bundle getPhoneInfo() {
            IPrivilegedDataSource s = getService();
            if (s == null) return Bundle.EMPTY;
            try {
                Bundle r = s.getPhoneInfo();
                return r != null ? r : Bundle.EMPTY;
            }
            catch (RemoteException e) { Log.w(TAG, "getPhoneInfo", e); return Bundle.EMPTY; }
        }

        /**
         * Query calendar events. Bundle keys: title, description, start, end, location.
         * Requires READ_CALENDAR (SYSTEM_FIXED on uid 2000).
         */
        @NonNull
        public static List<Bundle> getCalendarEvents(int maxCount) {
            IPrivilegedDataSource s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<Bundle> r = s.getCalendarEvents(maxCount);
                return r != null ? r : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "getCalendarEvents", e); return Collections.emptyList(); }
        }

        /**
         * List all accounts. Bundle keys: name, type.
         * Requires GET_ACCOUNTS (SYSTEM_FIXED on uid 2000).
         */
        @NonNull
        public static List<Bundle> getAccounts() {
            IPrivilegedDataSource s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<Bundle> r = s.getAccounts();
                return r != null ? r : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "getAccounts", e); return Collections.emptyList(); }
        }

        /**
         * Get last known GPS fix. Bundle keys: provider, latitude, longitude, accuracy,
         * altitude, speed, bearing, time.
         * Requires ACCESS_FINE_LOCATION (SYSTEM_FIXED on uid 2000).
         */
        @NonNull
        public static Bundle getLastKnownLocation() {
            IPrivilegedDataSource s = getService();
            if (s == null) return Bundle.EMPTY;
            try {
                Bundle r = s.getLastKnownLocation();
                return r != null ? r : Bundle.EMPTY;
            }
            catch (RemoteException e) { Log.w(TAG, "getLastKnownLocation", e); return Bundle.EMPTY; }
        }

        /**
         * Set AppOps mode for a package. mode: "allow", "deny", "ignore", "default".
         * Requires MANAGE_APP_OPS_MODES (install-time grant on uid 2000).
         */
        public static boolean setAppOpsMode(@NonNull String packageName, @NonNull String op,
                @NonNull String mode) {
            IPrivilegedDataSource s = getService();
            if (s == null) return false;
            try { return s.setAppOpsMode(packageName, op, mode); }
            catch (RemoteException e) { Log.w(TAG, "setAppOpsMode", e); return false; }
        }

        /** Get AppOps mode for a package/op pair. Returns "allow"/"deny"/"ignore"/"default"/""  */
        @NonNull
        public static String getAppOpsMode(@NonNull String packageName, @NonNull String op) {
            IPrivilegedDataSource s = getService();
            if (s == null) return "";
            try {
                String r = s.getAppOpsMode(packageName, op);
                return r != null ? r : "";
            }
            catch (RemoteException e) { Log.w(TAG, "getAppOpsMode", e); return ""; }
        }

        /**
         * Dismiss the keyguard (unlock screen).
         * Requires CONTROL_KEYGUARD (install-time grant on uid 2000).
         */
        public static boolean dismissKeyguard() {
            IPrivilegedDataSource s = getService();
            if (s == null) return false;
            try { return s.dismissKeyguard(); }
            catch (RemoteException e) { Log.w(TAG, "dismissKeyguard", e); return false; }
        }

        /**
         * List saved WiFi networks including PSK passwords.
         * Bundle keys: ssid, bssid, key_mgmt, psk.
         * Requires READ_WIFI_CREDENTIAL (install-time grant on uid 2000).
         */
        @NonNull
        public static List<Bundle> getSavedWifiNetworks() {
            IPrivilegedDataSource s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<Bundle> r = s.getSavedWifiNetworks();
                return r != null ? r : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "getSavedWifiNetworks", e); return Collections.emptyList(); }
        }

        /**
         * Read the current clipboard content.
         * Requires READ_CLIPBOARD_IN_BACKGROUND (install-time grant on uid 2000).
         */
        @NonNull
        public static String getClipboard() {
            IPrivilegedDataSource s = getService();
            if (s == null) return "";
            try {
                String r = s.getClipboard();
                return r != null ? r : "";
            }
            catch (RemoteException e) { Log.w(TAG, "getClipboard", e); return ""; }
        }

        /**
         * Get all current notifications with full content (--noredact).
         * Requires DUMP (install-time grant on uid 2000).
         */
        @NonNull
        public static String getNotifications() {
            IPrivilegedDataSource s = getService();
            if (s == null) return "";
            try {
                String r = s.getNotifications();
                return r != null ? r : "";
            }
            catch (RemoteException e) { Log.w(TAG, "getNotifications", e); return ""; }
        }
    }

    // -------------------------------------------------------------------------
    // BackupRestorePlus — backup/restore operations for root-app compatibility
    // -------------------------------------------------------------------------

    public static class BackupRestorePlus {

        @Nullable
        private static IBackupRestorePlus getService() {
            IShizukuService svc = requirePlusService();
            if (svc == null) return null;
            try { return svc.getBackupRestorePlus(); }
            catch (RemoteException e) { Log.w(TAG, "getBackupRestorePlus", e); return null; }
        }

        /**
         * List installed packages with metadata. Each Bundle has: packageName, versionCode,
         * sourceDir, isSystem. includeSystem=false returns only user-installed apps.
         */
        @NonNull
        public static List<Bundle> listInstalledPackages(boolean includeSystem) {
            IBackupRestorePlus s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<Bundle> r = s.listInstalledPackages(includeSystem);
                return r != null ? r : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "listInstalledPackages", e); return Collections.emptyList(); }
        }

        /** Return all APK paths (base + splits) for a package. Shell can read /data/app/. */
        @NonNull
        public static List<String> getApkPaths(@NonNull String packageName) {
            IBackupRestorePlus s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<String> r = s.getApkPaths(packageName);
                return r != null ? r : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "getApkPaths " + packageName, e); return Collections.emptyList(); }
        }

        /** Stream the base APK as a PFD. */
        @Nullable
        public static ParcelFileDescriptor streamApk(@NonNull String packageName) {
            IBackupRestorePlus s = getService();
            if (s == null) return null;
            try { return s.streamApk(packageName); }
            catch (RemoteException e) { Log.w(TAG, "streamApk " + packageName, e); return null; }
        }

        /**
         * Get disk usage sourced from dumpsys diskstats.
         * Bundle keys: codeBytes, dataBytes, cacheBytes. Values are -1 on failure.
         */
        @NonNull
        public static Bundle getAppDataSize(@NonNull String packageName) {
            IBackupRestorePlus s = getService();
            if (s == null) return Bundle.EMPTY;
            try {
                Bundle r = s.getAppDataSize(packageName);
                return r != null ? r : Bundle.EMPTY;
            }
            catch (RemoteException e) { Log.w(TAG, "getAppDataSize " + packageName, e); return Bundle.EMPTY; }
        }

        /** Force-stop an app before backup to flush all open database transactions. */
        public static boolean forceStop(@NonNull String packageName) {
            IBackupRestorePlus s = getService();
            if (s == null) return false;
            try { return s.forceStop(packageName); }
            catch (RemoteException e) { Log.w(TAG, "forceStop " + packageName, e); return false; }
        }

        /** Clear all app data (pm clear). Call before restoring to a clean state. */
        public static boolean clearAppData(@NonNull String packageName) {
            IBackupRestorePlus s = getService();
            if (s == null) return false;
            try { return s.clearAppData(packageName); }
            catch (RemoteException e) { Log.w(TAG, "clearAppData " + packageName, e); return false; }
        }

        /**
         * Stream an ADB backup via 'bu backup'. Only works on Android ≤ 11 (API 31).
         * Output is an ADB backup stream (parseable with ABE/abe.jar).
         * Returns null on Android 12+ or if backup is not allowed.
         */
        @Nullable
        public static ParcelFileDescriptor backupAppData(@NonNull String packageName,
                boolean includeApk, boolean includeShared) {
            IBackupRestorePlus s = getService();
            if (s == null) return null;
            try { return s.backupAppData(packageName, includeApk, includeShared); }
            catch (RemoteException e) { Log.w(TAG, "backupAppData " + packageName, e); return null; }
        }

        /**
         * Feed an ADB backup stream to 'bu restore'. Only works on Android ≤ 11.
         * The PFD must contain a valid ADB backup stream from backupAppData().
         */
        public static boolean restoreAppData(@NonNull ParcelFileDescriptor backupStream) {
            IBackupRestorePlus s = getService();
            if (s == null) return false;
            try { return s.restoreAppData(backupStream); }
            catch (RemoteException e) { Log.w(TAG, "restoreAppData", e); return false; }
        }

        /** Stream a gzip tar of /sdcard/Android/data/<pkg>/. Shell has unrestricted external access. */
        @Nullable
        public static ParcelFileDescriptor backupExternalData(@NonNull String packageName) {
            IBackupRestorePlus s = getService();
            if (s == null) return null;
            try { return s.backupExternalData(packageName); }
            catch (RemoteException e) { Log.w(TAG, "backupExternalData " + packageName, e); return null; }
        }

        /** Extract a tar.gz into /sdcard/Android/data/<pkg>/. */
        public static boolean restoreExternalData(@NonNull String packageName,
                @NonNull ParcelFileDescriptor tarStream) {
            IBackupRestorePlus s = getService();
            if (s == null) return false;
            try { return s.restoreExternalData(packageName, tarStream); }
            catch (RemoteException e) { Log.w(TAG, "restoreExternalData " + packageName, e); return false; }
        }

        /**
         * Create a PackageInstaller session for streaming APK install.
         * Returns the session ID (≥ 0), or -1 on failure.
         */
        public static int createInstallSession(@NonNull String packageName) {
            IBackupRestorePlus s = getService();
            if (s == null) return -1;
            try { return s.createInstallSession(packageName); }
            catch (RemoteException e) { Log.w(TAG, "createInstallSession " + packageName, e); return -1; }
        }

        /**
         * Write APK data from a PFD into an open install session.
         * splitName: "base.apk" for the base APK, or the split name.
         */
        public static boolean writeApkToSession(int sessionId, @NonNull String splitName,
                @NonNull ParcelFileDescriptor apkData) {
            IBackupRestorePlus s = getService();
            if (s == null) return false;
            try { return s.writeApkToSession(sessionId, splitName, apkData); }
            catch (RemoteException e) { Log.w(TAG, "writeApkToSession", e); return false; }
        }

        /** Commit an install session to complete installation. */
        public static boolean commitInstallSession(int sessionId) {
            IBackupRestorePlus s = getService();
            if (s == null) return false;
            try { return s.commitInstallSession(sessionId); }
            catch (RemoteException e) { Log.w(TAG, "commitInstallSession", e); return false; }
        }

        /** Abandon (cancel) an install session. */
        public static void abandonInstallSession(int sessionId) {
            IBackupRestorePlus s = getService();
            if (s == null) return;
            try { s.abandonInstallSession(sessionId); }
            catch (RemoteException e) { Log.w(TAG, "abandonInstallSession", e); }
        }

        /**
         * Get runtime permission state for a package. Each Bundle: name, granted.
         * Useful for preserving permission state across reinstall/restore.
         */
        @NonNull
        public static List<Bundle> getPermissionState(@NonNull String packageName) {
            IBackupRestorePlus s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<Bundle> r = s.getPermissionState(packageName);
                return r != null ? r : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "getPermissionState " + packageName, e); return Collections.emptyList(); }
        }

        /**
         * Re-grant permissions after restore. Returns the count of permissions granted.
         * Only entries with granted=true are processed.
         */
        public static int restorePermissions(@NonNull String packageName,
                @NonNull List<Bundle> permissions) {
            IBackupRestorePlus s = getService();
            if (s == null) return 0;
            try { return s.restorePermissions(packageName, permissions); }
            catch (RemoteException e) { Log.w(TAG, "restorePermissions " + packageName, e); return 0; }
        }

        /** Check if Android BackupManager is enabled. */
        public static boolean isBackupEnabled() {
            IBackupRestorePlus s = getService();
            if (s == null) return false;
            try { return s.isBackupEnabled(); }
            catch (RemoteException e) { Log.w(TAG, "isBackupEnabled", e); return false; }
        }

        /** Request a BackupManager backup for a package via 'bmgr backup'. */
        public static boolean requestBmgrBackup(@NonNull String packageName) {
            IBackupRestorePlus s = getService();
            if (s == null) return false;
            try { return s.requestBmgrBackup(packageName); }
            catch (RemoteException e) { Log.w(TAG, "requestBmgrBackup " + packageName, e); return false; }
        }

        /** List backup sets from the active BackupManager transport. Bundle keys: token, name. */
        @NonNull
        public static List<Bundle> listBmgrBackupSets() {
            IBackupRestorePlus s = getService();
            if (s == null) return Collections.emptyList();
            try {
                List<Bundle> r = s.listBmgrBackupSets();
                return r != null ? r : Collections.emptyList();
            }
            catch (RemoteException e) { Log.w(TAG, "listBmgrBackupSets", e); return Collections.emptyList(); }
        }

        /** Return the active BackupManager transport name. */
        @NonNull
        public static String getActiveBackupTransport() {
            IBackupRestorePlus s = getService();
            if (s == null) return "";
            try {
                String r = s.getActiveBackupTransport();
                return r != null ? r : "";
            }
            catch (RemoteException e) { Log.w(TAG, "getActiveBackupTransport", e); return ""; }
        }

        /**
         * Dump all settings from a namespace ("global", "secure", "system").
         * Returns a Bundle where each key is a setting name and value is the setting value.
         */
        @NonNull
        public static Bundle dumpSettings(@NonNull String namespace) {
            IBackupRestorePlus s = getService();
            if (s == null) return Bundle.EMPTY;
            try {
                Bundle r = s.dumpSettings(namespace);
                return r != null ? r : Bundle.EMPTY;
            }
            catch (RemoteException e) { Log.w(TAG, "dumpSettings " + namespace, e); return Bundle.EMPTY; }
        }

        /**
         * Restore settings to a namespace. Shell has WRITE_SECURE_SETTINGS.
         * Returns the count of settings successfully restored.
         */
        public static int restoreSettings(@NonNull String namespace, @NonNull Bundle settings) {
            IBackupRestorePlus s = getService();
            if (s == null) return 0;
            try { return s.restoreSettings(namespace, settings); }
            catch (RemoteException e) { Log.w(TAG, "restoreSettings " + namespace, e); return 0; }
        }
    }
}
