# TypeFlow AI 2.0

An Android custom keyboard with Gemini-powered rewriting. The Android app never contains the Gemini API key: it calls your Cloudflare Worker, which calls the Gemini API.

## Features

- Android Input Method Service (IME) with QWERTY keys, backspace, enter, space, comma and period.
- AI actions: Rewrite, Professional, Casual, Polite, Shorten and Expand.
- Uses selected text when available; otherwise rewrites the text immediately before the cursor (up to 700 characters).
- Main app screen to enable the keyboard and save the HTTPS backend URL.
- GitHub Actions workflow to build a debug APK.

## 1. Deploy the Gemini backend

1. Install Node.js and Wrangler, or use the Cloudflare dashboard's Worker editor.
2. Create a Worker named `typeflow-ai-api`.
3. Paste the contents of `worker/index.js` into the Worker editor and deploy it.
4. In Worker Settings → Variables and Secrets, add a **secret** named `GEMINI_API_KEY` and paste your Gemini API key there. Never put the key in this repository, Android source, or an APK.
5. The Worker uses the stable Gemini API model `gemini-3.8-flash`. Change `MODEL` in `worker/index.js` only if you intentionally want another supported model.
6. Visit `https://YOUR-WORKER.workers.dev/health`; it should return `ok: true`.

CLI alternative from the `worker` directory:

```bash
npm install -g wrangler
wrangler login
wrangler secret put GEMINI_API_KEY
# paste the key when prompted
wrangler deploy
```

Do not commit `.dev.vars`, API keys, or secret values. For public release, add Cloudflare rate limiting and abuse controls to the Worker; this starter endpoint has input limits but is not a complete public-service abuse-prevention system.

## 2. Build the Android app

Open this folder in Android Studio (JDK 17, Android SDK 35), wait for Gradle sync, then Build → Build Bundle(s) / APK(s) → Build APK(s).

Or push this project to a GitHub repository. The included workflow runs on pushes to `main`/`master` and manual dispatch. Download `TypeFlowAI-debug-apk` from the completed Actions run.

## 3. Connect the app

1. Install `app-debug.apk` on Android.
2. Open TypeFlow AI → Open keyboard settings → enable TypeFlow AI.
3. Tap Choose TypeFlow keyboard and select it.
4. In the app, save your Worker URL, e.g. `https://typeflow-ai-api.YOUR-SUBDOMAIN.workers.dev`.
5. Open any text field, select a sentence (recommended), and tap an AI action.

## Privacy and limitations

- Text is sent to your Worker and Google Gemini only after an AI toolbar action is tapped. Do not submit passwords, payment details, or other sensitive text.
- Android's OS may display its standard warning when enabling a third-party keyboard. A keyboard can potentially observe what is typed; use only a keyboard you trust.
- This is a functional starter, not a Play Store-ready product. It currently provides an English QWERTY layout; Shift/Caps, number/symbol page, multilingual layouts, clipboard history, voice typing, suggestions, per-user authentication and robust abuse controls are not implemented yet.
- The debug APK is unsigned for Play Store release. A release build needs a signing key stored securely in CI secrets.
