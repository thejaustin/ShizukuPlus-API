package af.shizuku.server;

import android.os.Bundle;

/**
 * Privileged display configuration — override physical display resolution and
 * density (DPI) via the wm shell tool. Changes persist until explicitly reset
 * or the device reboots.
 *
 * These are the same operations as `adb shell wm size/density`, exposed as a
 * clean IPC interface so apps running under Shizuku can control display layout
 * without needing ADB access themselves.
 */
interface IDisplayTunerPlus {

    /**
     * Override the display resolution to width×height pixels.
     * Passing 0 for either dimension resets to device default.
     */
    boolean setDisplaySize(int width, int height);

    /** Clear any display size override and restore the physical default. */
    boolean resetDisplaySize();

    /**
     * Override the display density (DPI).
     * Pass 0 to reset to the device default.
     */
    boolean setDisplayDensity(int dpi);

    /** Clear any density override and restore the physical default. */
    boolean resetDisplayDensity();

    /**
     * Get current display size info.
     * Returns a Bundle with integer keys:
     *   "physical_width", "physical_height" — hardware resolution
     *   "width", "height"                   — effective (overridden if set)
     *   "has_override"                       — 1 if an override is active, 0 otherwise
     */
    Bundle getDisplaySize();

    /**
     * Get current effective density in DPI.
     * Returns the override density if set, otherwise the physical density.
     */
    int getDisplayDensity();

    /**
     * Get physical (hardware) density in DPI, regardless of any override.
     */
    int getPhysicalDensity();
}
