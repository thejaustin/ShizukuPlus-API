package rikka.shizuku.server.util;

import android.util.Log;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

/**
 * Shizuku+ Server-side Logger.
 * This class handles logging for the privileged server process.
 * It logs to Logcat with a consistent 'ShizukuPlus' prefix for easy filtering.
 */
public class Logger {

    public interface EventDispatcher {
        void dispatch(int priority, String tag, String message, Throwable throwable);
    }

    private static EventDispatcher sEventDispatcher;

    public static void setEventDispatcher(EventDispatcher dispatcher) {
        sEventDispatcher = dispatcher;
    }

    private final String tag;
    private final java.util.logging.Logger fileLogger;

    public Logger(String tag) {
        this.tag = "ShizukuPlus:" + tag;
        this.fileLogger = null;
    }

    public Logger(String tag, String logFilePath) {
        this.tag = "ShizukuPlus:" + tag;
        java.util.logging.Logger logger = null;
        try {
            logger = java.util.logging.Logger.getLogger(this.tag);
            FileHandler fh = new FileHandler(logFilePath, true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
        } catch (IOException e) {
            Log.e(this.tag, "Failed to initialize file logger", e);
        }
        this.fileLogger = logger;
    }

    public void v(String msg) {
        println(Log.VERBOSE, msg, null);
    }

    public void v(String fmt, Object... args) {
        println(Log.VERBOSE, format(fmt, args), null);
    }

    public void v(String msg, Throwable tr) {
        println(Log.VERBOSE, msg, tr);
    }

    public void d(String msg) {
        println(Log.DEBUG, msg, null);
    }

    public void d(String fmt, Object... args) {
        println(Log.DEBUG, format(fmt, args), null);
    }

    public void d(String msg, Throwable tr) {
        println(Log.DEBUG, msg, tr);
    }

    public void i(String msg) {
        println(Log.INFO, msg, null);
    }

    public void i(String fmt, Object... args) {
        println(Log.INFO, format(fmt, args), null);
    }

    public void i(String msg, Throwable tr) {
        println(Log.INFO, msg, tr);
    }

    public void w(String msg) {
        println(Log.WARN, msg, null);
    }

    public void w(String fmt, Object... args) {
        println(Log.WARN, format(fmt, args), null);
    }

    public void w(String msg, Throwable tr) {
        println(Log.WARN, msg, tr);
    }

    public void w(Throwable tr, String fmt, Object... args) {
        println(Log.WARN, format(fmt, args), tr);
    }

    public void e(String msg) {
        println(Log.ERROR, msg, null);
    }

    public void e(String fmt, Object... args) {
        println(Log.ERROR, format(fmt, args), null);
    }

    public void e(String msg, Throwable tr) {
        println(Log.ERROR, msg, tr);
    }

    public void e(Throwable tr, String fmt, Object... args) {
        println(Log.ERROR, format(fmt, args), tr);
    }

    private String format(String fmt, Object... args) {
        return args == null || args.length == 0 ? fmt : String.format(Locale.ENGLISH, fmt, args);
    }

    private void println(int priority, String msg, Throwable tr) {
        String fullMsg = tr == null ? msg : msg + "\n" + Log.getStackTraceString(tr);
        
        if (fileLogger != null && priority >= Log.INFO) {
            if (priority >= Log.WARN) {
                fileLogger.warning(fullMsg);
            } else {
                fileLogger.info(fullMsg);
            }
        }
        
        Log.println(priority, tag, fullMsg);

        // WARN is used throughout the server for routine/expected fallback paths (legacy
        // binder descriptor compat, blocked-but-handled calls, etc.), not just real problems.
        // Forwarding all of those to Sentry as full events - across every client on every
        // device - was a major driver of quota exhaustion. Only genuine errors are worth the
        // event budget; logcat still gets everything via Log.println above regardless.
        if (sEventDispatcher != null && priority >= Log.ERROR) {
            sEventDispatcher.dispatch(priority, tag, msg, tr);
        }
    }
}
