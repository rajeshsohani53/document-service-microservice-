# Document Service — Chunking Verified (Standalone Note)

> Focused note for the milestone where chunking was wired in, an error was fixed,
> and the output was verified. Document Service is now **complete**.

---

## 1. The Error I Hit — "bean could not be found"

```
Parameter 0 of constructor in ...DocumentController required a bean of type
'...service.ChunkingService' that could not be found.
```

**What it means:** I told `DocumentController` it needs a `ChunkingService` in its constructor,
but Spring had no such **bean** to inject → couldn't build the controller → app refused to start.

**Cause:** `ChunkingService` was missing the **`@Service`** annotation. Spring only manages classes
it's told to. Without a stereotype annotation, the class is invisible to Spring.

**Fix:**
```java
import org.springframework.stereotype.Service;

@Service   // ← this registers it as a bean
public class ChunkingService { ... }
```

**Interview takeaway (memorize this one line):**
> Spring only injects beans it manages. A class becomes a bean via a stereotype annotation
> (`@Service` / `@Component` / `@Controller`), and it must sit inside the component-scan path —
> the main class's package or a sub-package. That explains ~90% of "bean could not be found" errors.

---

## 2. Wiring the Service into the Controller

Used **constructor injection** (not field `@Autowired`):

```java
private final ChunkingService chunkingService;

public DocumentController(ChunkingService chunkingService) {
    this.chunkingService = chunkingService;
}
```

**Why constructor injection is preferred:**
- Dependency is explicit (you can see it in the constructor signature).
- Field can be `final` → can't be swapped out after construction.
- Easy to unit-test (pass a mock in the constructor).
- With a single constructor, Spring auto-injects — no `@Autowired` needed.

Controller just receives the request and **delegates**; the splitting logic lives in the service.
(Controller = HTTP layer, Service = logic layer.)

---

## 3. Reading the Output — Proof the Concept Works

Uploaded an 8,556-char `.txt` → got **24 chunks** back. Two things to point at in an interview:

### (a) Split on natural boundaries
Every chunk starts/ends at a section header, numbered line, or paragraph break.
No chunk cuts a word or sentence in half → the "recursive" splitter preferring
paragraphs → lines → sentences → words is working.

### (b) Overlap is visible (the money shot)
- Chunk 1 ends with `"There are 9 of these objects:"` and chunk 2 **begins** with the same line.
- Chunks 10 → 11 → 12 all carry `"directly onto the page using out.println(...)"` at the seam.

That repeated text **is** the 50-char overlap. If a question's answer sat on that boundary,
it survives whole in at least one chunk.

---

## 4. Why 24 Chunks, Not ~20?

The file has lots of short lines and section dividers, so the splitter hits a natural
paragraph/line boundary **before** filling all 500 characters, and cuts early.

**Key insight (precise interview answer):**
> **500 is a ceiling, not a target.** Real chunks are often shorter than the max because the
> recursive splitter cuts at the nearest natural boundary rather than padding to exactly 500.

---

## 5. Status

- [x] `@Service` bean issue fixed
- [x] `ChunkingService` wired into controller via constructor injection
- [x] Upload → read → chunk verified (24 clean chunks, overlap visible)
- [x] **Document Service (8081) — COMPLETE**, one clean job, defensible line by line

**Next service:** Embedding Service (8082) — one job: *text → vector*.

**Commit:** `feat: chunk uploaded documents via ChunkingService (recursive 500/50)`
