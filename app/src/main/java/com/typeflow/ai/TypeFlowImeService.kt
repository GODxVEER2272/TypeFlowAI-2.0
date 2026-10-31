package com.typeflow.ai

import android.inputmethodservice.InputMethodService
import android.view.*
import android.view.inputmethod.InputConnection
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class TypeFlowImeService : InputMethodService() {
    private val io = Executors.newSingleThreadExecutor()
    private var root: LinearLayout? = null
    private var status: TextView? = null
    private val bg = Color.rgb(25, 24, 34)
    private val keyBg = Color.rgb(55, 52, 68)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreateInputView(): View {
        val container = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(5),dp(7),dp(5),dp(8)); setBackgroundColor(bg) }
        root = container
        val tools = listOf("Rewrite", "Professional", "Casual", "Polite", "Shorten", "Expand")
        val first = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        tools.forEach { label -> first.addView(tool(label) { runAi(label.lowercase()) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2),dp(2),dp(2),dp(4)) }) }
        container.addView(first)
        status = TextView(this).apply { text="TypeFlow AI  •  Select text or place cursor"; textSize=11f; setTextColor(0xFFBDB8CC.toInt()); gravity=Gravity.CENTER; setPadding(0,0,0,dp(4)) }
        container.addView(status)
        listOf("qwertyuiop", "asdfghjkl", "⇧zxcvbnm⌫").forEachIndexed { index, row ->
            val line=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
            row.forEach { ch ->
                val label=ch.toString(); val weight=if(label=="⇧"||label=="⌫") 1.45f else 1f
                line.addView(key(label) { handleKey(label) }, LinearLayout.LayoutParams(0,dp(51),weight).apply { setMargins(dp(2),dp(3),dp(2),dp(3)) })
            }
            container.addView(line)
        }
        val bottom=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        listOf("?123"," , ","English",".","↵").forEachIndexed { i,label ->
            val shown=when(label){" , "->",";"; else->label}
            bottom.addView(key(shown) { when(i){0->insert("?123");1->insert(",");2->insert(" ");3->insert(".");4->sendEnter()} }, LinearLayout.LayoutParams(0,dp(48), when(i){2->4f;else->1f}).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) })
        }
        container.addView(bottom)
        return container
    }
    private fun tool(label:String, action:()->Unit)=Button(this).apply { text=label; textSize=9.5f; isAllCaps=false; setTextColor(Color.WHITE); setBackgroundColor(0xFF51456D.toInt()); setPadding(dp(2),0,dp(2),0); setOnClickListener { action() } }
    private fun key(label:String, action:()->Unit)=TextView(this).apply { text=label; textSize=if(label.length>2) 12f else 19f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setBackgroundColor(keyBg); setOnClickListener { action() } }
    private fun current(): InputConnection? = currentInputConnection
    private fun insert(s:String) { current()?.commitText(s,1) }
    private fun sendEnter(){ val ic=current() ?: return; val result=ic.performEditorAction(currentInputEditorInfo?.imeOptions?.and(android.view.inputmethod.EditorInfo.IME_MASK_ACTION) ?: 0); if(!result) ic.commitText("\n",1) }
    private fun handleKey(k:String){ val ic=current() ?: return; when(k){"⌫"-> { if(!ic.deleteSurroundingText(1,0)) ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL)) }; "⇧"->{}; else->insert(k) } }
    private fun runAi(task:String) {
        val ic=current() ?: return
        val selected=ic.getSelectedText(0)?.toString().orEmpty()
        val before=ic.getTextBeforeCursor(1200,0)?.toString().orEmpty()
        val text=if(selected.isNotBlank()) selected else before.takeLast(700)
        if(text.isBlank()){ status?.text="Select text or type a sentence first"; return }
        val prefs=getSharedPreferences("typeflow", MODE_PRIVATE); val endpoint=prefs.getString("backend_url", "").orEmpty()
        if(!endpoint.startsWith("https://")){ status?.text="Set your HTTPS backend URL in TypeFlow app"; return }
        status?.text="Gemini is working…"
        io.execute {
            try {
                val conn=(URL("$endpoint/rewrite").openConnection() as HttpURLConnection).apply { requestMethod="POST"; connectTimeout=12000; readTimeout=30000; doOutput=true; setRequestProperty("Content-Type","application/json") }
                val payload=JSONObject().put("text",text).put("task",task).toString()
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val stream=if(conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val body=stream.bufferedReader().use { it.readText() }; conn.disconnect()
                val result=JSONObject(body).optString("text")
                if(result.isBlank()) throw Exception(JSONObject(body).optString("error","Empty response"))
                mainExecutor.execute {
                    if(selected.isNotBlank()) ic.commitText(result,1)
                    else { ic.deleteSurroundingText(text.length,0); ic.commitText(result,1) }
                    status?.text="Done ✓"
                }
            } catch(e:Exception){ mainExecutor.execute { status?.text="AI error: ${e.message?.take(90) ?: "Check backend"}" } }
        }
    }
    override fun onDestroy(){ io.shutdown(); super.onDestroy() }
}
