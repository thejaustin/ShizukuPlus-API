package af.shizuku.server;

import android.os.ParcelFileDescriptor;

interface IStorageProxy {
    /**
     * Open a file at the given path with the specified mode.
     * Requires biometric or user confirmation for sensitive paths.
     */
    ParcelFileDescriptor openFile(String path, int mode);

    /**
     * Check if a path exists.
     */
    boolean exists(String path);

    /**
     * List files in a directory.
     */
    List<String> listFiles(String path);

    /**
     * Delete a file or empty directory at the given path.
     */
    boolean delete(String path);

    /**
     * Get file information.
     */
    Bundle getFileInfo(String path);

    /**
     * Create a directory at the given path.
     */
    boolean mkdir(String path);

    /**
     * Copy a file from srcPath to destPath server-side.
     * Falls back to run-as <pkg> for /data/data/<pkg>/ paths on debuggable apps.
     * Returns false if the source is inaccessible or the copy fails.
     */
    boolean copyFile(String srcPath, String destPath);

    /**
     * Open a file at the given content:// URI using the shell 'content read' command.
     * Useful for reading files from app FileProviders without direct filesystem access.
     * Returns null if the URI is not readable.
     */
    ParcelFileDescriptor openContentUri(String contentUri);

    /**
     * Create a gzip-compressed tar archive of dirPath and stream it through a pipe.
     * If packageContext is non-null and the target is a debuggable app's /data/data
     * directory, uses run-as <packageContext> to access it.
     * Returns a readable ParcelFileDescriptor to the tar stream, or null on failure.
     */
    ParcelFileDescriptor tarDirectory(String dirPath, String packageContext);
}
