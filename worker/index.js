
async function fetchGeminiWithRetry(url, options) {
  const retryable = [408, 429, 500, 502, 503, 504];

  for (let attempt = 0; attempt < 3; attempt++) {
    const response = await fetchGeminiWithRetry(GEMINI_URL, {

    if (!retryable.includes(response.status) || attempt === 2) {
      return response;
    }

    await response.body?.cancel().catch(() => {});

    const delay = 1000 * (2 ** attempt) +
      Math.floor(Math.random() * 300);

    await new Promise(resolve => setTimeout(resolve, delay));
  }

  throw new Error("Gemini service unavailable");
}
