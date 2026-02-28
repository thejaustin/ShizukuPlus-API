package moe.shizuku.server;

interface IAICorePlus {
    /**
     * Get a color sample from any pixel on the screen.
     * Extension of the Android 17 EyeDropper API for privileged use.
     */
    int getPixelColor(int x, int y);

    /**
     * Schedule a high-priority task on the Neural Processing Unit (NPU).
     */
    boolean scheduleNPULoad(in Bundle taskData);

    /**
     * Capture a privileged screenshot of a specific window/layer for AI analysis.
     */
    Bitmap captureLayer(int layerId);

    /**
     * Get current system intelligence context (detected entities, screen text).
     */
    Bundle getSystemContext();
}
