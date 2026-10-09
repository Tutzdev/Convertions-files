package dev.tutz.boostconvert.job;

import dev.tutz.boostconvert.job.ConversionService.StoredResult;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/conversions")
public class ConversionController {

    private final ConversionService conversions;
    private final JobProperties properties;

    public ConversionController(ConversionService conversions, JobProperties properties) {
        this.conversions = conversions;
        this.properties = properties;
    }

    /** Queues a conversion and answers {@code 202 Accepted}; poll the {@code Location} until it finishes. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<JobResponse> submit(@RequestPart("file") MultipartFile file, @RequestParam("target") String target,
            UriComponentsBuilder uri) {
        ConversionJob job = conversions.submit(file, target);
        URI location = uri.path("/api/v1/conversions/{id}").buildAndExpand(job.id()).toUri();
        return ResponseEntity.accepted().location(location).body(JobResponse.from(job, properties.retention()));
    }

    @GetMapping("/{id}")
    JobResponse get(@PathVariable UUID id) {
        return JobResponse.from(conversions.get(id), properties.retention());
    }

    @GetMapping("/{id}/result")
    ResponseEntity<Resource> download(@PathVariable UUID id) {
        StoredResult result = conversions.result(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(result.file().name(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.file().format().mediaType()))
                .contentLength(result.file().sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // The result is user content: never let the browser sniff it into HTML or run it in our origin.
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(result.path()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        conversions.delete(id);
        return ResponseEntity.noContent().build();
    }
}
