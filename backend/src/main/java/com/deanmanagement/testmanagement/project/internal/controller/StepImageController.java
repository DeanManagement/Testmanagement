package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.StepImage;
import com.deanmanagement.testmanagement.project.internal.service.ImageMediaTypes;
import com.deanmanagement.testmanagement.project.internal.service.StepImageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    @GetMapping("/{id}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id,
                                                          @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        // findById enforces project access (PRD-001 §4.4) before any bytes are served.
        StepImage image = stepImageService.findById(id);
        String etag = "\"" + image.getUpdatedAt().toEpochMilli() + "\"";

        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .build();
        }

        byte[] data = image.getData();
        StreamingResponseBody body = out -> new ByteArrayInputStream(data).transferTo(out);
        // Legacy rows may predate the upload allowlist — never echo an unsafe stored type,
        // or an uploaded text/html "image" would execute script in the app origin.
        boolean safeImageType = ImageMediaTypes.isAllowed(image.getContentType());
        MediaType contentType = safeImageType
                ? MediaType.parseMediaType(image.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        ContentDisposition disposition = (safeImageType ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(image.getFileName() != null ? image.getFileName() : "image", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                // private: per-user authorized response — shared caches must never store it (PRD-017).
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .eTag(etag)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .contentType(contentType)
                .body(body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        stepImageService.delete(id);
    }
}
