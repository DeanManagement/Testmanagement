package com.deanmanagement.testmanagement.project.internal.ci;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Cucumber JSON scenario tags carry the case key (PRD-040 §3.5). */
class CucumberKeyTagTest {

    private final CucumberJsonParser parser = new CucumberJsonParser(JsonMapper.builder().build());

    private CiResult parseWithTags(String tags) {
        String json = "[{\"name\":\"f\",\"elements\":[{\"name\":\"s\",\"type\":\"scenario\",\"tags\":[" + tags
                + "],\"steps\":[{\"keyword\":\"Given \",\"name\":\"a\",\"result\":{\"status\":\"passed\"}}]}]}]";
        return parser.parse(json.getBytes(StandardCharsets.UTF_8)).getFirst();
    }

    @Test
    void theKeyComesFromTheTmTag() {
        assertThat(parseWithTags("{\"name\":\"@smoke\"},{\"name\":\"@tm:PROJ-17\",\"line\":3}").testCaseKey())
                .isEqualTo("PROJ-17");
    }

    @Test
    void otherTagsAreNotAKey() {
        assertThat(parseWithTags("{\"name\":\"@smoke\"},{\"name\":\"@tm:\"}").testCaseKey()).isNull();
    }

    @Test
    void aReportWithoutTagsHasNoKey() {
        String json = "[{\"name\":\"f\",\"elements\":[{\"name\":\"s\",\"type\":\"scenario\",\"steps\":[]}]}]";

        assertThat(parser.parse(json.getBytes(StandardCharsets.UTF_8)).getFirst().testCaseKey()).isNull();
    }
}
