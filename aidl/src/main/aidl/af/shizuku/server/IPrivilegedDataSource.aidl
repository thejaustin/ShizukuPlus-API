package af.shizuku.server;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/**
 * Privileged data and control APIs that work from uid 2000 because com.android.shell
 * has them granted as SYSTEM_FIXED — meaning the user cannot revoke them.
 *
 * Install-time grants (signature-level, always active):
 *   CONTROL_KEYGUARD, READ_WIFI_CREDENTIAL, READ_FRAME_BUFFER, INJECT_EVENTS,
 *   MANAGE_APP_OPS_MODES, CAPTURE_VIDEO_OUTPUT, READ_CLIPBOARD_IN_BACKGROUND,
 *   MODIFY_PHONE_STATE, DUMP, INSTALL_PACKAGES, DELETE_PACKAGES
 *
 * Runtime grants (SYSTEM_FIXED, cannot be revoked):
 *   READ_SMS, SEND_SMS, READ_CONTACTS, READ_CALL_LOG, CALL_PHONE, READ_PHONE_STATE,
 *   READ_PHONE_NUMBERS, ACCESS_FINE_LOCATION, READ_CALENDAR, GET_ACCOUNTS,
 *   CAMERA, RECORD_AUDIO, WRITE_CONTACTS, WRITE_CALENDAR, WRITE_CALL_LOG
 */
interface IPrivilegedDataSource {

    // ── Screen / Input (READ_FRAME_BUFFER + INJECT_EVENTS) ───────────────────

    /** Capture the screen as a PNG byte stream. Shell has READ_FRAME_BUFFER. */
    ParcelFileDescriptor screenshotAsPfd();

    /** Synthesize a tap at device coordinates. Shell has INJECT_EVENTS. */
    boolean injectTap(int x, int y);

    /** Type text by injecting individual key events. Shell has INJECT_EVENTS. */
    boolean injectText(String text);

    /** Perform a swipe gesture. Shell has INJECT_EVENTS. */
    boolean injectSwipe(int startX, int startY, int endX, int endY, int durationMs);

    /** Press a key by keycode (e.g. 3=Home, 4=Back, 66=Enter). Shell has INJECT_EVENTS. */
    boolean injectKeyEvent(int keyCode);

    // ── Messaging (READ_SMS / SEND_SMS — SYSTEM_FIXED runtime) ───────────────

    /**
     * Query the SMS content provider. folder: inbox, sent, draft, outbox, all.
     * Works because uid 2000 has READ_SMS SYSTEM_FIXED — it can never be revoked.
     */
    List<Bundle> getSmsMessages(String folder, int maxCount);

    /**
     * Send an SMS directly via the system SMS send API.
     * Shell has SEND_SMS SYSTEM_FIXED. Returns false on failure.
     */
    boolean sendSms(String recipient, String body);

    // ── Contacts (READ_CONTACTS — SYSTEM_FIXED runtime) ──────────────────────

    /**
     * Query the Contacts content provider for contact display names, phones, and emails.
     * uid 2000 has READ_CONTACTS SYSTEM_FIXED.
     */
    List<Bundle> getContacts(int maxCount);

    // ── Call Log (READ_CALL_LOG — SYSTEM_FIXED runtime) ──────────────────────

    /**
     * Query call log entries (type, number, duration, date).
     * uid 2000 has READ_CALL_LOG SYSTEM_FIXED.
     */
    List<Bundle> getCallLog(int maxCount);

    // ── Telephony (READ_PHONE_STATE + READ_PHONE_NUMBERS — SYSTEM_FIXED) ─────

    /**
     * Return IMEI, MEID, phone number, network operator, and SIM serial number.
     * uid 2000 has READ_PHONE_STATE + READ_PHONE_NUMBERS SYSTEM_FIXED.
     */
    Bundle getPhoneInfo();

    // ── Calendar (READ_CALENDAR — SYSTEM_FIXED runtime) ──────────────────────

    /**
     * Query calendar events: title, description, start/end time, location.
     * uid 2000 has READ_CALENDAR SYSTEM_FIXED.
     */
    List<Bundle> getCalendarEvents(int maxCount);

    // ── Accounts (GET_ACCOUNTS — SYSTEM_FIXED runtime) ───────────────────────

    /**
     * List all accounts registered with AccountManager (type + name).
     * uid 2000 has GET_ACCOUNTS SYSTEM_FIXED. Reveals Google, Samsung, app accounts.
     */
    List<Bundle> getAccounts();

    // ── Location (ACCESS_FINE_LOCATION — SYSTEM_FIXED runtime) ───────────────

    /**
     * Get the last known GPS fix from the location service.
     * uid 2000 has ACCESS_FINE_LOCATION SYSTEM_FIXED.
     * Bundle keys: provider, latitude, longitude, accuracy, altitude, speed, bearing, time.
     */
    Bundle getLastKnownLocation();

    // ── AppOps (MANAGE_APP_OPS_MODES — install permission) ───────────────────

    /**
     * Set the AppOps mode for a specific operation on a package.
     * Mode: "allow", "deny", "ignore", "default".
     * With MANAGE_APP_OPS_MODES, shell can grant any app access to any AppOps-gated
     * capability (camera, microphone, SMS, location, etc.) without user confirmation.
     * This is MORE powerful than pm grant for restricted permissions.
     */
    boolean setAppOpsMode(String packageName, String op, String mode);

    /**
     * Get the current AppOps mode for a package/op pair.
     * Returns "allow", "deny", "ignore", "default", or "" on failure.
     */
    String getAppOpsMode(String packageName, String op);

    // ── Keyguard (CONTROL_KEYGUARD — install permission) ─────────────────────

    /**
     * Dismiss the keyguard. On non-secure devices: fully unlocks. On secure devices
     * (PIN/pattern): transitions to the credential entry screen, bypassing swipe-only lock.
     * Shell has CONTROL_KEYGUARD as an install-time grant.
     */
    boolean dismissKeyguard();

    // ── WiFi (READ_WIFI_CREDENTIAL — install permission) ─────────────────────

    /**
     * List saved WiFi networks including pre-shared keys (passwords).
     * Shell has READ_WIFI_CREDENTIAL as an install-time grant — this was specifically
     * added to allow ADB to read credentials for testing automation.
     * Bundle keys: ssid, bssid, key_mgmt, psk (password), frequency.
     */
    List<Bundle> getSavedWifiNetworks();

    // ── Clipboard (READ_CLIPBOARD_IN_BACKGROUND — install permission) ─────────

    /**
     * Read the current system clipboard content.
     * Shell has READ_CLIPBOARD_IN_BACKGROUND — bypasses the Android 12+ foreground
     * restriction that prevents background apps from reading the clipboard.
     */
    String getClipboard();

    // ── Notifications (DUMP — install permission) ─────────────────────────────

    /**
     * Get all current notifications with full content via 'dumpsys notification --noredact'.
     * The --noredact flag suppresses the normally-applied content masking.
     * Shell has DUMP as an install-time grant.
     */
    String getNotifications();
}
