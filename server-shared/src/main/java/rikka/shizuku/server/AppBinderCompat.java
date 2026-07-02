package rikka.shizuku.server;

import android.os.Bundle;

import rikka.shizuku.server.util.Logger;

/**
 * The IShizukuApplication callbacks (bindApplication / dispatchRequestPermissionResult)
 * are declared {@code oneway}. Because they are fire-and-forget, a call made with the
 * fork's {@code af.shizuku.server.IShizukuApplication} descriptor to a client that was
 * built against the stock {@code moe.shizuku.server.IShizukuApplication} descriptor fails
 * silently on the client (enforceInterface SecurityException) and never throws on the
 * server, so any try/catch based fallback is dead code.
 *
 * To stay compatible with BOTH fork ("af") clients and stock/other ("moe") clients such
 * as Obtainium, every callback is dispatched via both descriptors using their real
 * AIDL-generated proxies (so marshalling is correct). A client's stub only accepts its
 * own descriptor, so exactly one delivery succeeds and the other is ignored; the
 * transaction codes are identical across both descriptors.
 */
public final class AppBinderCompat {

    private static final Logger LOGGER = new Logger("AppBinderCompat");

    private AppBinderCompat() {
    }

    /** Dispatch bindApplication(reply) via both the af and moe descriptors. */
    public static void bindApplication(af.shizuku.server.IShizukuApplication application, Bundle reply) {
        try {
            application.bindApplication(reply);
        } catch (Throwable e) {
            LOGGER.w("bindApplication via af descriptor failed");
        }
        try {
            moe.shizuku.server.IShizukuApplication.Stub
                    .asInterface(application.asBinder())
                    .bindApplication(reply);
        } catch (Throwable e) {
            LOGGER.w("bindApplication via moe descriptor failed");
        }
    }

    /** Dispatch dispatchRequestPermissionResult(requestCode, reply) via both descriptors. */
    public static void dispatchRequestPermissionResult(af.shizuku.server.IShizukuApplication application, int requestCode, Bundle reply) {
        try {
            application.dispatchRequestPermissionResult(requestCode, reply);
        } catch (Throwable e) {
            LOGGER.w("dispatchRequestPermissionResult via af descriptor failed");
        }
        try {
            moe.shizuku.server.IShizukuApplication.Stub
                    .asInterface(application.asBinder())
                    .dispatchRequestPermissionResult(requestCode, reply);
        } catch (Throwable e) {
            LOGGER.w("dispatchRequestPermissionResult via moe descriptor failed");
        }
    }
}
