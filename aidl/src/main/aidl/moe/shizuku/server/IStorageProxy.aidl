package moe.shizuku.server;

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
     * Get file information.
     */
    Bundle getFileInfo(String path);
}
