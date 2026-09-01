package af.shizuku.server;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/**
 * Backup and restore operations accessible from uid 2000 (ADB/shell).
 *
 * This interface bridges the gap between root-dependent backup apps (Swift Backup,
 * Neo Backup, Titanium Backup) and Shizuku's shell-level privilege. It covers every
 * backup/restore primitive that uid 2000 can perform without root:
 *
 *   - APK streaming (shell can read /data/app/)
 *   - ADB backup/restore via 'bu backup' / 'bu restore' (Android ≤ 11)
 *   - External storage data (/sdcard/Android/data/<pkg>/) — shell has full access
 *   - BackupManager control via 'bmgr' (shell can call it)
 *   - Settings dump/restore via 'settings list' / 'settings put'
 *   - Package inventory and permission state
 *   - Install APKs from a client-supplied stream (pm install-create/write/commit)
 */
interface IBackupRestorePlus {

    // ── Package Inventory ─────────────────────────────────────────────────────

    /**
     * List installed packages with backup-relevant metadata.
     * Bundle keys per entry: packageName, versionName, versionCode, isSystem,
     * isDebuggable, allowBackup, dataDir, sourceDir, splitSourceDirs (String[]),
     * uid, targetSdk.
     * If includeSystem is false, only user-installed apps are returned.
     */
    List<Bundle> listInstalledPackages(boolean includeSystem);

    /**
     * Return all APK paths for a package: base APK + all split APKs.
     * Paths are in /data/app/ which shell can read on stock AOSP.
     * Returns an empty list if the package is not installed.
     */
    List<String> getApkPaths(String packageName);

    /**
     * Stream the base APK as a PFD. Equivalent to opening the sourceDir path
     * returned by getApkPaths(), but avoids an extra round-trip.
     */
    ParcelFileDescriptor streamApk(String packageName);

    /**
     * Return the approximate data size for a package in bytes, sourced from
     * 'dumpsys diskstats'. Avoids needing direct /data/data/ access.
     * Bundle keys: codeBytes, dataBytes, cacheBytes.
     * Returns an empty Bundle if the package is not found in diskstats output.
     */
    Bundle getAppDataSize(String packageName);

    // ── Pre-backup / Pre-restore Utilities ───────────────────────────────────

    /**
     * Force-stop an app before backup to ensure all open database transactions
     * are committed and files are flushed to disk.
     * Uses 'am force-stop'. Shell has FORCE_STOP_PACKAGES.
     */
    boolean forceStop(String packageName);

    /**
     * Clear all app data (equivalent to Settings → App → Clear Data).
     * Uses 'pm clear'. Necessary before restoring app data to a clean state.
     * Returns false if the operation is denied or the package does not exist.
     */
    boolean clearAppData(String packageName);

    // ── ADB Backup / Restore (Android ≤ 11) ──────────────────────────────────

    /**
     * Stream an ADB backup for the given package via 'bu backup'.
     * The output is a raw ADB backup stream: "ANDROID BACKUP" header + gzip'd tar.
     * Parse with the Android Backup Extractor (ABE) or abe.jar.
     *
     * includeApk: pass true to include the APK in the backup stream.
     * includeShared: pass true to include shared storage data.
     *
     * On Android ≤ 11: works for any app with allowBackup=true.
     * On Android 12+: returns null (permission gate was tightened for uid 2000).
     */
    ParcelFileDescriptor backupAppData(String packageName, boolean includeApk, boolean includeShared);

    /**
     * Feed an ADB backup stream to 'bu restore' via stdin.
     * The PFD must contain a valid ADB backup stream previously produced by backupAppData()
     * or 'adb backup'. Uses 'bu restore' which bypasses normal BACKUP permission for uid 2000.
     *
     * On Android ≤ 11: works without BACKUP permission.
     * On Android 12+: returns false.
     * Returns true if bu restore exits 0.
     */
    boolean restoreAppData(in ParcelFileDescriptor backupStream);

    // ── External Storage Backup / Restore ─────────────────────────────────────

    /**
     * Stream a gzip-compressed tar archive of /sdcard/Android/data/<pkg>/.
     * Shell has unrestricted read access to external storage on all Android versions.
     * Returns null if the directory does not exist.
     */
    ParcelFileDescriptor backupExternalData(String packageName);

    /**
     * Extract a gzip-compressed tar stream into /sdcard/Android/data/<pkg>/.
     * The directory is created if it doesn't exist.
     * The PFD must contain a tar.gz produced by backupExternalData() or equivalent.
     * Returns false if extraction fails.
     */
    boolean restoreExternalData(String packageName, in ParcelFileDescriptor tarStream);

    // ── Streaming APK Install ─────────────────────────────────────────────────

    /**
     * Create a PackageInstaller session for a streaming APK install.
     * Uses 'pm install-create -g --multi-package'.
     * Returns the numeric session ID, or -1 on failure.
     * Shell has INSTALL_PACKAGES (install-time grant).
     */
    int createInstallSession(String packageName);

    /**
     * Write APK bytes from a PFD into an open install session.
     * Uses 'pm install-write <session-id> base.apk -'.
     * splitName: "base" for the base APK, or the split name for split APKs.
     * Returns false if the write fails.
     */
    boolean writeApkToSession(int sessionId, String splitName, in ParcelFileDescriptor apkData);

    /**
     * Commit an install session, completing the installation.
     * Uses 'pm install-commit <session-id>'.
     * Returns true if pm exits 0 (installation succeeded).
     */
    boolean commitInstallSession(int sessionId);

    /**
     * Abandon (cancel) an install session.
     * Uses 'pm install-abandon <session-id>'.
     */
    void abandonInstallSession(int sessionId);

    // ── Permission State ──────────────────────────────────────────────────────

    /**
     * Return the full permission state for a package.
     * Bundle keys per entry: name (String), granted (boolean).
     * Only runtime permissions are included (not install-time).
     * Useful for preserving permission state across reinstall.
     */
    List<Bundle> getPermissionState(String packageName);

    /**
     * Re-grant a set of runtime permissions to a package after restore.
     * Only entries with granted=true are processed; others are skipped.
     * Uses 'pm grant' for each permission.
     * Returns the count of permissions successfully granted.
     */
    int restorePermissions(String packageName, in List<Bundle> permissions);

    // ── BackupManager (bmgr) ──────────────────────────────────────────────────

    /**
     * Return true if the Android BackupManager is enabled.
     * Parses output of 'bmgr enabled'.
     */
    boolean isBackupEnabled();

    /**
     * Request a BackupManager backup for the given package.
     * Uses 'bmgr backup <pkg>'. This schedules a backup via the active transport
     * (Google account backup, local transport, etc.).
     * Returns false if bmgr is not available or the request fails.
     */
    boolean requestBmgrBackup(String packageName);

    /**
     * List available backup sets from the active BackupManager transport.
     * Uses 'bmgr list sets'.
     * Bundle keys per entry: token (String), name (String).
     * Returns an empty list if no sets are available or bmgr is disabled.
     */
    List<Bundle> listBmgrBackupSets();

    /**
     * Return the active BackupManager transport name.
     * Parses 'bmgr list transports'.
     */
    String getActiveBackupTransport();

    // ── Settings Backup / Restore ─────────────────────────────────────────────

    /**
     * Dump all key=value pairs from a settings namespace.
     * namespace: "global", "secure", or "system".
     * Uses 'settings list <namespace>'. Shell has WRITE_SECURE_SETTINGS for restore.
     * Returns a Bundle where each key is a setting name and value is the setting value.
     */
    Bundle dumpSettings(String namespace);

    /**
     * Restore a settings namespace from key=value pairs.
     * Uses 'settings put <namespace> <key> <value>' for each entry.
     * Shell has WRITE_SECURE_SETTINGS (install-time grant) for secure/global namespaces.
     * Returns the count of settings successfully restored.
     */
    int restoreSettings(String namespace, in Bundle settings);
}
