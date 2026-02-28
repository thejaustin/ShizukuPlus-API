package moe.shizuku.server;

interface IOverlayManagerPlus {
    /**
     * Enable or disable a specific system overlay.
     */
    boolean setOverlayEnabled(String packageName, boolean enabled);

    /**
     * Change the priority of an overlay.
     */
    boolean setHighestPriority(String packageName);

    /**
     * List all installed overlays and their states.
     */
    List<String> getAllOverlays();
}
