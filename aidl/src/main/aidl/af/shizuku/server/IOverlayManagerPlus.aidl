package af.shizuku.server;

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

    /**
     * Inject a dynamic resource overlay (Android 12+).
     */
    boolean injectResourceOverlay(String targetPackage, String resourceName, int type, String value);

    /**
     * [Ghost Bridge] Prepares an OverlayFS shadow mount for rootless system modification simulation.
     */
    boolean prepareShadowMount(String callingPackage, String partition);

    /**
     * [Samsung One UI 7+] Write `current_sec_active_themepackage` to the system settings table.
     *
     * On One UI 7+ (Android 15 / API 35+) Samsung shows a mandatory setup wizard the first time
     * a theme package is activated. Writing this setting before (or right after) enabling overlays
     * bypasses that flow entirely. Pass the overlay/theme package name that is being activated, or
     * pass null / empty string to clear the setting and reset to the default state.
     *
     * No-op (returns true) on non-Samsung devices.
     */
    boolean setActiveThemePackage(String packageName);
}
