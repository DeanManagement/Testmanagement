package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.StepImage;
import com.deanmanagement.testmanagement.project.internal.service.StepImageService;
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
@RequestMapping("/api/step-images")
@Tag(name = "Step Images", description = "Reference image upload and retrieval for test steps")
@RequiredArgsConstructor
public class StepImageController {

    private final StepImageService stepImageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> upload(@RequestParam UUID testStepId,
                                    @RequestParam MultipartFile file) throws IOException {
        StepImage image = stepImageService.upload(
                testStepId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );
        return Map.of("id", image.getId());
    }

    /**
     * Returns the bytes with an exact {@code Content-Length}.
     *
     * <p>This used to hand back a {@code StreamingResponseBody}, which bought nothing — the image
     * is already fully in memory, having come out of a {@code byte[]} column — and cost a
     * {@code Content-Length}. A response without one is chunked, and a chunked body that ends
     * unexpectedly is a stream reset rather than a short file: over HTTP/2 and HTTP/3 the browser
     * reports {@code ERR_QUIC_PROTOCOL_ERROR} on a request whose status line said 200. Declaring
     * the length lets every hop frame the body correctly and length-check it.
     */
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id,
                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        // findById enforces project access (PRD-001 §4.4) before any bytes are served.
        StepImage image = stepImageService.findById(id);
        return ImageResponses.of(image.getUpdatedAt(), image.getContentType(), image.getFileName(), "image",
                image.getData(), ifNoneMatch);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        stepImageService.delete(id);
    }
}
