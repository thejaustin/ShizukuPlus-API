// Descriptor MUST be moe.shizuku.server.IShizukuApplication: this is the callback interface a
// client passes to attachApplication for the server to invoke. Third-party rikka clients
// implement moe.shizuku.server.IShizukuApplication, so the server's oneway callbacks -
// especially dispatchRequestPermissionResult (code 2), the "permission granted" notification -
// must transact under the moe.* token or the client's Binder rejects it with an interface
// mismatch and the app's requestPermission callback never fires (#325-adjacent).
//
// Codes 1/2 match upstream Shizuku exactly. dispatchLog (3) and dispatchSentryEvent (4) are
// ShizukuPlus additions only ever called on the MANAGER's client (which uses this same AIDL),
// never on third-party clients, so they don't collide with anything a rikka client expects.
package moe.shizuku.server;

interface IShizukuApplication {

    oneway void bindApplication(in Bundle data) = 1;

    oneway void dispatchRequestPermissionResult(int requestCode, in Bundle data) = 2;

    oneway void dispatchLog(in String appName, in String packageName, in String action) = 3;

    oneway void dispatchSentryEvent(in String eventJson) = 4;

    // Sui only
    void showPermissionConfirmation(int requestUid, int requestPid, in String requestPackageName, int requestCode) = 10000;
}
