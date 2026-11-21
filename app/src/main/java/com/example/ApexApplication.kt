package com.example

import android.app.Application
import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File

/**
 * Suppresses spurious native HAL/EGL error log spam (`E/platform: Failed to open rendernode: No such file or directory`)
 * emitted by `libEGL_mesa.so` / `gralloc.minigbm.so` in cloud streaming emulator containers that lack `/dev/dri/renderD128`.
 *
 * Strictly targets ONLY the `/dev/socket/logdw` socket descriptor (matched by its explicit peer path
 * `"/dev/socket/logdw"` or its `UnixSocketAddress` byte representation `47, 108, 111, 103, 100, 119`),
 * leaving all IPC, InputChannel, and system sockets untouched.
 */
object RenderNodeLogGuard {

    // ASCII byte representation of "/logdw" inside UnixSocketAddress.toString() -> Arrays.toString(sun_path)
    private const val LOGDW_ASCII_BYTES = "47, 108, 111, 103, 100, 119"

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        runCatching {
            val renderNode = File("/dev/dri/renderD128")
            val logdwSocket = File("/dev/socket/logdw")
            if (renderNode.exists() || !logdwSocket.exists()) {
                return
            }

            // Force liblog.so to open and cache its /dev/socket/logdw descriptor before scanning /proc/self/fd
            Log.d("ApexRenderGuard", "init")

            val fdDir = File("/proc/self/fd")
            val entries = fdDir.listFiles() ?: return
            val devNull = Os.open("/dev/null", OsConstants.O_WRONLY, 0)
            try {
                for (entry in entries) {
                    val fdNum = entry.name.toIntOrNull() ?: continue
                    if (fdNum <= 2) continue
                    val linkTarget = runCatching { Os.readlink(entry.absolutePath) }.getOrDefault("")
                    if (!linkTarget.startsWith("socket:[")) continue

                    val pfd = runCatching { ParcelFileDescriptor.fromFd(fdNum) }.getOrNull() ?: continue
                    try {
                        val peerAddr = runCatching { Os.getpeername(pfd.fileDescriptor) }.getOrNull()
                        val peerName = peerAddr?.toString().orEmpty()

                        val isLogdwPeer = peerName.contains("logdw", ignoreCase = true) ||
                            peerName.contains(LOGDW_ASCII_BYTES)

                        if (isLogdwPeer) {
                            Os.dup2(devNull, fdNum)
                            installed = true
                        }
                    } finally {
                        runCatching { pfd.close() }
                    }
                }
            } finally {
                runCatching { Os.close(devNull) }
            }
        }
    }
}

class ApexApplication : Application() {
    init {
        RenderNodeLogGuard.install()
    }

    override fun attachBaseContext(base: Context?) {
        RenderNodeLogGuard.install()
        super.attachBaseContext(base)
        RenderNodeLogGuard.install()
    }

    override fun onCreate() {
        RenderNodeLogGuard.install()
        super.onCreate()
        RenderNodeLogGuard.install()
    }
}
