package af.shizuku.server;

import android.os.ParcelFileDescriptor;

/**
 * Enables backup/restore of non-debuggable app data on Android 12+ without root.
 *
 * The "temp-debug" technique:
 *   1. prepareTempDebug(pkg) — saves the original APK to /data/local/tmp/,
 *      patches android:debuggable="true" into its binary manifest, signs the
 *      patched APK with an ephemeral V1+V2 key, uninstalls (keeping data),
 *      and reinstalls the patched version. After this call, run-as <pkg> works.
 *   2. streamDataDir(pkg)  / restoreDataDir(pkg, pfd)
 *      — run-as <pkg> tar for backup and restore.
 *   3. restoreOriginal(pkg) — uninstalls (keeping data) and reinstalls the
 *      original APK from the saved copy. App data survives both round-trips.
 *
 * Cross-device use:
 *   Device A: prepareTempDebug → streamDataDir → restoreOriginal → done.
 *   Device B: prepareTempDebug → restoreDataDir → restoreOriginal → done.
 *   Original APK (from streamOriginalApk) must be transferred separately.
 */
interface IApkPatcher {

    /**
     * Patch the installed APK to be debuggable and reinstall it, preserving
     * all app data. After this succeeds, run-as <pkg> works.
     *
     * Internally:
     *   • saves /data/app/<pkg>/base.apk to /data/local/tmp/splus_td/<pkg>_orig.apk
     *   • patches android:debuggable="true" in the binary manifest
     *   • signs with V1+V2 using an ephemeral key
     *   • pm uninstall --user 0 -k <pkg>
     *   • pm install <patched.apk>
     *
     * Returns false if the package is not found, patching fails, or install fails.
     * Idempotent: calling twice is safe (returns true immediately if already patched).
     */
    boolean prepareTempDebug(String packageName);

    /**
     * Stream the app's /data/data/<pkg>/ as a gzip-compressed tar archive.
     * Only valid after prepareTempDebug(). The tar is produced by:
     *   run-as <pkg> tar -czf - -C /data/data/<pkg> .
     * Returns null if the app is not in temp-debug state or run-as fails.
     */
    ParcelFileDescriptor streamDataDir(String packageName);

    /**
     * Restore a gzip-compressed tar into /data/data/<pkg>/.
     * Only valid after prepareTempDebug(). Runs:
     *   run-as <pkg> tar -xzf - -C /data/data/<pkg>
     * Returns false if run-as fails or the tar extraction errors.
     */
    boolean restoreDataDir(String packageName, in ParcelFileDescriptor tarStream);

    /**
     * Restore the original APK after a temp-debug session.
     *   • pm uninstall --user 0 -k <pkg>   (data survives)
     *   • pm install <original.apk>          (original key, fresh install)
     * Removes temp files from /data/local/tmp/splus_td/.
     * Returns false if no original APK was saved or reinstall fails.
     */
    boolean restoreOriginal(String packageName);

    /**
     * Stream the original APK (saved during prepareTempDebug) as a PFD.
     * Useful for including the APK in a cross-device backup set.
     * Returns null if prepareTempDebug has not been called yet.
     */
    ParcelFileDescriptor streamOriginalApk(String packageName);

    /**
     * Return true if prepareTempDebug() has been called and restoreOriginal()
     * has not yet been called for this package.
     */
    boolean isTempDebugging(String packageName);

    /**
     * Restore all packages left in temp-debug state (e.g., after a server crash).
     * Calls restoreOriginal() for each package in the temp-debug set.
     * Should be called on server startup.
     */
    void cleanupAllTempDebug();
}
