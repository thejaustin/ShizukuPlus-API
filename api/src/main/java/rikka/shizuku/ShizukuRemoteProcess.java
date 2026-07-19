package rikka.shizuku;

import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import moe.shizuku.server.IRemoteProcess;

public class ShizukuRemoteProcess extends Process implements Parcelable {

    private static final Set<ShizukuRemoteProcess> CACHE = Collections.synchronizedSet(new ArraySet<>());

    private static final String TAG = "ShizukuRemoteProcess";

    private IRemoteProcess remote;
    private OutputStream os;
    private InputStream is;

    ShizukuRemoteProcess(IRemoteProcess remote) {
        // The service returns null when the privileged process could not be started (e.g. the
        // command failed to spawn, or a stale server rejected the call). Fail with a clear,
        // catchable exception instead of an NPE on remote.asBinder() (SHIZUKUPLUS-85).
        if (remote == null) {
            throw new IllegalStateException(
                    "Shizuku returned a null remote process for newProcess() — the privileged service could not start the command");
        }
        this.remote = remote;
        try {
            this.remote.asBinder().linkToDeath((IBinder.DeathRecipient) () -> {
                this.remote = null;
                Log.v(TAG, "remote process is dead");

                CACHE.remove(ShizukuRemoteProcess.this);
            }, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "linkToDeath", e);
        }

        // The reference to the binder object must be hold
        CACHE.add(this);
    }

    @Override
    public OutputStream getOutputStream() {
        if (os == null) {
            try {
                os = new ParcelFileDescriptor.AutoCloseOutputStream(remote.getOutputStream());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
        return os;
    }

    @Override
    public InputStream getInputStream() {
        if (is == null) {
            try {
                is = new ParcelFileDescriptor.AutoCloseInputStream(remote.getInputStream());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
        return is;
    }

    @Override
    public InputStream getErrorStream() {
        try {
            return new ParcelFileDescriptor.AutoCloseInputStream(remote.getErrorStream());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int waitFor() throws InterruptedException {
        try {
            return remote.waitFor();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int exitValue() {
        try {
            return remote.exitValue();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void destroy() {
        try {
            remote.destroy();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean alive() {
        try {
            return remote.alive();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean waitForTimeout(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            return remote.waitForTimeout(timeout, unit.toString());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public IBinder asBinder() {
        return remote.asBinder();
    }

    private ShizukuRemoteProcess(Parcel in) {
        remote = IRemoteProcess.Stub.asInterface(in.readStrongBinder());
    }

    public static final Creator<ShizukuRemoteProcess> CREATOR = new Creator<ShizukuRemoteProcess>() {
        @Override
        public ShizukuRemoteProcess createFromParcel(Parcel in) {
            return new ShizukuRemoteProcess(in);
        }

        @Override
        public ShizukuRemoteProcess[] newArray(int size) {
            return new ShizukuRemoteProcess[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongBinder(remote.asBinder());
    }
}
