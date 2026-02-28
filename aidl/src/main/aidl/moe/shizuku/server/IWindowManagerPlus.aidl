package moe.shizuku.server;

interface IWindowManagerPlus {
    /**
     * Force enable free-form resizing for a specific package,
     * bypassing the app's manifest restrictions.
     */
    void forceResizable(String packageName, boolean enabled);

    /**
     * Pin a window to a specific region of the screen (Desktop Mode).
     */
    void pinToRegion(int taskId, in Rect region);

    /**
     * Force an app into a system bubble.
     */
    void setAsBubble(int taskId, boolean enabled);

    /**
     * Configure the position and visibility of the Android 17 'Bubble Bar'.
     */
    void configureBubbleBar(in Bundle settings);

    /**
     * Set a window as 'Always on Top' using privileged flags.
     */
    void setAlwaysOnTop(int taskId, boolean enabled);
}
