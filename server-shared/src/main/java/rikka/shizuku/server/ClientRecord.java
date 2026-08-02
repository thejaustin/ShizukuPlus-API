package rikka.shizuku.server;

import static rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED;

import android.os.Bundle;

import af.shizuku.common.util.UserHandleCompat;
import moe.shizuku.server.IShizukuApplication;
import rikka.hidden.compat.DeviceIdleControllerApis;
import rikka.shizuku.server.util.HandlerUtil;
import rikka.shizuku.server.util.Logger;

public class ClientRecord {

    protected static final Logger LOGGER = new Logger("ClientRecord");

    public final int uid;
    public final int pid;
    public final IShizukuApplication client;
    public final String packageName;
    public final int apiVersion;
    public boolean allowed;

    public ClientRecord(int uid, int pid, IShizukuApplication client, String packageName, int apiVersion) {
        this.uid = uid;
        this.pid = pid;
        this.client = client;
        this.packageName = packageName;
        this.allowed = false;
        this.apiVersion = apiVersion;
    }

    public void dispatchRequestPermissionResult(int requestCode, boolean allowed) {
        Bundle reply = new Bundle();
        reply.putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, allowed);
        try {
            // This fires right after the user taps Allow/Deny in the manager's permission dialog -
            // exactly when the requesting app is most likely to have been backgrounded long enough
            // for Android's Cached Apps Freezer (12+) to have frozen it, silently dropping this
            // oneway callback ("sent binder code ... to frozen apps and got error -74", see #371).
            // The temp allowlist clears OomAdjuster's freeze state for the UID
            // (SHOULD_NOT_FREEZE_REASON_UID_ALLOWLISTED), not just Doze.
            DeviceIdleControllerApis.addPowerSaveTempWhitelistApp(packageName, 30 * 1000,
                    UserHandleCompat.getUserId(uid), 316/* PowerExemptionManager#REASON_SHELL */, "shell");
        } catch (Throwable e) {
            LOGGER.w(e, "Failed to add %s to power save temp whitelist before dispatchRequestPermissionResult", packageName);
        }
        try {
            client.dispatchRequestPermissionResult(requestCode, reply);
        } catch (Throwable e) {
            // The whitelist call above only requests an unfreeze; it doesn't guarantee AMS has
            // actually cleared the freeze state before this transact runs in the same call stack.
            // A user who just tapped Allow on a rish/plus consent notification (#377) can still
            // lose this race - retry once after a short delay instead of dropping the grant.
            LOGGER.w(e, "dispatchRequestPermissionResult failed for client (uid=%d, pid=%d, package=%s), scheduling one retry", uid, pid, packageName);
            HandlerUtil.getMainHandler().postDelayed(() -> {
                try {
                    client.dispatchRequestPermissionResult(requestCode, reply);
                } catch (Throwable retryError) {
                    LOGGER.w(retryError, "Retry dispatchRequestPermissionResult failed for client (uid=%d, pid=%d, package=%s)", uid, pid, packageName);
                }
            }, 300);
        }
    }
}
