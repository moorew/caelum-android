package de.astronarren.allsky.data

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Drives the optional focus motor. Two transports are supported:
 *
 *   * [FocusTransport.SSH] — opens an SSH connection (via the maintained
 *     mwiede/jsch fork) and runs `python3 <scriptPath> <direction> <steps>`.
 *     Requires only sshd + python on the Pi, which every stock Allsky build
 *     already has.
 *   * [FocusTransport.HTTP] — issues a GET against a user-supplied endpoint,
 *     substituting `{direction}` and `{steps}` placeholders. Useful when
 *     someone has wrapped the script in a tiny Flask/PHP shim, or when SSH
 *     is unavailable on a restricted network.
 *
 * Both run on Dispatchers.IO so the call site can `await` without blocking
 * Compose.
 */
class FocusController(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {

    enum class Direction(val token: String) { FORWARD("forward"), BACKWARD("backward") }

    data class Result(val success: Boolean, val output: String)

    suspend fun move(settings: FocusSettings, direction: Direction, steps: Int): Result {
        return when (settings.transport) {
            FocusTransport.SSH -> moveOverSsh(settings, direction, steps)
            FocusTransport.HTTP -> moveOverHttp(settings, direction, steps)
        }
    }

    private suspend fun moveOverSsh(settings: FocusSettings, direction: Direction, steps: Int): Result =
        withContext(Dispatchers.IO) {
            if (settings.host.isBlank()) {
                return@withContext Result(false, "SSH host is empty — set it in Focus settings.")
            }
            val jsch = JSch()
            val session = jsch.getSession(
                settings.username.ifBlank { "pi" },
                settings.host,
                if (settings.port > 0) settings.port else 22
            )
            session.setPassword(settings.password)
            // Skip strict host key checking — we're inside the user's own
            // LAN (or a Tailscale tunnel) and the keystore on the device
            // isn't really meaningful here. We trade strict-host for
            // friction-free setup.
            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive,publickey")
            session.setServerAliveInterval(15_000)
            session.connect(15_000)

            try {
                val command = buildString {
                    append("python3 ")
                    append(settings.scriptPath)
                    append(" ")
                    append(direction.token)
                    append(" ")
                    append(steps)
                }
                val channel = session.openChannel("exec") as ChannelExec
                channel.setCommand(command)
                val stdout = ByteArrayOutputStream()
                val stderr = ByteArrayOutputStream()
                channel.outputStream = stdout
                channel.setErrStream(stderr)
                channel.connect(10_000)
                // Wait for the channel to close — focus moves typically
                // complete in well under a second per 512 steps.
                val waitDeadline = System.currentTimeMillis() + 15_000
                while (!channel.isClosed && System.currentTimeMillis() < waitDeadline) {
                    Thread.sleep(50)
                }
                val exit = channel.exitStatus
                channel.disconnect()
                val combined = (stdout.toString() + stderr.toString()).trim()
                Result(
                    success = exit == 0,
                    output = if (combined.isBlank()) "Exit $exit" else combined
                )
            } finally {
                session.disconnect()
            }
        }

    private suspend fun moveOverHttp(settings: FocusSettings, direction: Direction, steps: Int): Result =
        withContext(Dispatchers.IO) {
            val template = settings.httpEndpoint
            if (template.isBlank()) {
                return@withContext Result(false, "HTTP endpoint is empty — set it in Focus settings.")
            }
            val expanded = template
                .replace("{direction}", direction.token)
                .replace("{steps}", steps.toString())
            if (expanded.toHttpUrlOrNull() == null) {
                return@withContext Result(false, "Endpoint is not a valid URL: $expanded")
            }
            try {
                val request = Request.Builder().url(expanded).get().build()
                okHttpClient.newCall(request).execute().use { resp ->
                    val body = resp.body?.string()?.take(200).orEmpty()
                    Result(
                        success = resp.isSuccessful,
                        output = if (body.isBlank()) "HTTP ${resp.code}" else "HTTP ${resp.code} — $body"
                    )
                }
            } catch (e: Exception) {
                Result(false, e.message ?: "HTTP call failed")
            }
        }
}
