package af.shizuku.server;

import android.os.Bundle;

/**
 * Recent-tasks access for launchers and task switchers. Result Bundle keys are public
 * constants in rikka.shizuku.ShizukuPlusAPI.Tasks.
 */
interface ITaskManagerPlus {
    /**
     * Recent tasks of the calling app's user, newest first. {@code flags} are
     * ActivityManager.RECENT_* flags; RECENT_IGNORE_UNAVAILABLE is always applied.
     * Bundle: int[] "taskIds" and String[] "components" (flattened ComponentName, parallel
     * arrays). Empty Bundle on failure.
     */
    Bundle getRecentTasks(int maxNum, int flags);

    /**
     * The task's last screenshot as a HardwareBuffer (Parcelable) under "buffer".
     * Empty Bundle if there is none. The caller owns and must close the buffer.
     */
    Bundle getTaskThumbnail(int taskId);

    /** Brings a recent task to the front. {@code options} may be an ActivityOptions Bundle. */
    boolean startTaskFromRecents(int taskId, in Bundle options);

    /** Removes the task from recents and finishes its activities. Never touches accounts. */
    boolean removeTask(int taskId);
}
