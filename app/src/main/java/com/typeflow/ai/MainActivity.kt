package com.typeflow.ai

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import android.graphics.Color
import android.view.ViewGroup

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("typeflow", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 24); setBackgroundColor(Color.rgb(18,18,28)) }
        fun text(s: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply { text=s; textSize=size; setTextColor(color); setPadding(0,12,0,12) }
        root.addView(text("TypeFlow AI", 30f))
        root.addView(text("Your writing keyboard, powered by Gemini.", 16f, 0xFFBDB8CC.toInt()))
        root.addView(text("1. Enable the keyboard", 18f))
        root.addView(Button(this).apply { text="Open keyboard settings"; setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } })
        root.addView(text("2. Select TypeFlow AI", 18f))
        root.addView(Button(this).apply { text="Choose TypeFlow keyboard"; setOnClickListener { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() } })
        root.addView(text("3. Backend URL", 18f))
        val url = EditText(this).apply { setText(prefs.getString("backend_url", "")); hint="https://your-worker.workers.dev"; setSingleLine(true); textSize=15f }
        root.addView(url)
        root.addView(Button(this).apply { text="Save backend URL"; setOnClickListener { val value=url.text.toString().trim().trimEnd('/'); prefs.edit().putString("backend_url", value).apply(); Toast.makeText(this@MainActivity,"Saved",Toast.LENGTH_SHORT).show() } })
        root.addView(text("How to use", 20f))
        root.addView(text("Open any app, switch to TypeFlow AI, type or select text, then tap Rewrite / Professional / Casual / Polite / Shorten / Expand. AI features need your Cloudflare Worker URL and Gemini key configured on the worker.", 15f, 0xFFE2DEEA.toInt()))
        root.addView(text("Privacy: text is sent to your configured backend only when you tap an AI action. Avoid sending passwords or sensitive information.", 13f, 0xFFFFCC80.toInt()))
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
