package com.raj.documentservice.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.raj.documentservice.service.ChunkingService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*") 
public class DocumentController {
	private final ChunkingService chunkingService;

	public DocumentController(ChunkingService chunkingService) {
        this.chunkingService = chunkingService;
    }
    @PostMapping("/upload")
    public Map<String, Object> uploadDocument(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<String> chunks = chunkingService.splitIntoChunks(content);
        return Map.of(
            "filename", filename,
            "characters", content.length(),
            "totalChunks", chunks.size(),
            "chunks", chunks,
            "message", "File received successfully"
        );
    }
}