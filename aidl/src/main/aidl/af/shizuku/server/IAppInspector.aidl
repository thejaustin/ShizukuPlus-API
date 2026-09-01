package af.shizuku.server;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/**
 * Non-obvious privileged observation and data-access capabilities available to uid 2000 (shell).
 * Each method here represents something developers assume requires root, but doesn't on some
 * Android versions or configurations. All methods are side-effect-free (read-only).
 */
interface IAppInspector {

    /**
     * Stream an app's backup data using the system 'bu backup' command.
     * On Android ≤ 11, shell uid can call this without the BACKUP permission — it was
     * the original mechanism for 'adb backup' and bypasses normal permission enforcement.
     * On Android 12+, this returns null because the permission gate was tightened.
     * The returned PFD carries a raw ADB backup stream (gzip + tar, parseable with Android Backup Extractor).
     * Requires allowBackup=true in the target app's manifest.
     */
    ParcelFileDescriptor backupViaSystemAgent(String packageName);

    /**
     * Dump the JVM heap of a running process to a local file (HPROF format).
     * On standard AOSP, 'am dumpheap <pid>' from shell succeeds for processes where the caller
     * is in the same package — but on several Samsung/Xiaomi/OEM builds with relaxed policy,
     * shell can heap-dump ANY running user process. Returns false if SELinux blocks it.
     */
    boolean dumpHeap(int pid, String destPath);

    /**
     * Capture recent logcat lines, optionally filtered to a package's process(es).
     * Shell uid 2000 has implicit READ_LOGS access — no runtime permission needed.
     * This captures everything an app has written to logcat including production releases
     * that accidentally log sensitive data (tokens, SQL queries, API payloads).
     * Returns at most maxLines lines (clamped to 10,000).
     */
    String readLogcat(String packageName, int maxLines);

    /**
     * List all file paths currently open by a process, resolved from /proc/<pid>/fd/ symlinks.
     * Shell can traverse /proc/<pid>/fd/ for any user process (SELinux allows this in all
     * stock AOSP builds). This reveals: open SQLite databases, SharedPreferences XML files,
     * OAT files, open sockets — the exact file paths, even for paths we can't open directly.
     */
    List<String> getOpenFiles(int pid);

    /**
     * List exported content provider authorities for a package.
     * Exported providers are accessible to any caller including shell — discovering them
     * via 'pm dump' lets backup apps know what data is systematically queryable.
     */
    List<String> getExportedProviders(String packageName);

    /**
     * Invoke an arbitrary method on an exported content provider via 'content call'.
     * Many apps implement undocumented provider methods for inter-process communication
     * within their app suite — these respond to method calls from any uid including shell.
     * Returns the result Bundle, or an empty Bundle on failure.
     */
    Bundle callContentProvider(String uri, String method, String arg);

    /**
     * Query an exported content provider and return rows as Bundles.
     * Equivalent to 'content query --uri <uri> [--projection <cols>]'.
     * Projection is a comma-separated list of column names, or null for all columns.
     */
    List<Bundle> queryContentProvider(String uri, String projection);

    /**
     * Get the full dumpsys output for a named system service.
     * Shell can call 'dumpsys <service>' for most services. Particularly useful:
     * 'package', 'activity', 'meminfo', 'netstats', 'alarm', 'account', 'location', 'dropbox'.
     * Service name must be alphanumeric to prevent injection.
     */
    String getDumpsys(String serviceName);

    /**
     * Read a specific file from /proc/<pid>/ using a whitelist of safe filenames.
     * Allowed: maps, status, cmdline, comm, oom_score, net/tcp, net/tcp6, net/unix, smaps_rollup.
     * '/proc/<pid>/maps' reveals all memory-mapped files (databases, open libraries) by path.
     * '/proc/<pid>/net/tcp6' shows active TCP connections (IP:port) without packet contents.
     */
    String readProcFile(int pid, String filename);

    /**
     * Return a Bundle mapping running app package names to their primary PID.
     * Parsed from 'ps -A -o PID,NAME'. Only entries with dots in the name (package names)
     * are included. Use alongside getOpenFiles() to discover what a specific app has open.
     */
    Bundle getRunningAppPids();
}
