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

## Status / Next

- [x] Concept 1 — what a microservice is
- [x] Document Service scaffolded, running on 8081, LangChain4j 1.19.0 added via BOM
- [x] Upload endpoint written (arrival only)
- [ ] Test the upload (curl/Postman)
- [ ] `ChunkingService` — actual splitting with `DocumentSplitters.recursive(500, 50)`
- [ ] Then: Embedding Service (8082)
