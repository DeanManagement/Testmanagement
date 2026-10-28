package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.requirement.TraceabilityRowResponse;
import com.deanmanagement.testmanagement.shared.CsvCells;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The traceability matrix as CSV (TM20): one row per requirement and linked test case, with the
 * case's latest result; a requirement with no case gets one row with the case columns empty, so
 * the gap shows in the file instead of the requirement silently missing from it.
 */
@Service
@RequiredArgsConstructor
public class TraceabilityExportService {

    private static final String[] HEADERS = {"requirementId", "requirementTitle", "requirementCoverage",
            "testCaseKey", "testCaseTitle", "latestResult"};

    private final RequirementService requirementService;

    @Transactional(readOnly = true)
    public byte[] exportCsv(UUID projectId, UUID testPlanId) {
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {
            for (TraceabilityRowResponse row : requirementService.matrix(projectId, testPlanId)) {
                if (row.cells().isEmpty()) {
                    printer.printRecord(CsvCells.safe(row.externalId()), CsvCells.safe(row.title()), row.coverage(),
                            "", "", "");
                }
                for (TraceabilityRowResponse.Cell cell : row.cells()) {
                    printer.printRecord(CsvCells.safe(row.externalId()), CsvCells.safe(row.title()), row.coverage(),
                            cell.testCaseKey(), CsvCells.safe(cell.testCaseTitle()), cell.status());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
