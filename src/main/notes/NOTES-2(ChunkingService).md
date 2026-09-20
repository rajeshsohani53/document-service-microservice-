# RAG Microservices — Learning Notes

> Rebuilding my final-year Document Q&A RAG project in **Java + Spring Boot**, by hand,
> as a **microservices** system. Goal: one strong project for the interview that proves
> I understand RAG, LLM usage, microservices, and Spring Boot REST APIs — code I can defend
> line by line, not generated code I can't explain.

---

## 1. The Big Picture

- **Old project:** thesis Doc-QA-RAG, architecture designed by me but code was AI-generated (Python).
- **Now:** rebuild it in Java/Spring Boot myself, split into microservices, Docker + Kubernetes on top.
- **Interview pitch:** *"I designed this system for my thesis, then went back and rebuilt it myself
  in Spring Boot to own every layer."*

---

## 2. Concept 1 — What is a Microservice? (in Java terms)

**Definition I can say out loud:**
> A microservice is a complete, standalone Spring Boot application that does **one job** and
> runs as its **own separate process**.

Each service has its **own**:
- `pom.xml` (separate Maven project)
- `main()` method with `@SpringBootApplication`
- port (8080, 8081, 8082...)
- JVM process (started separately)
- JAR (later, its own Docker container)

**Monolith vs Microservices — the key line:**
- Monolith: controller → service call = a **method call inside one JVM**. Instant, can't fail on network.
- Microservices: orchestrator → embedding service = a **network (HTTP) call to another process**.
  Can be slow, can fail.
- *That single difference is the source of every microservices tradeoff* (latency, failure handling, complexity).

**Interview follow-ups (have these ready):**
- *"Isn't this just running multiple apps?"* → Yes, exactly. "Micro" = narrow scope, not tiny code.
- *"Different from a monolith with separate packages?"* → Packages = same process, method calls.
  Services = separate processes, network calls.
- *"Do they share code?"* → As little as possible. Maybe small shared DTOs. Goal = loose coupling.
- *"Why not just a monolith?"* → Independent scaling, independent deployment, fault isolation.
  Cost = complexity. Monolith is genuinely better for small apps.

---

## 3. The Architecture — 5 Services (Single Responsibility)

Each service = **one job**. This *is* the single-responsibility principle, made physical.

| Port | Service | One Job |
|------|---------|---------|
| 8080 | API Gateway / Orchestrator | Entry point; coordinates the whole flow (built last) |
| 8081 | **Document Service** | Raw document → clean chunks |
| 8082 | Embedding Service | Text → vector (only service that talks to the embedding model) |
| 8083 | Retrieval Service | Question vector → most relevant chunks (cosine + keyword graph) |
| 8084 | LLM / Answer Service | Question + chunks → build prompt → call Groq → answer |

**How they talk:** REST over HTTP (synchronous). Each exposes `@RestController` endpoints;
the orchestrator calls them with `RestTemplate` / `WebClient`.
→ This is my live answer to *"how do services communicate?"*

**Build order (dependency order):** Document → Embedding → Retrieval → LLM → Gateway.
Each one runs and is testable before the next.

---

## 4. Learning Path (concepts to cover while building)

1. What a microservice is ✅ (done)
2. How services communicate — REST + RestTemplate/WebClient
3. Service independence — separate data, separate deployment, no shared state
4. Gateway / orchestration pattern
5. Resilience — timeouts, retries, service-down handling
6. Containerization & orchestration — Docker per service, Kubernetes to run them together

(1–4 = core interview meat. 5–6 = senior-sounding bonus.)

---

## 5. Service #1 — Document Service (port 8081)

### Setup
- Generated with **Spring Initializr** (start.spring.io).
- Maven · Java 17 · Spring Boot **3.3.5** · Packaging **Jar**.
- Group `com.raj` · Artifact `document-service` · Package `com.raj.documentservice`.
- Dependency from Initializr: **Spring Web** (brings Spring MVC + embedded Tomcat → lets it expose REST endpoints).
- `application.properties`: `server.port=8081`

### LangChain4j (added manually to pom.xml)
- **Not in the Initializr picker** — it's a third-party library, so it goes in the pom by hand.
- Latest **stable = 1.19.0**, group `dev.langchain4j`.
- Managed with a **BOM** (Bill of Materials): one place dictates versions for the whole library family,
  so modules never mismatch. → Interview line: *"I used the LangChain4j BOM to keep module versions aligned."*

```xml
<!-- dependencyManagement (sibling of <dependencies>) -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>1.19.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- inside <dependencies> — NOTE: no <version>, the BOM supplies it -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
</dependency>
```

### Single-responsibility decision
- Old `ChunkingService` did **two** jobs: split the document **and** call Groq for per-chunk context.
- Splitting = document concern. Calling an LLM = LLM concern.
- **Fix:** Document Service only **splits**. No Groq, no LLM here. Context generation moves to the LLM service later.
- Result: this service's only extra dependency is LangChain4j core (for the splitter).

---

## 6. The Upload Endpoint

`POST /api/documents/upload` — the door into the system. Step 1: prove the file arrives (no chunking yet).

**Layering (same as gym/blog projects):**
- `controller/DocumentController` — handles the HTTP request ✅
- `service/ChunkingService` — does the splitting (next step)

```java
package com.raj.documentservice.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @PostMapping("/upload")
    public Map<String, Object> uploadDocument(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);

        return Map.of(
            "filename", filename,
            "characters", content.length(),
            "message", "File received successfully"
        );
    }
}
```

**Key points:**
- `@RequestMapping("/api/documents")` + `@PostMapping("/upload")` → full URL `POST /api/documents/upload`.
- **`MultipartFile`** = Spring's type for an uploaded file. Files arrive as `multipart/form-data`
  (same format as an HTML file-upload form), **not JSON**.
- **`@RequestParam("file")`, NOT `@RequestBody`** ← the one an interviewer will test.
  - `@RequestBody` = JSON body.
  - File upload = named form field → `@RequestParam` with `MultipartFile`.
  - The `"file"` string must match the form-field name the client sends.
- `file.getBytes()` → raw bytes; `new String(..., UTF_8)` → text.
  Explicit UTF-8 so it behaves the same on every machine (platform default = real bug source with non-ASCII).
- Returning a `Map` → Spring + Jackson auto-convert to JSON (getter-based serialization, same as before).
- `throws IOException` → `getBytes()` can fail if the stream breaks.

**Scope note:** reads file as **plain text** → works for `.txt` now.
Real PDF/Word parsing needs a document loader (LangChain4j loaders or Apache PDFBox) — add later.

---

## 7. Frontend Test Page + CORS

- Built a simple `upload.html` (plain HTML + vanilla JS `fetch`) to test uploads without curl.
- JS builds a `FormData`, appends the file under key `"file"` (must match `@RequestParam("file")`),
  then POSTs it. Browser auto-sets `multipart/form-data` — **don't set Content-Type manually**
  (it breaks the multipart boundary marker).

**CORS (interview gold):**
- Opening `upload.html` from disk (`file://`) → page origin is `null` → calling `localhost:8081`
  is a **cross-origin** request.
- Browser blocks JS from reading the response by default → `Failed to fetch`.
  This is the browser's security model, **not a bug in the code**.
- Dev fix: `@CrossOrigin(origins = "*")` on the controller → Spring sends the
  `Access-Control-Allow-Origin` header so the browser allows it.
- `*` = allow any origin (covers the `null` file:// origin too). Real deploy: lock to a specific frontend URL.
- Better pattern: handle CORS centrally at the **API Gateway**, not in every service.
  → Interview line: *"CORS is a browser security rule; I handled it at the gateway, not per-service."*

```java
@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")   // dev only
public class DocumentController { ... }
```

**Upload confirmed working** — uploaded a `.txt`, got back JSON with filename + character count.

---

## 8. ChunkingService — the real job of this service

**Why chunk at all? (3 reasons)**
1. **LLM context limits** — can't fit a whole document in a prompt. Retrieve only the relevant chunks, send those.
2. **Better embeddings** — one vector per piece of text. A whole-document vector = a blurry average of everything.
   A focused chunk vector = captures a specific idea. Smaller = sharper.
3. **Precise retrieval** — chunks are the *unit of retrieval*; return the paragraph that answers the question,
   not the entire file.

**The splitter: `DocumentSplitters.recursive(500, 50)`**
- **500** = max chunk size in **characters**.
- **50** = **overlap** in characters between consecutive chunks.
- **"recursive"** = splits on natural boundaries in order of preference: paragraphs → lines → sentences → words.
  Won't cut mid-word or mid-sentence unless forced. Clean, readable chunks.
- **Why overlap?** If an idea spans the boundary between chunk 1 and chunk 2, without overlap it gets cut in
  half and *neither* chunk captures it fully. Overlap repeats the last 50 chars of chunk 1 at the start of
  chunk 2, so boundary-spanning meaning survives in at least one complete chunk. Cheap insurance.

```java
package com.raj.documentservice.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ChunkingService {

    public List<String> splitIntoChunks(String text) {
        Document document = Document.from(text);                          // 1. wrap raw text
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 50); // 2. build splitter
        List<TextSegment> segments = splitter.split(document);            // 3. split into chunks
        return segments.stream()                                          // 4. pull plain text out
                .map(TextSegment::text)
                .toList();
    }
}
```

**Pieces:**
- `@Service` → Spring bean, so it can be `@Autowired` into the controller (same DI as the other projects).
- `Document` → LangChain4j wrapper around raw text; the splitter works on a `Document`, not a bare `String`.
- `TextSegment` → what each chunk actually is (text + optional metadata). `.text()` returns the plain string.
- Stream maps each `TextSegment` → its `.text()` → clean `List<String>`.

**Interview follow-ups:**
- *"Why not send the whole doc to the LLM?"* → context limits + cost + noise.
- *"How do you choose chunk size?"* → tradeoff: too big = blurry embeddings + wasted context;
  too small = fragmented ideas. 500 chars = reasonable middle for prose; tune it for your data.
- *"What happens at chunk boundaries?"* → the overlap solves it.

---

## Status / Next

- [x] Concept 1 — what a microservice is
- [x] Document Service scaffolded, running on 8081, LangChain4j 1.19.0 added via BOM
- [x] Upload endpoint written + tested (returns filename + char count)
- [x] Frontend test page (`upload.html`) + CORS handled with `@CrossOrigin`
- [ ] `ChunkingService` — code ready; explain the 500 / 50 back, then create it
- [ ] Wire `ChunkingService` into the controller → upload returns chunks instead of char count
- [ ] Then: Embedding Service (8082)
