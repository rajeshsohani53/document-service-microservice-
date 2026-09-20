package com.raj.documentservice.service;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
@Service
public class ChunkingService {
	public List<String> splitIntoChunks(String text) 
	{
		// wrap the raw text in a lngchain Document object
		//so 
		Document document=Document.from(text);
		//next we build the text splitter 
		 DocumentSplitter splitters=DocumentSplitters.recursive(500,50);
		 // 3. Split the document into segments (chunks)
        List<TextSegment> segments = splitters.split(document);
        return segments.stream()
                .map(TextSegment::text)
                .toList();
	}
}
