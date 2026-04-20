# 13 — MCP, Tools & Internet Access for GemOfGemma

**Date:** April 18, 2026  
**Author:** Elaine (ML Engineer)  
**Status:** Strategic Research — determines next major feature  

---

## Table of Contents

1. [Gemma 4 Function Calling / Tool Use](#1-gemma-4-function-calling--tool-use)
2. [MCP (Model Context Protocol) on Android](#2-mcp-model-context-protocol-on-android)
3. [Hybrid Architecture: On-Device AI + Cloud Tools](#3-hybrid-architecture-on-device-ai--cloud-tools)
4. [RAG (Retrieval Augmented Generation) on Android](#4-rag-retrieval-augmented-generation-on-android)
5. [Implementation Approach](#5-implementation-approach)
6. [Other Tool Ideas](#6-other-tool-ideas)
7. [Recommendations & Next Steps](#7-recommendations--next-steps)

---

## 1. Gemma 4 Function Calling / Tool Use

### 1.1 Does Gemma 4 Support Function Calling Natively?

**YES.** Gemma 4 supports structured function calling out of the box. This is confirmed by:

- Google's official documentation lists function calling as a core Gemma capability
- LiteRT-LM SDK (v0.10.2) includes `@Tool`, `@ToolParam`, and `ToolSet` annotations for native tool declaration
- The `ConversationConfig` constructor accepts a `tools` parameter
- `Content.ToolResponse` type exists for feeding tool results back to the model
- `Message.tool()` factory method creates tool-response messages
- Google AI Edge Gallery reference app demonstrates `MobileActions` and `TinyGarden` tool-calling patterns

### 1.2 How LiteRT-LM's Tool-Calling API Works

The LiteRT-LM SDK provides two approaches:

#### Approach A: Native ToolSet (SDK-level)

```kotlin
// Define tools using annotations
class WebSearchToolSet : ToolSet {
    @Tool("Search the web for current information")
    fun search_web(
        @ToolParam("The search query") query: String,
        @ToolParam("Max results to return") maxResults: Int = 5
    ): String {
        // Execute search, return results as string
        return performWebSearch(query, maxResults)
    }
}

// Register with conversation
val config = ConversationConfig(
    systemPrompt = "You are a helpful assistant...",
    tools = listOf(WebSearchToolSet())
)
val conversation = engine.createConversation(config)
```

When the model decides to call a tool:
1. `sendMessage()` returns a `Message` with tool-call content
2. The SDK automatically invokes the `@Tool`-annotated method
3. The result is automatically fed back as `Content.ToolResponse`
4. The model generates a final response incorporating the tool result

#### Approach B: Manual Function Calling (Our Current Approach)

Our `PhoneActionToolSet` currently uses prompt-based function calling:
1. Tool definitions are embedded as text in the system prompt via `toPromptString()`
2. Model outputs `<function_call>{"name": "...", "params": {...}}</function_call>` tags
3. `FunctionCallParser` extracts `ParsedAction` from model output
4. `ActionDispatcher` routes to the appropriate handler

**Both approaches work.** The manual approach is more flexible and debuggable. The native approach is cleaner but requires synchronous tool execution within the SDK's callback.

### 1.3 Can We Define a "Web Search" Tool?

**YES, absolutely.** We can define `search_web` as a tool using either approach:

```kotlin
// In PhoneActionToolSet.kt — add to existing tools list:
ToolDefinition(
    name = "search_web",
    description = "Search the internet for current information, news, facts, or answers to questions the assistant doesn't know",
    parameters = listOf(
        ToolParameter("query", "String", "The search query"),
        ToolParameter("maxResults", "Int", "Maximum number of results (1-10)", required = false)
    )
)
```

The model would call this when it encounters questions about:
- Current events, news, weather
- Facts it's unsure about or that post-date its training
- Prices, availability, schedules
- Anything requiring real-time data

---

## 2. MCP (Model Context Protocol) on Android

### 2.1 What is MCP?

MCP (Model Context Protocol) is an **open protocol** (hosted by The Linux Foundation) that provides a standardized way to connect LLM applications to external data sources, tools, and workflows. Think of it as "USB-C for AI" — a universal interface.

Key primitives:
- **Tools:** Executable functions the model can invoke (search, calculate, API calls)
- **Resources:** Data the model can read (files, databases, API responses)
- **Prompts:** Reusable prompt templates
- **Sampling:** Server-initiated LLM requests (reverse direction)

### 2.2 Can MCP Run on Android?

**YES, via the official Kotlin SDK.**

The MCP Kotlin SDK (`io.modelcontextprotocol:kotlin-sdk:0.11.1`) is:
- **Kotlin Multiplatform** — targets JVM, Native, JS, and Wasm
- **Coroutine-friendly** — suspending APIs, Flow support
- **Maintained by JetBrains** in collaboration with the MCP project
- **Version 0.11.1** released ~1 week ago (as of April 2026)

#### Gradle Setup

```kotlin
dependencies {
    // Full SDK (client + server)
    implementation("io.modelcontextprotocol:kotlin-sdk:0.11.1")
    // Or client-only
    implementation("io.modelcontextprotocol:kotlin-sdk-client:0.11.1")
    // Or server-only
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.11.1")
    
    // Required Ktor engine
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")  // Android-compatible
}
```

### 2.3 Could We Run a Local MCP Server on the Phone?

**YES.** The Kotlin SDK includes server capabilities:

```kotlin
// Run a local MCP server on the device
val mcpServer = Server(
    serverInfo = Implementation("gemofgemma-tools", "1.0.0"),
    options = ServerOptions(
        capabilities = ServerCapabilities(
            tools = ServerCapabilities.Tools(listChanged = true)
        )
    )
)

mcpServer.addTool(
    name = "search_web",
    description = "Search the internet",
    inputSchema = ToolSchema(
        properties = buildJsonObject {
            put("query", buildJsonObject { put("type", "string") })
        }
    )
) { request ->
    val query = request.arguments?.get("query")?.jsonPrimitive?.content ?: ""
    val results = BraveSearchClient.search(query)
    CallToolResult(content = listOf(TextContent(results)))
}

// Start with embedded Ktor server (CIO engine works on Android)
embeddedServer(CIO, host = "127.0.0.1", port = 3000) {
    mcpStreamableHttp { mcpServer }
}.start(wait = false)
```

**Available transports for Android:**
| Transport | Android Viable? | Use Case |
|-----------|----------------|----------|
| STDIO | ❌ No stdin/stdout | N/A |
| Streamable HTTP | ✅ Yes (Ktor CIO) | Local server, remote access |
| SSE | ✅ Yes | Legacy compatibility |
| WebSocket | ✅ Yes | Low-latency bidirectional |
| ChannelTransport | ✅ Yes (in-process) | **Best for on-device** |

**Best option: ChannelTransport** — uses Kotlin coroutines channels for in-process communication. No networking overhead, no port binding. Perfect for running MCP server and client in the same Android process.

### 2.4 Is MCP Worth It for GemOfGemma?

**Analysis:**

| Factor | MCP Approach | Direct Tool Approach |
|--------|-------------|---------------------|
| Complexity | Higher — requires MCP SDK, Ktor, transport setup | Lower — direct function calls |
| Flexibility | Can connect to remote MCP servers too | Local tools only |
| Ecosystem | Access to growing MCP server ecosystem | Custom tools only |
| Overhead | ~2-5MB SDK + deps, small memory/CPU overhead | Near-zero overhead |
| Debugging | MCP Inspector tool available | Standard debugger |
| Future-proof | Industry standard, growing adoption | Custom, may need migration |

**Verdict: NOT recommended for Phase 1.** MCP adds complexity without clear benefit when all tools run on-device. However, it becomes valuable if/when we want to connect to remote tool servers. **Revisit for Phase 3+.**

For now, extend `PhoneActionToolSet` directly — it's simpler, lower overhead, and already integrated.

---

## 3. Hybrid Architecture: On-Device AI + Cloud Tools

### 3.1 The Pattern

```
User asks: "What's the latest news about SpaceX?"
    ↓
Gemma 4 (on-device) recognizes it needs current info
    ↓
Gemma outputs: <function_call>{"name":"search_web","params":{"query":"SpaceX latest news"}}</function_call>
    ↓
FunctionCallParser extracts the call
    ↓
WebSearchHandler makes HTTP request to search API
    ↓
Results returned to Gemma's conversation context
    ↓
Gemma synthesizes a natural language answer from the search results
    ↓
User sees: "SpaceX successfully launched Starship Flight 15 today..."
```

### 3.2 How Existing Apps Handle This

| App | Approach | On-Device? | Search Provider |
|-----|----------|-----------|-----------------|
| **Google Gemini** | Cloud model + Google Search integration | No | Google Search (internal) |
| **Perplexity** | Cloud model + multi-source search | No | Multiple (Bing, Google, etc.) |
| **ChatGPT** | Cloud model + Bing search tool | No | Bing API |
| **Apple Intelligence** | On-device Siri + server-side search | Hybrid | Apple/Google |
| **Samsung Galaxy AI** | On-device Gemma Nano + cloud Gemini | Hybrid | Google Search |

**GemOfGemma's advantage:** We'd be one of the first fully on-device AI assistants with web search capability. The AI runs locally (privacy), but can optionally reach out to the internet when needed.

### 3.3 Available Search APIs

| API | Free Tier | Paid | Quality | Best For |
|-----|-----------|------|---------|----------|
| **Brave Search API** | $5/mo free credits (~1000 queries) | $5/1000 requests | Excellent | **Recommended** — independent index, privacy-focused, MCP-native |
| **SerpAPI** | 250 searches/mo | $25/mo (1000 searches) | Excellent (Google results) | Google-quality results |
| **DuckDuckGo Instant Answer** | Free (unofficial) | N/A | Good for quick facts | Simple answers, no auth needed |
| **Google Custom Search** | 100 queries/day free | $5/1000 queries | Best | Google-quality, but complex setup |
| **Bing Web Search** | 1000 calls/mo free | $3/1000 calls | Very good | Microsoft ecosystem |
| **Tavily** | 1000/mo free | $5/mo (5000) | Good | AI-optimized, RAG-ready |
| **Jina Reader** | Free (limited) | Paid tiers | Good | URL→text extraction |

**Recommendation: Brave Search API**
- $5/mo free credits (enough for personal/demo use)
- Independent index (not scraping Google)
- Privacy-focused (SOC 2, Zero Data Retention option)
- Extra snippets optimized for LLM context
- Used by Claude MCP, Perplexity, Mistral, Cohere
- Simple REST API, JSON response
- **LLM Context endpoint** returns pre-formatted text optimized for feeding to models

### 3.4 Brave Search API — Implementation Detail

```kotlin
// Simple Brave Search integration
class BraveSearchClient(
    private val httpClient: OkHttpClient,
    private val apiKey: String
) {
    suspend fun search(query: String, count: Int = 5): String {
        val url = "https://api.search.brave.com/res/v1/web/search"
        val request = Request.Builder()
            .url("$url?q=${URLEncoder.encode(query, "UTF-8")}&count=$count")
            .addHeader("X-Subscription-Token", apiKey)
            .addHeader("Accept", "application/json")
            .build()
        
        val response = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute()
        }
        
        val json = JSONObject(response.body?.string() ?: "")
        val results = json.optJSONObject("web")?.optJSONArray("results")
        
        return buildString {
            results?.let { arr ->
                for (i in 0 until minOf(arr.length(), count)) {
                    val r = arr.getJSONObject(i)
                    appendLine("${i+1}. ${r.getString("title")}")
                    appendLine("   ${r.getString("url")}")
                    appendLine("   ${r.optString("description", "")}")
                    appendLine()
                }
            }
        }
    }
}
```

### 3.5 Privacy Implications

| Data | Leaves Device? | Mitigation |
|------|---------------|------------|
| **User's question** | ❌ No — stays on-device | Gemma processes locally |
| **Search query** | ✅ Yes — sent to search API | Only the extracted query, not the full conversation |
| **Search results** | Downloaded to device | Processed on-device by Gemma |
| **AI-generated answer** | ❌ No — stays on-device | Gemma generates locally |
| **Conversation history** | ❌ No — stays on-device | Never sent to any server |

**Key privacy principle:** Only the search query leaves the device. The user's full conversation context, personal data, and AI responses remain entirely on-device. The user should see a clear indicator when a web search is happening.

**With Brave Search's Zero Data Retention:** Even the search query is not stored on Brave's servers.

---

## 4. RAG (Retrieval Augmented Generation) on Android

### 4.1 Can We Do RAG Locally?

**YES, with caveats.** On-device RAG requires three components:

1. **Embedding model** — converts text to vectors
2. **Vector store** — indexes and searches vectors
3. **Retrieval pipeline** — fetches relevant chunks and feeds to Gemma

### 4.2 On-Device Embedding Options

| Option | Size | Quality | Speed on Mobile |
|--------|------|---------|----------------|
| **Gemma 4 E2B embeddings** | 0 extra (reuse model) | Good | ~100ms/chunk |
| **TF-Lite all-MiniLM-L6-v2** | ~22 MB | Very good | ~5ms/chunk |
| **ONNX bge-small-en-v1.5** | ~33 MB | Excellent | ~10ms/chunk |
| **Google text-embedding-004** | Cloud only | Best | Network latency |

**Recommendation:** Use a small dedicated embedding model (all-MiniLM-L6-v2 via TFLite) for speed. 22MB is acceptable. Don't use Gemma for embeddings — it's too slow and memory-heavy for batch embedding.

### 4.3 On-Device Vector Databases

| Database | Android Support | Size | Features |
|----------|----------------|------|----------|
| **SQLite + sqlite-vec** | ✅ Native | ~500KB | SQLite extension, vector search via SQL, perfect for Android |
| **ObjectBox** | ✅ Native Android SDK | ~2MB | Object DB with HNSW vector index, Kotlin-first |
| **Chroma** | ❌ Python server | N/A | Not viable on Android |
| **FAISS (mobile)** | ⚠️ C++ via JNI | ~5MB | Facebook's library, requires JNI bridge |
| **Pinecone** | Cloud only | N/A | Not on-device |
| **Room + custom** | ✅ Native | ~0 extra | Manual cosine similarity in SQL, works for small datasets |

**Recommendation: sqlite-vec** — it's a SQLite extension that adds vector search natively. Since Android already uses SQLite, this is the lowest-friction option. Alternative: **ObjectBox** if we want a dedicated Kotlin-first solution.

### 4.4 RAG Architecture for GemOfGemma

```
User indexes files/notes (one-time):
    Documents → Chunking (512 tokens) → Embedding (MiniLM) → sqlite-vec storage

At query time:
    User question → Embed query → Top-K vector search → Retrieve chunks
        ↓
    Gemma receives: system prompt + retrieved chunks + user question
        ↓
    Gemma generates answer grounded in user's documents
```

**Use cases for on-device RAG:**
- Search personal notes/documents
- Query downloaded PDFs/manuals
- Search conversation history
- Index bookmarked web pages for offline access
- Personal knowledge base

### 4.5 RAG Priority Assessment

**Verdict: Phase 3+ feature.** On-device RAG is valuable but:
- Web search (Phase 2) covers the most common "I need external info" use case
- RAG requires significant UX work (document import, indexing progress, storage management)
- sqlite-vec may need NDK compilation for custom builds
- Start with web search, add RAG later for personal document search

---

## 5. Implementation Approach

### 5.1 Adding `search_web` to PhoneActionToolSet

Add to `PhoneActionToolSet.tools` list:

```kotlin
ToolDefinition(
    name = "search_web",
    description = "Search the internet for current information, recent events, facts, prices, weather, or anything requiring up-to-date data",
    parameters = listOf(
        ToolParameter("query", "String", "The search query to look up"),
        ToolParameter("maxResults", "Int", "Number of results (1-10, default 5)", required = false)
    )
)
```

### 5.2 New Handler: WebSearchActionHandler

```kotlin
@Singleton
class WebSearchActionHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {
    private val apiKey: String by lazy {
        // From BuildConfig, encrypted SharedPrefs, or user settings
        BuildConfig.BRAVE_SEARCH_API_KEY
    }
    
    suspend fun execute(query: String, maxResults: Int = 5): ActionResult {
        if (!isNetworkAvailable(context)) {
            return ActionResult.Error("No internet connection. I can only answer from my training data.")
        }
        
        return try {
            val results = BraveSearchClient(httpClient, apiKey).search(query, maxResults)
            ActionResult.Success(results)
        } catch (e: Exception) {
            ActionResult.Error("Search failed: ${e.message}")
        }
    }
}
```

### 5.3 Flow Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                        USER DEVICE                           │
│                                                              │
│  ┌─────────┐    ┌──────────────┐    ┌─────────────────────┐ │
│  │  User    │───▶│ ChatViewModel │───▶│   GemmaService      │ │
│  │  Input   │    │              │    │                     │ │
│  └─────────┘    └──────────────┘    │  ┌───────────────┐  │ │
│                                      │  │  Gemma 4 E2B  │  │ │
│                                      │  │  (on-device)  │  │ │
│                                      │  └───────┬───────┘  │ │
│                                      │          │           │ │
│                                      │   Model decides:    │ │
│                                      │   "I need to search"│ │
│                                      │          │           │ │
│                                      │  ┌───────▼───────┐  │ │
│                                      │  │FunctionCall   │  │ │
│                                      │  │Parser         │  │ │
│                                      │  └───────┬───────┘  │ │
│                                      │          │           │ │
│                                      │  ┌───────▼───────┐  │ │
│                                      │  │ActionDispatcher│  │ │
│                                      │  └───────┬───────┘  │ │
│                                      └──────────┼──────────┘ │
│                                                 │             │
│                                      ┌──────────▼──────────┐ │
│                                      │WebSearchHandler     │ │
│                                      │                     │ │
│                                      │ if(no internet) {   │ │
│                                      │   return fallback   │ │
│                                      │ }                   │ │
│                                      └──────────┬──────────┘ │
│                                                 │             │
└─────────────────────────────────────────────────┼─────────────┘
                                                  │
                              ONLY the search query crosses
                              the device boundary
                                                  │
                                      ┌───────────▼───────────┐
                                      │  Brave Search API     │
                                      │  (brave.com/search)   │
                                      │                       │
                                      │  Returns: titles,     │
                                      │  URLs, snippets       │
                                      └───────────┬───────────┘
                                                  │
┌─────────────────────────────────────────────────┼─────────────┐
│                        USER DEVICE              │             │
│                                                 ▼             │
│                                      ┌──────────────────────┐│
│                                      │ Results injected     ││
│                                      │ into Gemma's context ││
│                                      │                      ││
│                                      │ Gemma synthesizes    ││
│                                      │ natural language     ││
│                                      │ answer from results  ││
│                                      └──────────┬───────────┘│
│                                                 │             │
│  ┌──────────────┐    ┌──────────────┐          │             │
│  │  User sees   │◀───│ ChatViewModel │◀─────────┘             │
│  │  answer +    │    │              │                         │
│  │  sources     │    └──────────────┘                         │
│  └──────────────┘                                             │
└───────────────────────────────────────────────────────────────┘
```

### 5.4 Feeding Search Results Back to Conversation

When `search_web` is called, the results must be injected back into the conversation context:

```kotlin
// In GemmaService.processChat() — after ActionDispatcher returns search results:
if (parsedAction.functionName == "search_web") {
    val searchResults = actionResult.message  // formatted search results
    
    // Feed results back to the model as a tool response
    val toolResponse = """
    [Web Search Results for "${parsedAction.params["query"]}"]
    $searchResults
    [End of search results]
    
    Based on these search results, provide a helpful answer to the user's question. 
    Cite sources when possible.
    """.trimIndent()
    
    // Send tool response to the same conversation
    val finalResponse = conversation.sendMessage(toolResponse)
    return AiResponse.TextResponse(finalResponse.text)
}
```

### 5.5 Latency Analysis

| Step | Expected Latency | Notes |
|------|-----------------|-------|
| Gemma processes user question | 300ms - 2s | On-device, depends on query length |
| Gemma decides to call search_web | Included above | Part of generation |
| FunctionCallParser extracts call | <1ms | Regex/string parsing |
| HTTP request to Brave Search | 200-500ms | Network dependent |
| Parse search results | <5ms | JSON parsing |
| Feed results back to Gemma | 300ms - 1s | Context injection |
| Gemma generates final answer | 1-3s | Depends on answer length |
| **Total round-trip** | **~2-7 seconds** | Acceptable for search queries |

For comparison:
- Google Gemini app: ~2-5 seconds for search-augmented responses
- Perplexity: ~3-8 seconds for search + answer
- ChatGPT with Bing: ~3-10 seconds

**Our latency is competitive** despite running the AI model on-device.

### 5.6 Fallback: No Internet

```kotlin
// In WebSearchActionHandler
if (!isNetworkAvailable(context)) {
    return ActionResult.Error(
        "I don't have internet access right now. " +
        "I'll answer based on my training knowledge, but the information " +
        "may not be current. Would you like me to try anyway?"
    )
}
```

The model should gracefully degrade:
1. Attempt search → detect no connectivity
2. Inform user that answer is from training data only
3. Provide best-effort answer from model knowledge
4. Optionally queue the search for when connectivity returns

---

## 6. Other Tool Ideas

Beyond web search, here are tools we could add to `PhoneActionToolSet`:

### 6.1 High-Value Tools (Recommended)

| Tool | Description | Complexity | Privacy Impact |
|------|-------------|-----------|----------------|
| **calculate** | Math evaluation (use Android's built-in eval or exp4j library) | Low | None — on-device |
| **get_weather** | Current weather via OpenWeatherMap API (free tier: 1000 calls/day) | Medium | Location leaves device |
| **get_date_time** | Current date, time, timezone, day of week | Trivial | None |
| **search_contacts** | Search device contacts by name | Low | None — on-device |
| **query_calendar** | Read upcoming events from CalendarProvider | Low | None — on-device |
| **read_clipboard** | Read current clipboard contents | Trivial | None |
| **get_battery** | Battery level and charging status | Trivial | None |
| **get_location** | Current GPS location (if permitted) | Low | None — on-device |
| **wikipedia_summary** | Fetch Wikipedia article summary (free API, no key) | Low | Query leaves device |
| **unit_convert** | Convert units (temp, weight, distance, currency) | Low | None (except live currency rates) |

### 6.2 Medium-Value Tools (Phase 3+)

| Tool | Description | Complexity | Privacy Impact |
|------|-------------|-----------|----------------|
| **translate_text** | Use Android's MLKit on-device translation | Medium | None — on-device |
| **read_notifications** | Read recent notifications via NotificationListenerService | Medium | None — on-device |
| **app_state_query** | Query running apps, foreground app, recent apps | Low | None |
| **file_search** | Search files by name/type on device storage | Medium | None — on-device |
| **read_sms** | Read recent SMS messages (with permission) | Medium | None — on-device |
| **screen_content** | Read current screen via AccessibilityService | Already have | None — on-device |
| **create_note** | Create a note (ACTION_CREATE_NOTE, API 34+) | Low | None |
| **set_reminder** | Create a reminder via CalendarProvider | Low | None |

### 6.3 Advanced Tools (Phase 4+)

| Tool | Description | Complexity | Notes |
|------|-------------|-----------|-------|
| **run_shortcut** | Execute Android Shortcuts/Routines | High | Powerful automation |
| **smart_home** | Control Home Assistant / Google Home devices | High | Requires integration |
| **music_search** | "What song is this?" via audio recognition | High | Needs Shazam/ACRCloud API |
| **image_generate** | Generate images from text (Stable Diffusion on-device) | Very High | Requires separate model |
| **code_execute** | Run simple code snippets | High | Security concerns |

### 6.4 Tool Implementation Priority Matrix

```
Impact ↑
       │
  High │  search_web    get_weather    query_calendar
       │  calculate     search_contacts
       │
  Med  │  wikipedia     translate      read_notifications
       │  get_date_time unit_convert   file_search
       │
  Low  │  get_battery   read_clipboard  app_state_query
       │
       └──────────────────────────────────────────────▶
              Low          Medium          High
                        Effort →
```

**Phase 2 additions (do now):** search_web, calculate, get_date_time, get_battery, get_location  
**Phase 3 additions:** get_weather, wikipedia_summary, query_calendar, search_contacts, unit_convert  
**Phase 4 additions:** translate_text, read_notifications, file_search, read_sms

---

## 7. Recommendations & Next Steps

### 7.1 Recommended Architecture (Phase 2)

```
┌─────────────────────────────────────┐
│         PhoneActionToolSet          │
│                                     │
│  Existing tools:                    │
│  ├── sendSms, makeCall, setAlarm    │
│  ├── setVolume, toggleFlashlight    │
│  ├── toggleDnd, openApp, navigate   │
│  ├── mediaControl, setBrightness    │
│  └── createCalendarEvent            │
│                                     │
│  NEW tools (Phase 2):               │
│  ├── search_web (Brave Search API)  │
│  ├── calculate (exp4j or built-in)  │
│  ├── get_date_time (System.now)     │
│  ├── get_battery (BatteryManager)   │
│  └── get_location (FusedLocation)   │
│                                     │
│  Future tools (Phase 3+):           │
│  ├── get_weather (OpenWeatherMap)   │
│  ├── wikipedia (REST API)           │
│  ├── query_calendar (ContentProv)   │
│  ├── search_contacts (ContentProv)  │
│  └── ... more as needed             │
└─────────────────────────────────────┘
```

### 7.2 Key Technical Decisions

1. **Use Brave Search API** — best balance of quality, privacy, cost, and LLM-optimization
2. **Keep manual function calling** — don't migrate to native ToolSet yet. Prompt-based is more flexible
3. **Skip MCP for now** — too much complexity for on-device use. Revisit when we need remote tool servers
4. **Skip RAG for now** — web search covers the primary "external knowledge" use case
5. **API key management** — store Brave API key in encrypted SharedPreferences, allow user to provide their own key
6. **Show search indicator** — display a "Searching the web..." UI state so user knows data is leaving the device
7. **Offline graceful degradation** — always attempt local answer when network is unavailable

### 7.3 Privacy Principles

1. **The AI model NEVER leaves the device** — Gemma 4 runs 100% on-device
2. **Conversation history NEVER leaves the device** — only extracted search queries go to the API
3. **User controls when searches happen** — can disable web search in settings
4. **Transparent data flow** — show clear UI when a web request is being made
5. **Minimal data exposure** — send only the search query, not the full conversation context
6. **User-provided API keys** — allow users to bring their own Brave/Google/etc. API key

### 7.4 Implementation Checklist

- [ ] Add `search_web`, `calculate`, `get_date_time`, `get_battery`, `get_location` to `PhoneActionToolSet`
- [ ] Create `WebSearchActionHandler` with Brave Search API integration
- [ ] Create `CalculatorActionHandler` (simple math eval)
- [ ] Create `DateTimeActionHandler` (trivial)
- [ ] Create `BatteryActionHandler` (trivial)
- [ ] Create `LocationActionHandler` (FusedLocationProvider)
- [ ] Update `ActionDispatcher` to route new tool names
- [ ] Update `SafetyValidator` — search_web = Approved (only query leaves device), get_location = NeedsConfirmation
- [ ] Update `GemmaService.processVoiceCommand()` to handle search tool responses (re-inject into conversation)
- [ ] Add "Searching..." UI state in ChatViewModel
- [ ] Add web search toggle in Settings
- [ ] Add API key input in Settings (or use BuildConfig default)
- [ ] Handle no-internet fallback gracefully
- [ ] Test: "What's the weather in Tokyo?" → model calls search_web → results → answer
- [ ] Test: "What's 15% of $847?" → model calls calculate → answer
- [ ] Test: "What day is it?" → model calls get_date_time → answer

---

## Appendix A: Brave Search API Quick Reference

**Endpoint:** `GET https://api.search.brave.com/res/v1/web/search`

**Headers:**
- `X-Subscription-Token: <API_KEY>`
- `Accept: application/json`

**Parameters:**
- `q` — search query (required)
- `count` — results per page (1-20, default 10)
- `country` — country code (e.g., "us")
- `search_lang` — language (e.g., "en")

**LLM Context Endpoint:** `GET https://api.search.brave.com/res/v1/web/search` with `result_filter=query` returns pre-formatted text optimized for LLM consumption.

**Pricing:** $5/mo free credits, then $5/1000 requests.

---

## Appendix B: MCP Kotlin SDK Quick Reference

**Maven:** `io.modelcontextprotocol:kotlin-sdk:0.11.1`  
**Source:** https://github.com/modelcontextprotocol/kotlin-sdk  
**License:** Apache 2.0  
**Platforms:** JVM, Native, JS, Wasm (Kotlin Multiplatform)  
**Maintained by:** JetBrains + MCP community  
**Key dependency:** Ktor (bring your own engine)  
**Android-compatible engine:** `io.ktor:ktor-client-okhttp` or `io.ktor:ktor-client-cio`

**When to use MCP in GemOfGemma:**
- Phase 3+: If we want to connect to remote tool servers
- Phase 4+: If we want GemOfGemma to be an MCP server itself (expose phone capabilities to desktop AI)
- Never: If all tools remain local, direct integration is simpler

---

## Appendix C: Search API Comparison Matrix

| Feature | Brave | SerpAPI | Google CSE | Bing | DuckDuckGo | Tavily |
|---------|-------|---------|------------|------|------------|--------|
| Free tier | $5/mo credits | 250/mo | 100/day | 1000/mo | Unlimited* | 1000/mo |
| Paid cost | $5/1K | $25+/mo | $5/1K | $3/1K | N/A | $5/mo |
| Own index | ✅ | ❌ (Google) | ❌ (Google) | ✅ | ✅ | ❌ |
| LLM-optimized | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Privacy | SOC2 + ZDR | Standard | Google tracking | Microsoft | Best | Standard |
| API quality | Excellent | Excellent | Excellent | Very good | Basic | Good |
| Auth required | API key | API key | API key + CX | API key | None* | API key |
| Rate limit | 50 QPS | Varies | 10 QPS | 3 QPS | Unknown | 10 QPS |

*DuckDuckGo Instant Answer API is unofficial/limited — returns only instant answers, not full web results.
