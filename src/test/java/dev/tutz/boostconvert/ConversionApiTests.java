package dev.tutz.boostconvert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The whole HTTP flow against the real converters: upload, poll, download, delete. */
@SpringBootTest(properties = "boostconvert.jobs.storage-dir=target/test-storage")
class ConversionApiTests {

    @Autowired
    WebApplicationContext context;

    @Autowired
    JsonMapper json;

    MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.from(context);
    }

    @Test
    void uploadPollAndDownload() {
        MvcTestResult accepted = upload("holiday photo.png", TestFiles.png(64, 48), "jpg");

        assertThat(accepted).hasStatus(HttpStatus.ACCEPTED);
        JsonNode job = body(accepted);
        String id = job.get("id").stringValue();
        assertThat(accepted.getResponse().getHeader("Location")).endsWith("/api/v1/conversions/" + id);
        assertThat(job.get("converter").stringValue()).isEqualTo("png-to-jpg");

        JsonNode finished = awaitFinished(id);
        assertThat(finished.get("status").stringValue()).isEqualTo("SUCCEEDED");
        assertThat(finished.get("result").get("name").stringValue()).isEqualTo("holiday photo.jpg");
        assertThat(finished.get("expiresAt").isNull()).isFalse();

        MvcTestResult download = mvc.get().uri("/api/v1/conversions/{id}/result", id).exchange();
        assertThat(download).hasStatusOk().hasContentType("image/jpeg");
        assertThat(download.getResponse().getHeader("Content-Disposition"))
                .startsWith("attachment").contains("holiday%20photo.jpg");
        assertThat(download.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        byte[] bytes = download.getResponse().getContentAsByteArray();
        assertThat(bytes).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    }

    @Test
    void conversionErrorsAreReportedOnTheJob() {
        String id = body(upload("scan.pdf", TestFiles.pdf((String) null), "txt")).get("id").stringValue();

        JsonNode finished = awaitFinished(id);

        assertThat(finished.get("status").stringValue()).isEqualTo("FAILED");
        assertThat(finished.get("error").get("code").stringValue()).isEqualTo("PDF_HAS_NO_TEXT");
        assertThat(mvc.get().uri("/api/v1/conversions/{id}/result", id))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("RESULT_NOT_AVAILABLE");
    }

    @Test
    void uploadsThatCannotWorkAreRejectedImmediately() {
        assertThat(upload("invoice.png", TestFiles.pdf("hello"), "jpg"))
                .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .bodyJson().extractingPath("$.code").isEqualTo("FORMAT_MISMATCH");

        assertThat(upload("photo.png", TestFiles.png(4, 4), "csv"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.detail").isEqualTo("PNG cannot be converted to CSV. Available targets: jpg, pdf.");

        assertThat(upload("empty.csv", new byte[0], "json"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("EMPTY_FILE");
    }

    @Test
    void deletedJobsAreGone() {
        String id = body(upload("data.csv", "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8), "json")).get("id").stringValue();
        awaitFinished(id);

        assertThat(mvc.delete().uri("/api/v1/conversions/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/v1/conversions/{id}", id))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("JOB_NOT_FOUND");
    }

    @Test
    void catalogListsTheTenConverters() {
        assertThat(mvc.get().uri("/api/v1/converters"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[*].id").asArray().containsExactlyInAnyOrder(
                        "png-to-jpg", "jpg-to-png", "webp-to-png", "image-to-pdf",
                        "pdf-to-txt", "pdf-to-png", "md-to-html",
                        "csv-to-json", "json-to-csv", "xlsx-to-csv");
    }

    @Test
    void statsCountFinishedConversions() {
        String id = body(upload("notes.md", "# Hi".getBytes(StandardCharsets.UTF_8), "html")).get("id").stringValue();
        awaitFinished(id);

        JsonNode stats = body(mvc.get().uri("/api/v1/stats").exchange());
        assertThat(stats.get("totals").get("succeeded").asLong()).isPositive();
        assertThat(stats.get("queue").get("workers").asInt()).isEqualTo(2);
        assertThat(stats.get("converters").size()).isEqualTo(10);
    }

    private MvcTestResult upload(String name, byte[] content, String target) {
        return mvc.post().uri("/api/v1/conversions")
                .multipart()
                .file(new MockMultipartFile("file", name, "application/octet-stream", content))
                .param("target", target)
                .exchange();
    }

    private JsonNode awaitFinished(String id) {
        return await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50))
                .until(() -> body(mvc.get().uri("/api/v1/conversions/{id}", id).exchange()),
                        job -> !job.get("status").stringValue().matches("QUEUED|RUNNING"));
    }

    private JsonNode body(MvcTestResult result) {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
}
