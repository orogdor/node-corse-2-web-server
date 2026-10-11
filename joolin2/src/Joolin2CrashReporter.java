package com.jooan.qiaoanzhilian.ui.activity.play;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import java.io.PrintWriter;
import java.io.StringWriter;

/** On-device diagnostic, no remote upload and no credentials stored. */
public final class Joolin2CrashReporter {
    private static final String PREFS = "joolin2_fatal_errors";
    private static volatile boolean installed;

    private Joolin2CrashReporter() {}

    public static synchronized void install(Application application) {
        if (installed) return;
        installed = true;
        final Context context = application.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringWriter writer = new StringWriter();
                PrintWriter out = new PrintWriter(writer);
                out.println("Joolin2 failure, thread: " + thread.getName());
                if (error != null) error.printStackTrace(out);
                out.flush();
                String report = writer.toString();
                // Only local stack traces. Obscure common accidental secrets.
                report = report.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+", "$1[redacted]");
                report = report.replaceAll("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}", "[email redacted]");
                if (report.length() > 14000) report = report.substring(0, 14000);
                SharedPreferences store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                store.edit()
                    .putString("stack", report)
                    .putLong("time", System.currentTimeMillis())
                    .commit();
            } catch (Throwable ignored) {
                // Preserve Android's original crash reporter if this recorder fails.
            } finally {
                if (previous != null) previous.uncaughtException(thread, error);
                else Process.killProcess(Process.myPid());
            }
        });
    }
}
