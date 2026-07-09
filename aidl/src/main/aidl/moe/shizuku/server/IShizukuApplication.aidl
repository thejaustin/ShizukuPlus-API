// Legacy/stock descriptor, kept so the server can call back stock Shizuku clients
// (e.g. Obtainium) that were built against moe.shizuku.server.IShizukuApplication.
// Transaction codes MUST match the af descriptor (and upstream Shizuku) so a single
// wire transaction is understood by whichever stub the client registered.
package moe.shizuku.server;

interface IShizukuApplication {

    oneway void bindApplication(in Bundle data) = 1;

    oneway void dispatchRequestPermissionResult(int requestCode, in Bundle data) = 2;

    oneway void dispatchLog(in String appName, in String packageName, in String action) = 3;

    oneway void dispatchSentryEvent(in String eventJson) = 4;

    // Sui only
    void showPermissionConfirmation(int requestUid, int requestPid, in String requestPackageName, int requestCode) = 10000;
}
