package af.shizuku.server;

interface IStatusBarGovernorPlus {
    /** Disable the system notification shade from expanding on swipe-down. */
    boolean disableExpansion();

    /** Re-enable the system notification shade expansion. */
    boolean enableExpansion();

    /**
     * Programmatically click a Quick Settings tile by component name.
     * Works because shell UID passes StatusBarManagerService's enforceStatusBarOrShell check.
     * Samsung One UI tile names: "internet", "bt", "airplane", "dnd", "flashlight", "rotation", "nfc"
     */
    boolean clickTile(String component);

    /** Returns the current sysui_qs_tiles secure setting value (comma-separated tile list). */
    String getCurrentTiles();

    /** Overwrite the QS tile list. Comma-separated tile names. */
    boolean setTiles(String tileList);

    /** Collapse the shade (no-op if it's our shade doing the managing, but useful for cleanup). */
    boolean collapse();

    /** Expand the settings panel (Quick Settings) — used for delegating to system for unsupported tiles. */
    boolean expandSettings();
}
