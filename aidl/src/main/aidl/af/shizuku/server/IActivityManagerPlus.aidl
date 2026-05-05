package af.shizuku.server;

interface IActivityManagerPlus {
    /**
     * Force stop a package and clear its background tasks deeply.
     */
    boolean deepForceStop(String packageName);

    /**
     * Set the standby bucket for an app (e.g., ACTIVE, WORKING_SET, FREQUENT, RARE, RESTRICTED).
     */
    boolean setAppStandbyBucket(String packageName, int bucket);

    /**
     * Kill all background processes for memory optimization.
     */
    boolean killAllBackgroundProcesses();

    /**
     * Freeze (disable) an application.
     */
    boolean freezeApp(String packageName);

    /**
     * Unfreeze (enable) an application.
     */
    boolean unfreezeApp(String packageName);

    /**
     * Check if an application is currently frozen (disabled).
     */
    boolean isAppFrozen(String packageName);

    /**
     * Set the maximum number of background processes the system should maintain.
     */
    void setAppProcessLimit(int limit);

    /**
     * Get a list of currently running processes with their importance.
     */
    List<String> getRunningProcesses();

    /**
     * Clear the cache of a specific application.
     */
    boolean clearAppCache(String packageName);

    /**
     * Clear all data of a specific application.
     */
    boolean clearAppData(String packageName);
}
