package moe.shizuku.server;

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
}
