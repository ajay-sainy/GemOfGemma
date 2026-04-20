# Context Management for On-Device Chat with Gemma 4

**Researcher:** Elaine (ML Engineer)  
**Date:** 2026-04-18  
**Status:** VERIFIED — Implementation-ready

---

## 1. Gemma 4 E2B Context Window

- **Max context length:** 32,768 tokens (32K)
- **Benchmarked default:** 2,048 tokens (used in official benchmarks)
- **LiteRT-LM `EngineConfig.maxNumTokens`:** Controls context window at engine creation time
- **Source:** [HuggingFace model card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) — "The model can support up to 32k context length"
- **On-device constraint:** Larger context = more memory. At 32K, expect higher RAM usage than the benchmarked 676MB (GPU).

**Recommendation:** Use 8,192 tokens as a practical default for mobile. This gives ~12-15 multi-turn exchanges while keeping memory reasonable.

---

## 2. LiteRT-LM Conversation API — Does It Maintain History?

### Answer: YES — Confirmed

The `Conversation` object in LiteRT-LM **maintains full conversation history internally** across `sendMessage()` calls. This is the primary design pattern:

```kotlin
// From official Android guide (https://ai.google.dev/edge/litert-lm/android):
engine.createConversation().use { conversation ->
    while (true) {
        print("\n>>> ")
        conversation.sendMessageAsync(readln()).collect { print(it) }
    }
}
```

Each `sendMessage()` call:
1. Appends the user message to internal history
2. Generates a response using the full accumulated context
3. Appends the model response to internal history
4. Returns the response

### Evidence from Google AI Edge Gallery App

The Gallery app (official reference implementation) uses this exact pattern:
- `LlmModelInstance(engine, conversation)` — keeps a persistent conversation object
- `conversation.sendMessageAsync(Contents.of(contents))` — sends each new message to the same conversation
- `resetConversation()` — closes old conversation, creates new one (used for session reset or after N turns in TinyGarden)
- **Never rebuilds history manually** — the conversation object handles it

### `ConversationConfig.initialMessages`

Pre-seeds the conversation with synthetic history:
```kotlin
val config = ConversationConfig(
    initialMessages = listOf(
        Message.user("What is the capital of the United States?"),
        Message.model("Washington, D.C."),
    ),
)
```
This is useful for:
- Resuming a conversation after process death
- Providing few-shot examples

---

## 3. The Bug in GemOfGemma

**Root cause:** `GemmaService.processChat()` creates a **new** `Conversation` for every single message:

```kotlin
private fun processChat(request: AiRequest.TextChat): AiResponse {
    val config = ConversationConfig(...)
    val conversation = engine.createConversation(config)  // NEW every time!
    val result = conversation.sendMessage(request.message)
    return AiResponse.TextResponse(result.toString())
}
```

And `GemmaEngine.createConversation()` explicitly **closes** the previous conversation:
```kotlin
fun createConversation(config: ConversationConfig): Conversation {
    activeConversation?.close()  // Destroys all history!
    return eng.createConversation(config).also { activeConversation = it }
}
```

**Fix:** Keep a persistent `Conversation` per conversationId. Only create a new one for new sessions or resets.

---

## 4. Context Management Best Practices

### How Chat Apps Handle Context Windows

| Strategy | Used By | Description |
|----------|---------|-------------|
| **Persistent conversation object** | Gallery, Claude | Keep the same conversation, let runtime manage history |
| **Sliding window** | ChatGPT, Claude | Keep last N messages, drop oldest |
| **Summarization** | ChatGPT (gpt-4) | Summarize old messages into a compact summary |
| **Token counting** | All | Count tokens to stay within context budget |
| **System prompt reservation** | All | Always reserve space for system prompt + expected response |

### Token Estimation

For Gemma 4 (SentencePiece tokenizer):
- **English text:** ~1 token per 4 characters (approximate)
- **Code:** ~1 token per 3 characters
- **Non-Latin scripts:** ~1 token per 2-3 characters
- **System prompt for GemOfGemma chat:** ~200-500 tokens

### Our Context Budget (8,192 tokens)

| Component | Tokens Reserved |
|-----------|----------------|
| System prompt | 500 |
| Expected response | 1,024 |
| Safety buffer | 200 |
| **Available for history** | **~6,468** |

At ~50 tokens per short message exchange (user + assistant), that's **~130 exchanges** before any management is needed.

### Chosen Strategy: Persistent Conversation + Automatic Reset

1. **Primary:** Use LiteRT-LM's built-in conversation persistence (like the Gallery app)
2. **Safety net:** Track estimated token usage; when approaching the limit, close the old conversation and create a new one with `initialMessages` containing recent history
3. **User experience:** Show a system message when context is reset

---

## 5. Implementation Plan

### Option A: Persistent Conversation (CHOSEN)

1. `GemmaEngine` manages conversations by `conversationId`
2. `processChat()` reuses the existing conversation — just calls `sendMessage()`
3. Token counting tracks estimated usage
4. When approaching context limit: reset conversation with recent history via `initialMessages`
5. `ChatViewModel` passes `conversationId` (already does) — no need for full history in request

### Why Not Option B (Manual History Rebuild)?

- Unnecessary complexity — the `Conversation` object already does this
- `initialMessages` would re-process all history tokens on every request (slow)
- Matches the Gallery app's approach exactly

---

## 6. References

- [LiteRT-LM Android Guide](https://ai.google.dev/edge/litert-lm/android) — Official conversation API docs
- [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) — `LlmChatModelHelper.kt` shows persistent conversation pattern
- [Gemma 4 E2B Model Card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) — 32K context, performance data
