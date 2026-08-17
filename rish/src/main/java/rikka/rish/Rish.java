package rikka.rish;

import android.util.Log;

import java.util.Arrays;

public class Rish {

    private static final String TAG = "RISH";

    public void requestPermission(Runnable onGrantedRunnable) {

    }

    private void startShell(String[] args, boolean permissionGranted) {
        if (!permissionGranted) {
            requestPermission(() -> startShell(args, true));
            return;
        }

        startShell(args);
    }

    private void startShell(String[] args) {
        try {
            RishTerminal terminal = new RishTerminal(args);
            terminal.start();
            int exitCode = terminal.waitFor();
            System.exit(exitCode);
        } catch (Throwable e) {
            // A bare e.getMessage() prints a blank line for exceptions with no message (e.g. a
            // RemoteException-wrapped SecurityException that loses its text in transit), which
            // reads identically to "the command ran and did nothing" - always include the
            // exception class and, when available, the message, so a real server-side rejection
            // is never indistinguishable from a silent no-op on the terminal.
            System.err.println(e.getClass().getName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
            e.printStackTrace();
            System.err.flush();
            System.exit(1);
            //abort(e.getMessage());
        }
    }

    public void start(String[] args) {
        Log.d(TAG, "args: " + Arrays.toString(args));
        startShell(args, false);
    }
}
