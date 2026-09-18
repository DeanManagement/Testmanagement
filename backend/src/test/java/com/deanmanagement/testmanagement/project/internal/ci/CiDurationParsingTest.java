package com.deanmanagement.testmanagement.project.internal.ci;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** CI reports carry durations that used to be parsed and thrown away (PRD-036 §3.2). */
class CiDurationParsingTest {

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Nested
    class JUnit {

        private final JUnitXmlParser parser = new JUnitXmlParser();

        private List<CiResult> parse(String timeAttribute) {
            return parser.parse(utf8("<testsuite name=\"s\"><testcase name=\"t\" classname=\"c\" "
                    + timeAttribute + "/></testsuite>"));
        }

        @Test
        void readsDecimalSecondsAsMilliseconds() {
            assertThat(parse("time=\"1.234\"").getFirst().durationMs()).isEqualTo(1234L);
        }

        @Test
        void roundsSubMillisecondValues() {
            assertThat(parse("time=\"0.0006\"").getFirst().durationMs()).isEqualTo(1L);
        }

        @Test
        void aMissingTimeIsUnknown() {
            assertThat(parse("").getFirst().durationMs()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"1,234", "-1", "fast", "  ", "1e400"})
        void anUnusableTimeIsUnknownAndNeverFailsTheUpload(String time) {
            assertThat(parse("time=\"" + time + "\"").getFirst().durationMs()).isNull();
        }
    }

    @Nested
    class Cucumber {

        private final CucumberJsonParser parser = new CucumberJsonParser(JsonMapper.builder().build());

        private List<CiResult> parse(String steps) {
            return parser.parse(utf8("[{\"name\":\"f\",\"elements\":[{\"name\":\"s\",\"type\":\"scenario\","
                    + "\"steps\":[" + steps + "]}]}]"));
        }

        @Test
        void sumsStepDurationsGivenInNanoseconds() {
            List<CiResult> results = parse("""
                    {"keyword":"Given ","name":"a","result":{"status":"passed","duration":1500000000}},
                    {"keyword":"Then ","name":"b","result":{"status":"passed","duration":250000000}}""");

            assertThat(results.getFirst().durationMs()).isEqualTo(1750L);
        }

        @Test
        void aScenarioWithoutStepDurationsIsUnknown() {
            List<CiResult> results = parse("""
                    {"keyword":"Given ","name":"a","result":{"status":"passed"}}""");

            assertThat(results.getFirst().durationMs()).isNull();
        }
    }
}
