package rikka.shizuku.server;

import static rikka.shizuku.ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteCallbackList;
import android.os.RemoteException;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import moe.shizuku.server.IShizukuServiceConnection;
import rikka.hidden.compat.DeviceIdleControllerApis;
import rikka.shizuku.server.util.HandlerUtil;
import rikka.shizuku.server.util.Logger;

public abstract class UserServiceRecord {

    private Runnable startTimeoutCallback;

    private class ConnectionList extends RemoteCallbackList<IShizukuServiceConnection> {

        @Override
        public void onCallbackDied(IShizukuServiceConnection callback) {
            if (daemon || getRegisteredCallbackCount() != 0) {
                return;
            }

            LOGGER.v("Remove service record %s since it does not run as a daemon and all connections are gone", token);
            removeSelf();
        }
    }

    protected static final Logger LOGGER = new Logger("UserServiceRecord");

    private final IBinder.DeathRecipient deathRecipient;
    public final int versionCode;
    public final String packageName;
    public final int userId;
    public String token;
    public IBinder service;
    public final RemoteCallbackList<IShizukuServiceConnection> callbacks = new ConnectionList();
    public boolean daemon;
    public boolean starting;

    // Bumped on every setBinder()/destroy() so a delayed retry in broadcastBinderReceived() can
    // detect the record has moved on (rebound to a new service, or torn down) since it was
    // scheduled, instead of blindly delivering a stale/superseded binder.
    private volatile int bindGeneration = 0;
    private final Set<IBinder> pendingRetryBinders = ConcurrentHashMap.newKeySet();

    public UserServiceRecord(int versionCode, boolean daemon, String packageName, int userId) {
        this.versionCode = versionCode;
        this.packageName = packageName;
        this.userId = userId;
        this.token = UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
        this.deathRecipient = () -> {
            LOGGER.v("Binder for service record %s is dead", token);
            removeSelf();
        };
        this.daemon = daemon;
    }

    public void setStartingTimeout(long timeoutMillis) {
        if (starting) {
            LOGGER.w("Service record %s is already starting", token);
            return;
        }

        LOGGER.v("Set starting timeout for service record %s: %d", token, timeoutMillis);

        starting = true;
        startTimeoutCallback = () -> {
            if (starting) {
                LOGGER.w("Service record %s is not started in %d ms", token, timeoutMillis);
                removeSelf();
            }
        };
        HandlerUtil.getMainHandler().postDelayed(startTimeoutCallback, timeoutMillis);
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public void setBinder(IBinder binder) {
        LOGGER.v("Binder received for service record %s", token);

        HandlerUtil.getMainHandler().removeCallbacks(startTimeoutCallback);

        service = binder;
        bindGeneration++;

        try {
            binder.linkToDeath(deathRecipient, 0);
        } catch (Throwable tr) {
            LOGGER.w("linkToDeath %s", token);
        }

        // Guards against Android's Cached Apps Freezer (12+): broadcastBinderReceived() below
        // delivers a oneway callback into packageName's own process, which may have backgrounded
        // and been frozen while the UserService process was starting - silently dropping the
        // callback ("sent binder code ... to frozen apps and got error -74", see #371). Mirrors
        // UserServiceManager#whitelistBeforeCallback for this, the first-connect call site.
        try {
            DeviceIdleControllerApis.addPowerSaveTempWhitelistApp(packageName, 30 * 1000, userId,
                    316/* PowerExemptionManager#REASON_SHELL */, "shell");
        } catch (Throwable e) {
            LOGGER.w(e, "Failed to add %s to power save temp whitelist before broadcastBinderReceived", packageName);
        }

        broadcastBinderReceived();
    }

    private void callConnected(IShizukuServiceConnection conn, IBinder service) throws RemoteException {
        IBinder binder = conn.asBinder();
        String descriptor = binder.getInterfaceDescriptor();
        if ("moe.shizuku.server.IShizukuServiceConnection".equals(descriptor)) {
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(descriptor);
                data.writeStrongBinder(service);
                binder.transact(1 /* connected */, data, null, Binder.FLAG_ONEWAY);
            } finally {
                data.recycle();
            }
        } else {
            conn.connected(service);
        }
    }

    private void callDied(IShizukuServiceConnection conn) throws RemoteException {
        IBinder binder = conn.asBinder();
        String descriptor = binder.getInterfaceDescriptor();
        if ("moe.shizuku.server.IShizukuServiceConnection".equals(descriptor)) {
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(descriptor);
                binder.transact(2 /* died */, data, null, Binder.FLAG_ONEWAY);
            } finally {
                data.recycle();
            }
        } else {
            conn.died();
        }
    }

    public void broadcastBinderReceived() {
        LOGGER.v("Broadcast binder received for service record %s", token);

        IBinder deliveredService = service;
        int broadcastGeneration = bindGeneration;
        int count = callbacks.beginBroadcast();
        for (int i = 0; i < count; i++) {
            IShizukuServiceConnection conn = callbacks.getBroadcastItem(i);
            try {
                callConnected(conn, deliveredService);
            } catch (Throwable e) {
                // addPowerSaveTempWhitelistApp() in setBinder()/whitelistBeforeCallback() only
                // *requests* an unfreeze from AMS's own handler - it doesn't guarantee the freeze
                // state has actually cleared before this oneway transact runs in the same call
                // stack, so the very first attempt can still lose the race and hit the same "sent
                // binder code ... to frozen apps" drop it was meant to guard against (#371, still
                // reported failing on r2202/r2204). One delayed retry gives the exemption a real
                // chance to take effect instead of silently giving up on the first loss.
                //
                // broadcastBinderReceived() can be called again (e.g. UserServiceManager rebinding
                // an already-connected daemon) while a retry from an earlier failed attempt is
                // still pending, and setBinder()/destroy() can supersede or tear down `service` in
                // the meantime - pendingRetryBinders dedupes so this callback doesn't end up with
                // two in-flight retries, and the generation check makes a retry a no-op once the
                // record has moved on, instead of delivering a stale or post-destroy binder.
                IBinder connBinder = conn.asBinder();
                if (!pendingRetryBinders.add(connBinder)) {
                    LOGGER.w(e, "Failed to call connected %s, retry already pending for this connection", token);
                } else {
                    LOGGER.w(e, "Failed to call connected %s, scheduling one retry", token);
                    HandlerUtil.getMainHandler().postDelayed(() -> {
                        pendingRetryBinders.remove(connBinder);
                        if (bindGeneration != broadcastGeneration) {
                            LOGGER.w("Skipping stale retry for %s (service superseded or destroyed)", token);
                            return;
                        }
                        try {
                            callConnected(conn, deliveredService);
                        } catch (Throwable retryError) {
                            LOGGER.w(retryError, "Retry failed to call connected %s", token);
                        }
                    }, 300);
                }
            }
        }
        callbacks.finishBroadcast();
    }

    public void broadcastBinderDied() {
        LOGGER.v("Broadcast binder died for service record %s", token);

        int count = callbacks.beginBroadcast();
        for (int i = 0; i < count; i++) {
            try {
                callDied(callbacks.getBroadcastItem(i));
            } catch (Throwable e) {
                LOGGER.w("Failed to call died %s", token);
            }
        }
        callbacks.finishBroadcast();
    }

    public abstract void removeSelf();

    public void destroy() {
        // Invalidate any retry scheduled by broadcastBinderReceived() before this call - it must
        // not deliver `connected()` for a service that's about to be torn down.
        bindGeneration++;

        if (service != null) {
            service.unlinkToDeath(deathRecipient, 0);
        }

        if (service != null && service.pingBinder()) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(service.getInterfaceDescriptor());
                service.transact(USER_SERVICE_TRANSACTION_destroy, data, reply, Binder.FLAG_ONEWAY);
            } catch (Throwable e) {
                LOGGER.w("Failed to call destroy %s", token);
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        callbacks.kill();
    }
}
