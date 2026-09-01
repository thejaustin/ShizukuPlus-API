package af.shizuku.server;

/**
 * Privileged package operations accessible to the ADB/shell process (uid 2000).
 * All methods that take a packageName operate on the current user (user 0 or the
 * calling user) unless otherwise noted.
 */
interface IPackageGovernorPlus {

    /**
     * Grant a declared runtime permission to an app without a user prompt.
     * The permission must be listed in the app's manifest; this call cannot
     * grant permissions the app did not declare.
     */
    boolean grantPermission(String packageName, String permission);

    /**
     * Revoke a runtime permission from an app.
     */
    boolean revokePermission(String packageName, String permission);

    /**
     * Return the list of runtime permissions currently granted to a package.
     */
    List<String> getGrantedPermissions(String packageName);

    /**
     * Remove a package for the current user only (soft-debloat).
     * System apps are removed only for the user; user-installed apps are
     * fully uninstalled. Reversible via restoreSystemApp() for system apps.
     */
    boolean uninstallForUser(String packageName);

    /**
     * Re-enable a system app that was previously removed for this user via
     * uninstallForUser(). Has no effect on user-installed apps.
     */
    boolean restoreSystemApp(String packageName);

    /**
     * Suspend an app for the current user. The app appears greyed-out in the
     * launcher and cannot be launched. Unlike disabling, all app data is preserved.
     */
    boolean suspendApp(String packageName);

    /**
     * Unsuspend an app, restoring it to normal operation.
     */
    boolean unsuspendApp(String packageName);

    /**
     * Check whether an app is currently suspended for the current user.
     */
    boolean isAppSuspended(String packageName);

    /**
     * Silently install an APK from a file path accessible to the server process.
     * Grants all declared permissions automatically (-g flag).
     * Returns false if the path is null/blank or install fails.
     */
    boolean installApk(String apkPath);

    /**
     * Check if an app is debuggable (android:debuggable="true" in its manifest).
     * Debuggable apps can be accessed via run-as, enabling full data directory read.
     */
    boolean isAppDebuggable(String packageName);

    /**
     * Check if an app has android:allowBackup="true" in its manifest.
     * Apps with backup allowed can be backed up via BackupManager.
     */
    boolean isBackupAllowed(String packageName);

    /**
     * Return the primary data directory path for a package (e.g. /data/data/com.example.app).
     * Returns null if the package is not installed.
     */
    String getAppDataDir(String packageName);
}
