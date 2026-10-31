
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

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val bgColor = Color.rgb(25, 24, 33)
    private val keyColor = Color.rgb(55, 52, 67)
    private val accentColor = Color.rgb(91, 72, 132)
    private val foregroundColor = Color.WHITE
    private val secondaryColor = Color.rgb(190, 185, 205)

    private var keyboard: LinearLayout? = null
    private var suggestionBar: LinearLayout? = null
    private var status: TextView? = null

    private var symbols = false
    private var symbolPage = 0
    private var emojis = false
    private var shifted = false
    private var capsLock = false
    private var languageIndex = 0

    private val languages = listOf(
        "auto", "english", "hinglish", "hindi", "gujarati"
    )

    private val dictionary = listOf(
        "about", "after", "again", "also", "always",
        "amazing", "and", "are", "because", "beautiful",
        "before", "best", "better", "bro", "can", "come",
        "could", "day", "definitely", "doing", "done",
        "everything", "excellent", "for", "from", "friend",
        "get", "going", "good", "great", "happy", "have",
        "hello", "help", "here", "hey", "how", "important",
        "just", "know", "like", "love", "make", "maybe",
        "more", "much", "need", "nice", "not", "now",
        "okay", "please", "really", "right", "should",
        "sorry", "thanks", "that", "the", "their", "there",
        "this", "today", "tomorrow", "very", "want", "was",
        "what", "when", "where", "which", "will", "with",
        "would", "you", "your"
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

        val saved = getSharedPreferences("typeflow", MODE_PRIVATE)
            .getString("language", "auto") ?: "auto"

        languageIndex = languages.indexOf(saved).let {
            if (it < 0) 0 else it
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun ic(): InputConnection? = currentInputConnection

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(7))
            setBackgroundColor(bgColor)
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
                toolKey(label) { runAi(task) },
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
            setTextColor(secondaryColor)
            setPadding(0, dp(3), 0, dp(3))
        }

        root.addView(status)

        suggestionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        root.addView(
            suggestionBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(37)
            )
        )

        keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(keyboard)

        renderKeyboard()
        updateSuggestions()

        return root
    }

    private fun toolKey(label: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(foregroundColor)
            setBackgroundColor(accentColor)
            setOnClickListener { action() }
        }
    }

    private fun key(
        label: String,
        weight: Float = 1f,
        height: Int = 49,
        action: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = if (label.length > 2) 12f else 19f
            typeface = Typeface.DEFAULT
            gravity = Gravity.CENTER
            setTextColor(foregroundColor)
            setBackgroundColor(keyColor)
            setOnClickListener { action() }

            layoutParams = LinearLayout.LayoutParams(
                0, dp(height), weight
            ).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }
        }
    }

    private fun newRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
    }

    private fun addCharacterRow(
        parent: LinearLayout,
        characters: String
    ) {
        val row = newRow()

        characters.forEach { original ->
            val shown = if (shifted || capsLock) {
                original.uppercaseChar()
            } else {
                original
            }

            row.addView(key(shown.toString()) {
                insert(shown.toString())

                if (shifted && !capsLock) {
                    shifted = false
                    renderKeyboard()
                }
            })
        }

        parent.addView(row)
    }

    private fun renderKeyboard() {
        val target = keyboard ?: return
        target.removeAllViews()

        when {
            emojis -> renderEmojiKeyboard(target)
            symbols -> renderSymbolKeyboard(target)
            else -> renderLetterKeyboard(target)
        }
    }

    private fun renderLetterKeyboard(target: LinearLayout) {

        // Number row
        val numbers = newRow()

        "1234567890".forEach { character ->
            numbers.addView(key(character.toString()) {
                insert(character.toString())
            })
        }

        target.addView(numbers)

        // QWERTY rows
        addCharacterRow(target, "qwertyuiop")
        addCharacterRow(target, "asdfghjkl")

        // Shift + bottom letter row + backspace
        val third = newRow()

        third.addView(
            key(if (capsLock) "⇪" else "⇧", 1.35f) {
                if (shifted) {
                    capsLock = !capsLock
                    shifted = capsLock
                } else {
                    shifted = true
                }
                renderKeyboard()
            }
        )

        "zxcvbnm".forEach { original ->
            val shown = if (shifted || capsLock) {
                original.uppercaseChar()
            } else {
                original
            }

            third.addView(key(shown.toString()) {
                insert(shown.toString())

                if (shifted && !capsLock) {
                    shifted = false
                    renderKeyboard()
                }
            })
        }

        third.addView(
            key("⌫", 1.35f) {
                backspace()
            }
        )

        target.addView(third)

        // Bottom row
        val bottom = newRow()

        bottom.addView(
            key("?123", 1.35f, 48) {
                symbols = true
                symbolPage = 0
                renderKeyboard()
            }
        )

        bottom.addView(
            key("☺", 0.9f, 48) {
                emojis = true
                renderKeyboard()
            }
        )

        bottom.addView(
            key(",", 0.7f, 48) {
                insert(",")
            }
        )

        val space = key(languageLabel(), 3.6f, 48) {
            autoCorrect()
            insert(" ")
        }

        space.setOnLongClickListener {
            cycleLanguage()
            true
        }

        bottom.addView(space)

        bottom.addView(
            key(".", 0.7f, 48) {
                insert(".")
            }
        )

        bottom.addView(
            key("↵", 1.1f, 48) {
                sendEnter()
            }
        )

        target.addView(bottom)
    }

    private fun renderSymbolKeyboard(target: LinearLayout) {
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
            val row = newRow()

            line.forEach { character ->
                row.addView(key(character.toString()) {
                    insert(character.toString())
                })
            }

            target.addView(row)
        }

        val bottom = newRow()

        bottom.addView(
            key("ABC", 1.3f, 48) {
                symbols = false
                renderKeyboard()
            }
        )

        bottom.addView(
            key("☺", 0.9f, 48) {
                emojis = true
                renderKeyboard()
            }
        )

        bottom.addView(
            key("=\\<", 1.1f, 48) {
                symbolPage = 1 - symbolPage
                renderKeyboard()
            }
        )

        bottom.addView(
            key("⌫", 1f, 48) {
                backspace()
            }
        )

        bottom.addView(
            key("Space", 2.5f, 48) {
                autoCorrect()
                insert(" ")
            }
        )

        bottom.addView(
            key("↵", 1f, 48) {
                sendEnter()
            }
        )

        target.addView(bottom)
    }

    private fun renderEmojiKeyboard(target: LinearLayout) {
        val emojiList = listOf(
            "😀", "😂", "🥹", "😍", "🥰", "😎", "😭", "😊",
            "🔥", "❤️", "✨", "👍", "🙏", "💯", "🎉", "🤔",
            "😅", "🤣", "😴", "🤝", "💀", "🚀", "🌟", "🙌",
            "😉", "😇", "🥳", "😢", "💖", "👏", "🤩", "🫡"
        )

        emojiList.chunked(8).forEach { group ->
            val row = newRow()

            group.forEach { emoji ->
                row.addView(key(emoji) {
                    insert(emoji)
                })
            }

            target.addView(row)
        }

        val bottom = newRow()

        bottom.addView(
            key("ABC", 1.5f, 48) {
                emojis = false
                renderKeyboard()
            }
        )

        bottom.addView(
            key("⌫", 1f, 48) {
                backspace()
            }
        )

        bottom.addView(
            key("Space", 2.5f, 48) {
                insert(" ")
            }
        )

        bottom.addView(
            key("↵", 1f, 48) {
                sendEnter()
            }
        )

        target.addView(bottom)
    }

    private fun insert(value: String) {
        ic()?.commitText(value, 1)
        updateSuggestions()
    }

    private fun backspace() {
        val input = ic() ?: return
        val selected = input.getSelectedText(0)

        if (!selected.isNullOrEmpty()) {
            input.commitText("", 1)
        } else {
            input.deleteSurroundingText(1, 0)
        }

        updateSuggestions()
    }

    private fun sendEnter() {
        val input = ic() ?: return

        val action = currentInputEditorInfo
            ?.imeOptions
            ?.and(EditorInfo.IME_MASK_ACTION) ?: 0

        if (!input.performEditorAction(action)) {
            input.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            )
            input.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
            )
        }
    }

    private fun languageLabel(): String {
        return when (languages[languageIndex]) {
            "auto" -> "Auto • hold"
            "english" -> "English • hold"
            "hinglish" -> "Hinglish • hold"
            "hindi" -> "हिंदी • hold"
            "gujarati" -> "ગુજરાતી • hold"
            else -> "Auto • hold"
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
        renderKeyboard()
    }

    private fun updateSuggestions() {
        val bar = suggestionBar ?: return
        bar.removeAllViews()

        val before = ic()
            ?.getTextBeforeCursor(100, 0)
            ?.toString()
            .orEmpty()

        val currentWord = Regex("[\\p{L}']+$")
            .find(before)
            ?.value
            .orEmpty()

        val options = if (currentWord.isBlank()) {
            listOf("Hello", "Thanks", "Okay")
        } else {
            val matches = dictionary.filter {
                it.startsWith(currentWord, ignoreCase = true) &&
                    !it.equals(currentWord, ignoreCase = true)
            }.take(3)

            if (matches.isEmpty()) {
                listOf(currentWord)
            } else {
                matches
            }
        }

        options.take(3).forEach { suggestion ->
            val view = TextView(this).apply {
                text = suggestion
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(secondaryColor)
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
        val input = ic() ?: return

        val before = input.getTextBeforeCursor(100, 0)
            ?.toString()
            .orEmpty()

        val word = Regex("[\\p{L}']+$")
            .find(before)
            ?.value ?: return

        input.deleteSurroundingText(word.length, 0)
        input.commitText(replacement, 1)

        updateSuggestions()
    }

    private fun autoCorrect() {
        val input = ic() ?: return

        val before = input.getTextBeforeCursor(100, 0)
            ?.toString()
            .orEmpty()

        val word = Regex("[\\p{L}']+$")
            .find(before)
            ?.value ?: return

        val corrected = corrections[word.lowercase(Locale.ROOT)]
            ?: return

        input.deleteSurroundingText(word.length, 0)
        input.commitText(corrected, 1)
    }

    private fun runAi(task: String) {
        val input = ic() ?: return

        val selected = input.getSelectedText(0)?.toString().orEmpty()
        val before = input.getTextBeforeCursor(500, 0)
            ?.toString()
            .orEmpty()

        val original = if (selected.isNotBlank()) selected else before

        if (original.isBlank()) {
            status?.text = "Type or select text first"
            return
        }

        val endpoint = getSharedPreferences("typeflow", MODE_PRIVATE)
            .getString("backend_url", "")
            .orEmpty()
            .trim()
            .trimEnd('/')

        if (!endpoint.startsWith("https://")) {
            status?.text = "Save your backend URL in TypeFlow"
            return
        }

        status?.text = "Gemini is working..."

        val language = languages[languageIndex]

        io.execute {
            var connection: HttpURLConnection? = null

            try {
                val request = URL("$endpoint/rewrite")
                    .openConnection() as HttpURLConnection

                connection = request

                request.requestMethod = "POST"
                request.connectTimeout = 15000
                request.readTimeout = 60000
                request.doOutput = true

                request.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )

                val payload = JSONObject()
                    .put("text", original)
                    .put("task", task)
                    .put("language", language)

                request.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = request.responseCode

                val stream = if (code in 200..299) {
                    request.inputStream
                } else {
                    request.errorStream
                }

                val responseText = stream?.bufferedReader()
                    ?.use { it.readText() }
                    ?: throw Exception("Empty server response")

                val response = JSONObject(responseText)

                if (code !in 200..299) {
                    throw Exception(
                        response.optString(
                            "error",
                            "Server error: HTTP $code"
                        )
                    )
                }

                val rewritten = response.optString("text").trim()

                if (rewritten.isBlank()) {
                    throw Exception("Gemini returned empty text")
                }

                main.post {
                    if (selected.isNotBlank()) {
                        input.commitText(rewritten, 1)
                    } else {
                        input.deleteSurroundingText(original.length, 0)
                        input.commitText(rewritten, 1)
                    }

                    status?.text = "Rewritten ✓"
                    updateSuggestions()
                }

            } catch (error: Exception) {
                main.post {
                    status?.text =
                        "AI error: ${error.message?.take(85) ?: "Try again"}"
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
