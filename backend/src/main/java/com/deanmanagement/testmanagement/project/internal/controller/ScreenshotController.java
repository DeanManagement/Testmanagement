package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Screenshot;
import com.deanmanagement.testmanagement.project.internal.service.ScreenshotService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/screenshots")
@Tag(name = "Screenshots", description = "Screenshot upload and retrieval for step results")
@RequiredArgsConstructor
public class ScreenshotController {

    private final ScreenshotService screenshotService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> upload(@RequestParam UUID stepResultId,
                                    @RequestParam MultipartFile file) throws IOException {
        Screenshot screenshot = screenshotService.upload(
                stepResultId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );
        return Map.of("id", screenshot.getId());
    }

    /** Fixed-length rather than streamed, for the reason spelled out on StepImageController. */
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id,
                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        // findById enforces project access (PRD-001 §4.4) before any bytes are served.
        Screenshot screenshot = screenshotService.findById(id);
        return ImageResponses.of(screenshot.getUpdatedAt(), screenshot.getContentType(), screenshot.getFileName(), "screenshot",
                screenshot.getData(), ifNoneMatch);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        screenshotService.delete(id);
    }
}
