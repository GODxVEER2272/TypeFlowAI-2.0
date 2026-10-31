
package com.typeflow.ai

import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class TypeFlowImeService : InputMethodService() {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private val background = Color.rgb(25, 24, 32)
    private val keyColor = Color.rgb(54, 51, 65)
    private val accent = Color.rgb(112, 87, 160)
    private val textColor = Color.WHITE
    private val muted = Color.rgb(185, 180, 200)

    private var status: TextView? = null
    private var suggestions: LinearLayout? = null
    private var keyboard: LinearLayout? = null

    private var symbolMode = false
    private var symbolPage = 0
    private var emojiMode = false
    private var shifted = false
    private var capsLock = false

    private val languages = listOf(
        "auto", "english", "hinglish", "hindi", "gujarati"
    )

    private var languageIndex = 0

    private val dictionary = listOf(
        "about", "after", "again", "also", "always",
        "amazing", "and", "are", "because", "beautiful",
        "before", "best", "better", "bro", "can", "come",
        "could", "day", "definitely", "do", "doing",
        "done", "everything", "excellent", "for", "from",
        "friend", "get", "going", "good", "great", "happy",
        "have", "hello", "help", "here", "hey", "how",
        "important", "just", "know", "like", "love",
        "make", "maybe", "more", "much", "need", "nice",
        "not", "now", "okay", "please", "really", "right",
        "should", "sorry", "thanks", "that", "the", "their",
        "there", "this", "today", "tomorrow", "very",
        "want", "was", "what", "when", "where", "which",
        "will", "with", "would", "you", "your"
    )

    private val corrections = mapOf(
        "teh" to "the",
        "adn" to "and",
        "thier" to "their",
        "recieve" to "receive",
        "becuase" to "because",
        "definately" to "definitely",
        "whta" to "what",
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "im" to "I'm",
        "ive" to "I've",
        "ur" to "your"
    )

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences("typeflow", MODE_PRIVATE)
        val savedLanguage = prefs.getString("language", "auto") ?: "auto"

        languageIndex = languages.indexOf(savedLanguage)
            .let { if (it < 0) 0 else it }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun connection(): InputConnection? =
        currentInputConnection

    override fun onCreateInputView(): View {
        return buildKeyboard()
    }

    private fun buildKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(7))
            setBackgroundColor(background)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val actions = listOf(
            "Rewrite" to "rewrite",
            "Professional" to "professional",
            "Casual" to "casual",
            "Polite" to "polite",
            "Shorten" to "shorten",
            "Expand" to "expand"
        )

        actions.forEach { (label, task) ->
            toolbar.addView(
                toolButton(label) { runAi(task) },
                LinearLayout.LayoutParams(0, dp(43), 1f).apply {
                    setMargins(dp(1), dp(2), dp(1), dp(3))
                }
            )
        }

        root.addView(toolbar)

        status = TextView(this).apply {
            text = "TypeFlow AI 2.1"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(3), 0, dp(4))
        }

        root.addView(status)

        suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        root.addView(
            suggestions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(38)
            )
        )

        keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(keyboard)

        renderKeys()
        updateSuggestions()

        return root
    }

    private fun toolButton(label: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setBackgroundColor(accent)
            setOnClickListener { action() }
        }
    }

    private fun makeKey(
        label: String,
        action: () -> Unit,
        weight: Float = 1f,
        height: Int = 49,
        longAction: (() -> Unit)? = null
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = if (label.length > 2) 12f else 19f
            typeface = Typeface.DEFAULT
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setBackgroundColor(keyColor)
            setOnClickListener { action() }

            if (longAction != null) {
                setOnLongClickListener {
                    longAction()
                    true
                }
            }

            layoutParams = LinearLayout.LayoutParams(
                0, dp(height), weight
            ).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }
        }
    }

    private fun addRow(
        parent: LinearLayout,
        keys: List<Triple<String, Float, () -> Unit>>
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        keys.forEach { (label, weight, action) ->
            row.addView(makeKey(label, action, weight))
        }

        parent.addView(row)
    }

    private fun renderKeys() {
        val target = keyboard ?: return
        target.removeAllViews()

        if (emojiMode) {
            renderEmoji(target)
            return
        }

        if (symbolMode) {
            renderSymbols(target)
            return
        }

        val numberRow = "1234567890".map { it.toString() }

        addRow(target, numberRow.map { key ->
            Triple(key, 1f) { insert(key) }
        })

        val rows = listOf(
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm"
        )

        rows.forEachIndexed { index, rowText ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            if (index == 2) {
                row.addView(
                    makeKey(
                        if (capsLock) "⇪" else "⇧",
                        { toggleShift() },
                        1.35f
                    )
                )
            }

            rowText.forEach { char ->
                val character = if (shifted || capsLock) {
                    char.uppercaseChar()
                } else {
                    char
                }

                row.addView(
                    makeKey(character.toString(), {
                        insert(character.toString())
                        if (shifted && !capsLock) {
                            shifted = false
                            renderKeys()
                        }
                    })
                )
            }

            if (index == 2) {
                row.addView(
                    makeKey("⌫", { backspace() }, 1.35f, 49) {
                        connection()?.deleteSurroundingText(10, 0)
                        updateSuggestions()
                    }
                )
            }

            target.addView(row)
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        bottom.addView(
            makeKey("?123", {
                symbolMode = true
                symbolPage = 0
                renderKeys()
            }, 1.35f, 48)
        )

        bottom.addView(
            makeKey("☺", {
                emojiMode = true
                renderKeys()
            }, 1f, 48)
        )

        bottom.addView(
            makeKey(",", { insert(",") }, 0.8f, 48)
        )

        bottom.addView(
            makeKey(languageLabel(), {
                cycleLanguage()
            }, 3.8f, 48)
        )

        bottom.addView(
            makeKey(".", { insert(".") }, 0.8f, 48)
        )

        bottom.addView(
            makeKey("↵", { sendEnter() }, 1.2f, 48)
        )

        target.addView(bottom)
    }

    private fun renderSymbols(target: LinearLayout) {
        val pages = listOf(
            listOf(
                "1234567890",
                "@#\$%&-*+()",
                "!\"':;/?",
                ".,<>[]{}"
            ),
            listOf(
                "1234567890",
                "~`|•√π÷×¶",
                "€£¥^°={}\\",
                ".,!?;:_"
            )
        )

        pages[symbolPage].forEach { line ->
            addRow(target, line.map { char ->
                Triple(char.toString(), 1f) {
                    insert(char.toString())
                }
            })
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        bottom.addView(
            makeKey("ABC", {
                symbolMode = false
                renderKeys()
            }, 1.5f, 48)
        )

        bottom.addView(
            makeKey("☺", {
                emojiMode = true
                renderKeys()
            }, 1f, 48)
        )

        bottom.addView(
            makeKey("=\\<", {
                symbolPage = 1 - symbolPage
                renderKeys()
            }, 1.5f, 48)
        )

        bottom.addView(
            makeKey("⌫", { backspace() }, 1f, 48)
        )

        bottom.addView(
            makeKey("Space", { insert(" ") }, 3f, 48)
        )

        bottom.addView(
            makeKey("↵", { sendEnter() }, 1f, 48)
        )

        target.addView(bottom)
    }

    private fun renderEmoji(target: LinearLayout) {
        val emojis = listOf(
            "😀","😂","🥹","😍","🥰","😎","😭","😊",
            "🔥","❤️","✨","👍","🙏","💯","🎉","🤔",
            "😅","🤣","😴","🤝","💀","🚀","🌟","🙌",
            "😉","😇","🥳","😢","💖","👏","🤩","🫡"
        )

        emojis.chunked(8).forEach { group ->
            addRow(target, group.map { emoji ->
                Triple(emoji, 1f) { insert(emoji) }
            })
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        bottom.addView(
            makeKey("ABC", {
                emojiMode = false
                renderKeys()
            }, 2f, 48)
        )

        bottom.addView(
            makeKey("⌫", { backspace() }, 1f, 48)
        )

        bottom.addView(
            makeKey("Space", { insert(" ") }, 3f, 48)
        )

        bottom.addView(
            makeKey("↵", { sendEnter() }, 1f, 48)
        )

        target.addView(bottom)
    }

    private fun insert(value: String) {
        connection()?.commitText(value, 1)
        updateSuggestions()
    }

    private fun backspace() {
        val ic = connection() ?: return

        val selected = ic.getSelectedText(0)

        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }

        updateSuggestions()
    }

    private fun toggleShift() {
        if (shifted) {
            capsLock = !capsLock
            shifted = capsLock
        } else {
            shifted = true
        }

        renderKeys()
    }

    private fun sendEnter() {
        val ic = connection() ?: return
        val action = currentInputEditorInfo
            ?.imeOptions
            ?.and(EditorInfo.IME_MASK_ACTION) ?: 0

        if (!ic.performEditorAction(action)) {
            ic.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            )
            ic.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
            )
        }
    }

    private fun languageLabel(): String {
        return when (languages[languageIndex]) {
            "auto" -> "Auto"
            "english" -> "EN"
            "hinglish" -> "Hinglish"
            "hindi" -> "हिंदी"
            "gujarati" -> "ગુજરાતી"
            else -> "Auto"
        }
    }

    private fun cycleLanguage() {
        languageIndex = (languageIndex + 1) % languages.size

        val language = languages[languageIndex]

        getSharedPreferences("typeflow", MODE_PRIVATE)
            .edit()
            .putString("language", language)
            .apply()

        status?.text = "Language: ${languageLabel()}"
        renderKeys()
    }

    private fun updateSuggestions() {
        val bar = suggestions ?: return
        bar.removeAllViews()

        val before = connection()
            ?.getTextBeforeCursor(80, 0)
            ?.toString()
            .orEmpty()

        val word = Regex("[\\p{L}']+$")
            .find(before)
            ?.value
            .orEmpty()

        val options = if (word.isBlank()) {
            listOf("Hello", "Thanks", "Okay")
        } else {
            val matches = dictionary.filter {
                it.startsWith(word, ignoreCase = true) &&
                    !it.equals(word, ignoreCase = true)
            }.take(3)

            (matches + listOf(word)).distinct().take(3)
        }

        options.forEach { suggestion ->
            val view = TextView(this).apply {
                text = suggestion
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(muted)
                setOnClickListener {
                    replaceCurrentWord(suggestion)
                }
            }

            bar.addView(
                view,
                LinearLayout.LayoutParams(0, dp(35), 1f)
            )
        }
    }

    private fun replaceCurrentWord(replacement: String) {
        val ic = connection() ?: return

        val before = ic.getTextBeforeCursor(80, 0)
            ?.toString()
            .orEmpty()

        val word = Regex("[\\p{L}']+$")
            .find(before)
            ?.value
            ?: return

        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(replacement, 1)
        updateSuggestions()
    }

    private fun autoCorrect() {
        val ic = connection() ?: return

        val before = ic.getTextBeforeCursor(80, 0)
            ?.toString()
            .orEmpty()

        val word = Regex("[\\p{L}']+$")
            .find(before)
            ?.value
            ?: return

        val corrected = corrections[word.lowercase(Locale.ROOT)]
            ?: return

        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(corrected, 1)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            autoCorrect()
            return super.onKeyDown(keyCode, event)
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun runAi(task: String) {
        val ic = connection() ?: return

        val selected = ic.getSelectedText(0)?.toString().orEmpty()
        val before = ic.getTextBeforeCursor(500, 0)?.toString().orEmpty()

        val inputText = if (selected.isNotBlank()) {
            selected
        } else {
            before
        }

        if (inputText.isBlank()) {
            status?.text = "Type or select some text first"
            return
        }

        val prefs = getSharedPreferences("typeflow", MODE_PRIVATE)

        val endpoint = prefs.getString("backend_url", "")
            .orEmpty()
            .trim()
            .trimEnd('/')

        if (!endpoint.startsWith("https://")) {
            status?.text = "Save your HTTPS backend URL in TypeFlow"
            return
        }

        status?.text = "AI is rewriting..."

        executor.execute {
            var http: HttpURLConnection? = null

            try {
                http = (URL("$endpoint/rewrite").openConnection()
                    as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                    )
                }

                val payload = JSONObject()
                    .put("text", inputText)
                    .put("task", task)
                    .put("language", languages[languageIndex])

                http.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = http.responseCode

                val stream = if (code in 200..299) {
                    http.inputStream
                } else {
                    http.errorStream
                }

                val response = stream?.bufferedReader()
                    ?.use { it.readText() }
                    ?: throw Exception("Empty server response")

                val resultJson = JSONObject(response)

                if (code !in 200..299) {
                    throw Exception(
                        resultJson.optString(
                            "error",
                            "Server error: HTTP $code"
                        )
                    )
                }

                val rewritten = resultJson.optString("text").trim()

                if (rewritten.isBlank()) {
                    throw Exception("AI returned empty text")
                }

                handler.post {
                    if (selected.isNotBlank()) {
                        ic.commitText(rewritten, 1)
                    } else {
                        ic.deleteSurroundingText(inputText.length, 0)
                        ic.commitText(rewritten, 1)
                    }

                    status?.text = "Rewritten ✓"
                    updateSuggestions()
                }

            } catch (error: Exception) {
                handler.post {
                    status?.text =
                        "AI error: ${error.message?.take(90) ?: "Try again"}"
                }
            } finally {
                http?.disconnect()
            }
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
