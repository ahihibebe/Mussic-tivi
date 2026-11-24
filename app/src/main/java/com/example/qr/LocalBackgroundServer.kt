package com.example.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

class LocalBackgroundServer(
    private val context: Context,
    private val onImageUploaded: (String) -> Unit,
    private val onStatusUpdate: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    var serverUrl: String? = null
        private set

    fun start(): String {
        stop()
        val ip = getLocalIpv4Address() ?: "127.0.0.1"
        val socket = try {
            ServerSocket(8765)
        } catch (e: Exception) {
            ServerSocket(0)
        }
        serverSocket = socket
        val port = socket.localPort
        val url = "http://$ip:$port"
        serverUrl = url

        onStatusUpdate("Đang mở cổng nhận ảnh tại $url")

        acceptJob = scope.launch {
            while (isActive && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    launch { handleClient(client) }
                } catch (e: Exception) {
                    if (!socket.isClosed) {
                        Log.e("LocalBgServer", "Accept error", e)
                    }
                }
            }
        }
        return url
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {
        }
        serverSocket = null
        serverUrl = null
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 30000
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val path = parts[1].substringBefore("?")

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                }
            }

            when {
                method == "GET" && path == "/" -> {
                    val html = buildUploadPageHtml()
                    sendResponse(output, 200, "OK", "text/html; charset=utf-8", html.toByteArray(StandardCharsets.UTF_8))
                }

                method == "POST" && path == "/upload" -> {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    if (contentLength <= 0 || contentLength > 25 * 1024 * 1024) {
                        sendResponse(
                            output,
                            400,
                            "Bad Request",
                            "application/json; charset=utf-8",
                            """{"ok":false,"error":"Kích thước ảnh không hợp lệ (tối đa 25MB)"}""".toByteArray(StandardCharsets.UTF_8)
                        )
                        return
                    }

                    val bodyBytes = readExactBytes(input, contentLength)
                    val savedPath = saveUploadedImage(bodyBytes)
                    if (savedPath != null) {
                        onStatusUpdate("Đã nhận ảnh nền mới từ điện thoại!")
                        onImageUploaded(savedPath)
                        sendResponse(
                            output,
                            200,
                            "OK",
                            "application/json; charset=utf-8",
                            """{"ok":true,"message":"Đổi ảnh nền Tivi thành công!"}""".toByteArray(StandardCharsets.UTF_8)
                        )
                    } else {
                        sendResponse(
                            output,
                            400,
                            "Bad Request",
                            "application/json; charset=utf-8",
                            """{"ok":false,"error":"Không thể giải mã tệp ảnh"}""".toByteArray(StandardCharsets.UTF_8)
                        )
                    }
                }

                method == "OPTIONS" -> {
                    sendResponse(output, 204, "No Content", "text/plain", ByteArray(0))
                }

                else -> {
                    sendResponse(output, 404, "Not Found", "text/plain; charset=utf-8", "Not Found".toByteArray())
                }
            }
        }
    }

    private fun saveUploadedImage(rawBytes: ByteArray): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // Downscale gracefully if larger than 2560px to stay smooth on TV memory
            var sampleSize = 1
            val maxDim = 2560
            while ((bounds.outWidth / sampleSize) > maxDim || (bounds.outHeight / sampleSize) > maxDim) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return null

            // Delete old custom background files so storage stays clean
            context.filesDir.listFiles { f ->
                f.name.startsWith("custom_bg_") && f.name.endsWith(".jpg")
            }?.forEach { it.delete() }

            val outFile = File(context.filesDir, "custom_bg_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            bitmap.recycle()
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e("LocalBgServer", "Failed saving uploaded image", e)
            null
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            if (b == '\n'.code) {
                val len = sb.length
                if (len > 0 && sb[len - 1] == '\r') {
                    sb.deleteCharAt(len - 1)
                }
                return sb.toString()
            }
            sb.append(b.toChar())
        }
    }

    private fun readExactBytes(input: InputStream, length: Int): ByteArray {
        val baos = ByteArrayOutputStream(length)
        val buffer = ByteArray(8192)
        var remaining = length
        while (remaining > 0) {
            val toRead = minOf(buffer.size, remaining)
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            baos.write(buffer, 0, read)
            remaining -= read
        }
        return baos.toByteArray()
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray
    ) {
        val header = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.US_ASCII))
        if (body.isNotEmpty()) {
            output.write(body)
        }
        output.flush()
    }

    private fun getLocalIpv4Address(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val candidates = mutableListOf<Pair<Int, String>>()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                val priority = when {
                    name.startsWith("wlan") || name.startsWith("wifi") -> 0
                    name.startsWith("eth") || name.startsWith("en") -> 1
                    else -> 2
                }
                for (addr in intf.inetAddresses.toList()) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (!host.startsWith("127.")) {
                            candidates.add(priority to host)
                        }
                    }
                }
            }
            candidates.minByOrNull { it.first }?.second
        } catch (e: Exception) {
            null
        }
    }

    private fun buildUploadPageHtml(): String = """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0" />
          <title>TV Music - Gửi Ảnh Nền Lên Tivi</title>
          <style>
            * { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
            body { margin: 0; padding: 20px; background: #f1f5f9; color: #0f172a; display: flex; justify-content: center; }
            .card { width: 100%; max-width: 440px; background: #ffffff; border-radius: 20px; padding: 24px; box-shadow: 0 10px 30px rgba(15,23,42,0.08); }
            h1 { font-size: 20px; margin: 0 0 6px; }
            p { font-size: 14px; color: #475569; margin: 0 0 20px; line-height: 1.5; }
            .drop { display: block; border: 2px dashed #93c5fd; background: #eff6ff; border-radius: 16px; padding: 24px 16px; text-align: center; color: #1d4ed8; font-weight: 600; cursor: pointer; margin-bottom: 16px; }
            input[type=file] { display: none; }
            #preview { width: 100%; max-height: 260px; object-fit: cover; border-radius: 12px; display: none; margin-bottom: 16px; border: 1px solid #e2e8f0; }
            button { width: 100%; padding: 14px; border: none; border-radius: 14px; background: #1d4ed8; color: #fff; font-size: 16px; font-weight: 700; cursor: pointer; }
            button:disabled { background: #94a3b8; cursor: not-allowed; }
            .status { margin-top: 14px; font-size: 14px; font-weight: 600; text-align: center; min-height: 20px; }
            .ok { color: #047857; }
            .err { color: #be123c; }
          </style>
        </head>
        <body>
          <div class="card">
            <h1>Đổi Ảnh Nền TV Music</h1>
            <p>Chọn ảnh từ thư viện điện thoại của bạn để đặt làm hình nền trực tiếp cho ứng dụng nghe nhạc trên Tivi.</p>
            <label class="drop" for="fileInput">📷 Chọn ảnh từ điện thoại</label>
            <input id="fileInput" type="file" accept="image/*" />
            <img id="preview" alt="Xem trước ảnh" />
            <button id="sendBtn" disabled>Gửi lên Tivi ngay</button>
            <div id="status" class="status"></div>
          </div>
          <script>
            const fileInput = document.getElementById('fileInput');
            const preview = document.getElementById('preview');
            const sendBtn = document.getElementById('sendBtn');
            const statusEl = document.getElementById('status');
            let selectedFile = null;

            fileInput.addEventListener('change', (e) => {
              const file = e.target.files[0];
              if (!file) return;
              selectedFile = file;
              preview.src = URL.createObjectURL(file);
              preview.style.display = 'block';
              sendBtn.disabled = false;
              statusEl.textContent = '';
              statusEl.className = 'status';
            });

            sendBtn.addEventListener('click', async () => {
              if (!selectedFile) return;
              sendBtn.disabled = true;
              sendBtn.textContent = 'Đang gửi ảnh lên Tivi...';
              statusEl.textContent = '';
              try {
                const res = await fetch('/upload', {
                  method: 'POST',
                  headers: { 'Content-Type': selectedFile.type || 'application/octet-stream' },
                  body: selectedFile
                });
                const data = await res.json();
                if (res.ok && data.ok) {
                  statusEl.textContent = '✅ ' + data.message;
                  statusEl.className = 'status ok';
                } else {
                  statusEl.textContent = '❌ ' + (data.error || 'Tải lên thất bại');
                  statusEl.className = 'status err';
                }
              } catch (err) {
                statusEl.textContent = '❌ Mất kết nối với Tivi. Hãy kiểm tra cùng mạng Wi-Fi.';
                statusEl.className = 'status err';
              } finally {
                sendBtn.disabled = false;
                sendBtn.textContent = 'Gửi lên Tivi ngay';
              }
            });
          </script>
        </body>
        </html>
    """.trimIndent()
}
