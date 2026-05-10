package af.shizuku.server;

import android.os.Bundle;

interface IContinuityBridge {
    /**
     * Sync a piece of data to another device running Shizuku+.
     */
    boolean syncData(String targetDeviceId, String key, in Bundle data);

    /**
     * Register a listener for incoming continuity events.
     */
    void registerContinuityListener(IBinder listener);

    /**
     * List nearby devices running Shizuku+ that are eligible for handoff.
     */
    List<String> listEligibleDevices();

    /**
     * Request a secure handoff of a task to another device.
     */
    boolean requestHandoff(String targetDeviceId, in Bundle taskState);
}
