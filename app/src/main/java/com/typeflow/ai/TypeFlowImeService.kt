
package com.typeflow.ai

import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class TypeFlowImeService : InputMethodService() {

    private val io = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var status: TextView? = null

    private val bg = Color.rgb(25, 24, 34)
    private val keyBg = Color.rgb(55, 52, 68)

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun current(): InputConnection? {
        return currentInputConnection
    }

    override fun onCreateInputView(): View {

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(7), dp(5), dp(8))
            setBackgroundColor(bg)
        }

        val tools = listOf(
            "Rewrite",
            "Professional",
            "Casual",
            "Polite",
            "Shorten",
            "Expand"
        )

        val toolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        tools.forEach { label ->
            toolRow.addView(
                createToolButton(label) {
                    runAi(label.lowercase())
                },
                LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(4))
                }
            )
        }

        container.addView(toolRow)

        status = TextView(this).apply {
            text = "TypeFlow AI • Select text or place cursor"
            textSize = 11f
            setTextColor(0xFFBDB8CC.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        }

        container.addView(status)

        val keyboardRows = listOf(
            "qwertyuiop",
            "asdfghjkl",
            "⇧zxcvbnm⌫"
        )

        keyboardRows.forEach { row ->

            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            row.forEach { character ->

                val label = character.toString()

                val weight = if (
                    label == "⇧" || label == "⌫"
                ) 1.45f else 1f

                line.addView(
                    createKeyButton(label) {
                        handleKey(label)
                    },
                    LinearLayout.LayoutParams(0, dp(51), weight).apply {
                        setMargins(dp(2), dp(3), dp(2), dp(3))
                    }
                )
            }

            container.addView(line)
        }

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val bottomKeys = listOf(
            "?123",
            ",",
            "English",
            ".",
            "↵"
        )

        bottomKeys.forEachIndexed { index, label ->

            bottomRow.addView(
                createKeyButton(label) {
                    when (index) {
                        0 -> insert("?123")
                        1 -> insert(",")
                        2 -> insert(" ")
                        3 -> insert(".")
                        4 -> sendEnter()
                    }
                },
                LinearLayout.LayoutParams(
                    0,
                    dp(48),
                    if (index == 2) 4f else 1f
                ).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            )
        }

        container.addView(bottomRow)

        return container
    }

    private fun createToolButton(
        label: String,
        action: () -> Unit
    ): Button {

        return Button(this).apply {
            text = label
            textSize = 9.5f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF51456D.toInt())
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { action() }
        }
    }

    private fun createKeyButton(
        label: String,
        action: () -> Unit
    ): TextView {

        return TextView(this).apply {
            text = label
            textSize = if (label.length > 2) 12f else 19f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(keyBg)
            setOnClickListener { action() }
        }
    }

    private fun insert(value: String) {
        current()?.commitText(value, 1)
    }

    private fun sendEnter() {

        val connection = current() ?: return

        val action = currentInputEditorInfo
            ?.imeOptions
            ?.and(EditorInfo.IME_MASK_ACTION) ?: 0

        val handled = connection.performEditorAction(action)

        if (!handled) {
            connection.commitText("\n", 1)
        }
    }

    private fun handleKey(key: String) {

        val connection = current() ?: return

        when (key) {

            "⌫" -> {
                if (!connection.deleteSurroundingText(1, 0)) {
                    connection.sendKeyEvent(
                        KeyEvent(
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_DEL
                        )
                    )
                }
            }

            "⇧" -> {
                // Shift key placeholder
            }

            else -> insert(key)
        }
    }

    private fun runAi(task: String) {

        val connection = current() ?: return

        val selectedText = connection
            .getSelectedText(0)
            ?.toString()
            .orEmpty()

        val textBeforeCursor = connection
            .getTextBeforeCursor(1200, 0)
            ?.toString()
            .orEmpty()

        val inputText = if (selectedText.isNotBlank()) {
            selectedText
        } else {
            textBeforeCursor.takeLast(700)
        }

        if (inputText.isBlank()) {
            status?.text = "Select text or type a sentence first"
            return
        }

        val preferences = getSharedPreferences(
            "typeflow",
            MODE_PRIVATE
        )

        val endpoint = preferences
            .getString("backend_url", "")
            .orEmpty()
            .trim()
            .trimEnd('/')

        if (!endpoint.startsWith("https://")) {
            status?.text = "Set your HTTPS backend URL in TypeFlow app"
            return
        }

        status?.text = "Gemini is working..."

        io.execute {

            var connectionHttp: HttpURLConnection? = null

            try {

                connectionHttp = (
                    URL("$endpoint/rewrite")
                        .openConnection() as HttpURLConnection
                ).apply {
                    requestMethod = "POST"
                    connectTimeout = 12000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/json"
                    )
                }

                val payload = JSONObject()
                    .put("text", inputText)
                    .put("task", task)
                    .toString()

                connectionHttp.outputStream.use {
                    it.write(payload.toByteArray(Charsets.UTF_8))
                }

                val responseCode = connectionHttp.responseCode

                val responseStream = if (responseCode in 200..299) {
                    connectionHttp.inputStream
                } else {
                    connectionHttp.errorStream
                }

                val responseBody = responseStream
                    .bufferedReader()
                    .use { it.readText() }

                val result = JSONObject(responseBody)
                    .optString("text")

                if (result.isBlank()) {
                    throw Exception("Empty response from AI server")
                }

                mainHandler.post {

                    if (selectedText.isNotBlank()) {
                        connection.commitText(result, 1)
                    } else {
                        connection.deleteSurroundingText(
                            inputText.length,
                            0
                        )
                        connection.commitText(result, 1)
                    }

                    status?.text = "Done ✓"
                }

            } catch (error: Exception) {

                mainHandler.post {
                    status?.text =
                        "AI error: ${error.message?.take(70) ?: "Check backend"}"
                }

            } finally {
                connectionHttp?.disconnect()
            }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
