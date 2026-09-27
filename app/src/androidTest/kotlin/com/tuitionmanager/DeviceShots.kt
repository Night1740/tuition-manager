package com.tuitionmanager

import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream

/**
 * With `-e capture true`, ask the host to run `adb exec-out screencap`.
 * UiAutomation and PixelCopy frames are empty on this headless emulator.
 * The signal files use `touch` and `ls` because [android.app.UiAutomation.executeShellCommand]
 * does not run a shell, so redirection would be ignored.
 */
internal fun captureNamedShot(name: String) {
    if (InstrumentationRegistry.getArguments().getString("capture") != "true") return
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    fun shell(command: String): String {
        val pipe = automation.executeShellCommand(command)
        return FileInputStream(pipe.fileDescriptor).bufferedReader().use { it.readText() }.also {
            pipe.close()
        }
    }
    shell("rm /data/local/tmp/tuition-ack-$name")
    shell("touch /data/local/tmp/tuition-need-$name")
    val deadline = System.currentTimeMillis() + 15_000
    while (System.currentTimeMillis() < deadline) {
        if (shell("ls /data/local/tmp/tuition-ack-$name").contains("tuition-ack-$name")) return
        Thread.sleep(200)
    }
    error("Timed out waiting for host screenshot $name")
}
