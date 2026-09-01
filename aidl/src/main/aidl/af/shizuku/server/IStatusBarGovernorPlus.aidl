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

    /**
     * Add a tile to the Quick Settings panel (appended after existing tiles).
     * Spec format: system tiles use short names ("wifi", "bt", "airplane", "dnd", "flashlight",
     * "rotation", "nfc", "internet"); custom tiles use "custom(com.pkg/.TileService)".
     * No-op if the tile is already present.
     */
    boolean addTile(String tileSpec);

    /**
     * Remove a tile from the Quick Settings panel.
     * No-op if the tile is not present.
     */
    boolean removeTile(String tileSpec);

    /**
     * Move an existing tile to a specific zero-based position in the QS panel.
     * If the tile isn't present it is inserted at that position.
     * Clamps to valid range automatically.
     */
    boolean moveTileToPosition(String tileSpec, int position);
}
