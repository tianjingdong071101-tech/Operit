package com.ai.assistance.operit.ui.features.toolbox.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.data.mcp.plugins.MCPSharedSession
import com.ai.assistance.operit.ui.features.token.webview.WebViewConfig
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "OpenCodeWebScreen"
private const val OPENCODE_WEB_PORT = 12749
private const val OPENCODE_WEB_URL = "http://127.0.0.1:$OPENCODE_WEB_PORT"
private const val POLL_INTERVAL_MS = 500L
private const val POLL_TIMEOUT_MS = 15000L

@Composable
fun OpenCodeWebScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val webView = remember { WebViewConfig.createWebView(context) }

    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isStopped by remember { mutableStateOf(false) }

    fun startServer() {
        scope.launch {
            try {
                isLoading = true
                isError = false
                isStopped = false

                val sessionId = MCPSharedSession.getOrCreateSharedSession(context)
                if (sessionId == null) {
                    isError = true
                    errorMessage = context.getString(R.string.opencode_web_start_failed)
                    return@launch
                }

                val terminal = Terminal.getInstance(context)

                val checkResult = terminal.executeCommand(
                    sessionId,
                    "curl -s -o /dev/null -w '%{http_code}' $OPENCODE_WEB_URL 2>/dev/null || echo FAIL"
                )

                if (checkResult == null || !checkResult.contains("200")) {
                    terminal.executeCommand(
                        sessionId,
                        "nohup opencode web --port $OPENCODE_WEB_PORT --hostname 127.0.0.1 > /tmp/opencode-web.log 2>&1 &"
                    )

                    val startTime = System.currentTimeMillis()
                    var started = false
                    while (System.currentTimeMillis() - startTime < POLL_TIMEOUT_MS) {
                        delay(POLL_INTERVAL_MS)
                        val pollResult = terminal.executeCommand(
                            sessionId,
                            "curl -s -o /dev/null -w '%{http_code}' $OPENCODE_WEB_URL 2>/dev/null || echo FAIL"
                        )
                        if (pollResult != null && pollResult.contains("200")) {
                            started = true
                            break
                        }
                    }

                    if (!started) {
                        isError = true
                        errorMessage = context.getString(R.string.opencode_web_start_failed)
                        return@launch
                    }
                }

                webView.loadUrl(OPENCODE_WEB_URL)
                isLoading = false
            } catch (e: Exception) {
                AppLogger.e(TAG, "启动opencode web失败: ${e.message}", e)
                isError = true
                errorMessage = e.message ?: context.getString(R.string.opencode_web_start_failed)
            }
        }
    }

    fun stopServer() {
        scope.launch {
            try {
                val sessionId = MCPSharedSession.getOrCreateSharedSession(context)
                if (sessionId != null) {
                    val terminal = Terminal.getInstance(context)
                    terminal.executeCommand(
                        sessionId,
                        "kill \$(lsof -ti:$OPENCODE_WEB_PORT) 2>/dev/null"
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "停止opencode web失败: ${e.message}", e)
            }
            isStopped = true
            isLoading = false
            isError = false
            webView.loadUrl("about:blank")
        }
    }

    LaunchedEffect(Unit) {
        startServer()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { webView.reload() }) {
                Icon(Icons.Default.Refresh, stringResource(R.string.opencode_web_refresh))
            }
            IconButton(onClick = { stopServer() }) {
                Icon(Icons.Default.Close, stringResource(R.string.opencode_web_stop))
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (isStopped) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.opencode_web_stopped),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { startServer() }) {
                            Text(stringResource(R.string.opencode_web_restart))
                        }
                    }
                }
            } else if (isLoading && !isError) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.opencode_web_starting))
                    }
                }
            } else if (isError) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { startServer() }) {
                            Text(stringResource(R.string.opencode_web_retry))
                        }
                    }
                }
            } else {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
