package rikka.shizuku.server;

import static rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED;

import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import java.util.concurrent.atomic.AtomicBoolean;

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
            // lose this race - a short backoff retry gives it more real chances to land; on
            // devices that support it (API 36+), a frozen-state callback supplements this by
            // reacting to the actual unfreeze event instead of guessing at a delay at all (see
            // #371, gmm96's logcat-confirmed writeup on the identical race in UserServiceRecord).
            LOGGER.w(e, "dispatchRequestPermissionResult failed for client (uid=%d, pid=%d, package=%s), scheduling retry", uid, pid, packageName);
            AtomicBoolean delivered = new AtomicBoolean(false);
            scheduleBackoffRetry(requestCode, reply, 0, delivered);
            tryRegisterFrozenStateRetry(requestCode, reply, delivered);
        }
    }

    private static final long[] RETRY_DELAYS_MS = {300, 1000, 3000};

    private void scheduleBackoffRetry(int requestCode, Bundle reply, int attempt, AtomicBoolean delivered) {
        HandlerUtil.getMainHandler().postDelayed(() -> {
            if (delivered.get()) {
                return;
            }
            try {
                client.dispatchRequestPermissionResult(requestCode, reply);
                delivered.set(true);
            } catch (Throwable retryError) {
                if (attempt + 1 < RETRY_DELAYS_MS.length) {
                    LOGGER.w(retryError, "Retry %d failed for client (uid=%d, pid=%d, package=%s), scheduling next retry", attempt + 1, uid, pid, packageName);
                    scheduleBackoffRetry(requestCode, reply, attempt + 1, delivered);
                } else {
                    LOGGER.w(retryError, "All backoff retries failed for client (uid=%d, pid=%d, package=%s)", uid, pid, packageName);
                }
            }
        }, RETRY_DELAYS_MS[attempt]);
    }

    // See UserServiceRecord.tryRegisterFrozenStateRetry for the full rationale - same API,
    // same SDK_INT >= 36 gate, purely supplemental to the backoff retry above.
    private void tryRegisterFrozenStateRetry(int requestCode, Bundle reply, AtomicBoolean delivered) {
        if (Build.VERSION.SDK_INT < 36) {
            return;
        }
        try {
            IBinder clientBinder = client.asBinder();
            IBinder.FrozenStateChangeCallback callback = new IBinder.FrozenStateChangeCallback() {
                @Override
                public void onFrozenStateChanged(IBinder who, int state) {
                    if (state != IBinder.FrozenStateChangeCallback.STATE_UNFROZEN) {
                        return;
                    }
                    who.removeFrozenStateChangeCallback(this);
                    if (delivered.get()) {
                        return;
                    }
                    try {
                        client.dispatchRequestPermissionResult(requestCode, reply);
                        delivered.set(true);
                    } catch (Throwable retryError) {
                        LOGGER.w(retryError, "Frozen-state-triggered retry failed for client (uid=%d, pid=%d, package=%s)", uid, pid, packageName);
                    }
                }
            };
            clientBinder.addFrozenStateChangeCallback(HandlerUtil.getMainHandler()::post, callback);
        } catch (Throwable t) {
            // Not supported on this device/binder driver - the backoff retry already scheduled
            // above is the fallback.
            LOGGER.v("addFrozenStateChangeCallback unavailable for client (uid=" + uid + "), relying on backoff retry", t);
        }
    }
}
