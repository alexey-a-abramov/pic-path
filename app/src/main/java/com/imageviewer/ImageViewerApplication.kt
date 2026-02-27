package com.imageviewer

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.imageviewer.ui.CrashActivity
import java.io.PrintWriter
import java.io.StringWriter

class ImageViewerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupExceptionHandler()
    }

    private fun setupExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("ImageViewerApp", "Uncaught exception in thread: ${thread.name}", throwable)

                // Get detailed stack trace
                val stackTrace = getStackTraceString(throwable)

                // Try to show crash screen
                showCrashScreen(stackTrace)

                // Give time for the activity to start
                Thread.sleep(500)

            } catch (e: Exception) {
                Log.e("ImageViewerApp", "Error in exception handler", e)
                // Fall back to default handler if our handler fails
                defaultHandler?.uncaughtException(thread, throwable)
            } finally {
                // Exit the app
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            }
        }
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)

        // Add additional context
        val builder = StringBuilder()
        builder.append("Exception: ${throwable.javaClass.name}\n")
        builder.append("Message: ${throwable.message ?: "No message"}\n")
        builder.append("\nStack Trace:\n")
        builder.append(sw.toString())

        // Add cause if present
        var cause = throwable.cause
        var level = 1
        while (cause != null && level <= 5) {
            builder.append("\n\nCaused by (level $level): ${cause.javaClass.name}\n")
            builder.append("Message: ${cause.message ?: "No message"}\n")
            val causeSw = StringWriter()
            val causePw = PrintWriter(causeSw)
            cause.printStackTrace(causePw)
            builder.append(causeSw.toString())
            cause = cause.cause
            level++
        }

        return builder.toString()
    }

    private fun showCrashScreen(stackTrace: String) {
        try {
            val intent = Intent(this, CrashActivity::class.java).apply {
                putExtra("stacktrace", stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            // Use handler to ensure we're starting from UI thread context
            if (Looper.myLooper() == Looper.getMainLooper()) {
                startActivity(intent)
            } else {
                Handler(Looper.getMainLooper()).post {
                    startActivity(intent)
                }
            }
        } catch (e: Exception) {
            Log.e("ImageViewerApp", "Failed to show crash screen", e)
        }
    }
}
