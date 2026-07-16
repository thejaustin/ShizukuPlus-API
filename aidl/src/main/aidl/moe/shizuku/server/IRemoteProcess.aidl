// Descriptor MUST be moe.shizuku.server.IRemoteProcess to match the binder interface every
// rikka Shizuku-API client compiles against. newProcess() returns this over the wire, and the
// client calls getInputStream()/waitFor()/etc on it with the moe.* interface token; if the
// server's implementation used a different package (e.g. af.shizuku.server), the token wouldn't
// match and every such call would fail with "Binder invocation to an incorrect interface" (#325).
package moe.shizuku.server;

interface IRemoteProcess {

    ParcelFileDescriptor getOutputStream();

    ParcelFileDescriptor getInputStream();

    ParcelFileDescriptor getErrorStream();

    int waitFor();

    int exitValue();

    void destroy();

    boolean alive();

    boolean waitForTimeout(long timeout, String unit);
}
