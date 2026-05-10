package af.shizuku.server;

import android.os.Bundle;

interface IVirtualMachineManager {
    /**
     * Create a new Virtual Machine.
     */
    boolean create(String name, in Bundle config);

    /**
     * Start a Virtual Machine.
     */
    boolean start(String name);

    /**
     * Stop a Virtual Machine.
     */
    boolean stop(String name);

    /**
     * Delete a Virtual Machine.
     */
    boolean delete(String name);

    /**
     * Get the status of a Virtual Machine.
     */
    String getStatus(String name);

    /**
     * List all Virtual Machines managed by Shizuku+.
     */
    List<String> list();
}
