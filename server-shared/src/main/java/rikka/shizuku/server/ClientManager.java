package rikka.shizuku.server;

import android.os.IBinder;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import moe.shizuku.server.IShizukuApplication;
import rikka.shizuku.server.util.Logger;

public class ClientManager<ConfigMgr extends ConfigManager> {

    protected static final Logger LOGGER = new Logger("UserServiceRecord");

    private final ConfigMgr configManager;
    private final List<ClientRecord> clientRecords = Collections.synchronizedList(new ArrayList<>());

    public ClientManager(ConfigMgr configManager) {
        this.configManager = configManager;
    }

    public ConfigMgr getConfigManager() {
        return configManager;
    }

    public List<ClientRecord> getClientRecords() {
        return clientRecords;
    }

    public List<ClientRecord> findClients(int uid) {
        synchronized (this) {
            List<ClientRecord> res = new ArrayList<>();
            for (ClientRecord clientRecord : clientRecords) {
                if (clientRecord.uid == uid) {
                    res.add(clientRecord);
                }
            }
            return res;
        }
    }

    public ClientRecord findClient(int uid, int pid) {
        for (ClientRecord clientRecord : clientRecords) {
            if (clientRecord.pid == pid && clientRecord.uid == uid) {
                return clientRecord;
            }
        }
        return null;
    }

    public int getClientCount() {
        return clientRecords.size();
    }

    public ClientRecord requireClient(int callingUid, int callingPid) {
        return requireClient(callingUid, callingPid, false);
    }

    public ClientRecord requireClient(int callingUid, int callingPid, boolean requiresPermission) {
        ClientRecord clientRecord = findClient(callingUid, callingPid);
        if (clientRecord == null) {
            // Delivery-order guard for Android 14 consent race (#387):
            // ShellConsentActivity can call deliverBinder() and hand the Shizuku binder to rish
            // before the server finishes processing rish's attachApplication() call on its own
            // Binder thread.  rish then immediately makes API calls (checkSelfPermission /
            // requestPermission) that arrive here before the ClientRecord exists.
            // Retry with exponential-ish back-off up to ~2 s total, matching the window used in
            // ShellBinderRequestHandler.deliverBinder() for the frozen-process case.
            final long[] retryDelaysMs = {50L, 100L, 100L, 200L, 200L, 300L, 500L};
            for (long delayMs : retryDelaysMs) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                clientRecord = findClient(callingUid, callingPid);
                if (clientRecord != null) {
                    LOGGER.d("requireClient: uid %d pid %d attached after %d ms delay (Android 14 consent race)",
                            callingUid, callingPid, delayMs);
                    break;
                }
            }
        }
        if (clientRecord == null) {
            LOGGER.w("Caller (uid %d, pid %d) is not an attached client", callingUid, callingPid);
            throw new IllegalStateException("Not an attached client");
        }
        if (requiresPermission && !clientRecord.allowed) {
            throw new SecurityException("Caller has no permission");
        }
        return clientRecord;
    }

    public ClientRecord addClient(int uid, int pid, IShizukuApplication client, String packageName, int apiVersion) {
        ClientRecord clientRecord = new ClientRecord(uid, pid, client, packageName, apiVersion);

        ConfigPackageEntry entry = configManager.find(uid);
        if (entry != null && entry.isAllowed()) {
            clientRecord.allowed = true;
        }

        IBinder binder = client.asBinder();
        IBinder.DeathRecipient deathRecipient = () -> clientRecords.remove(clientRecord);
        try {
            binder.linkToDeath(deathRecipient, 0);
        } catch (RemoteException e) {
            LOGGER.w(e, "addClient: linkToDeath failed");
            return null;
        }

        clientRecords.add(clientRecord);
        return clientRecord;
    }

    public void remove(String packageName) {
        synchronized (this) {
            clientRecords.removeIf(clientRecord -> clientRecord.packageName.equals(packageName));
        }
    }
}


