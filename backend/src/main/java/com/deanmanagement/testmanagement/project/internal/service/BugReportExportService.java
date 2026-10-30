package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportFilter;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.shared.CsvCells;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.TreeSet;
import java.util.UUID;

/** The filtered bug list as CSV (TM48), capped like the activity export. */
@Service
@RequiredArgsConstructor
public class BugReportExportService {

    static final int MAX_ROWS = 10_000;
    private static final int PAGE_SIZE = 500;
    private static final String[] HEADERS = {"key", "title", "status", "resolution", "priority", "environment",
            "assignee", "reporter", "test run", "labels", "found in", "fixed in", "created", "updated"};

    private final BugReportService bugReportService;

    @Transactional(readOnly = true)
    public byte[] exportCsv(UUID projectId, BugReportFilter filter, Sort sort) {
        // Ties fall back to the id, so pages never repeat or skip a bug.
        Sort stable = sort.and(Sort.by("id"));
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {
            int written = 0;
            int pageNumber = 0;
            Page<BugReportResponse> page;
            do {
                page = bugReportService.search(projectId, filter, PageRequest.of(pageNumber++, PAGE_SIZE, stable));
                for (BugReportResponse bug : page.getContent()) {
                    if (written == MAX_ROWS) {
                        printer.printRecord("Stopped after " + MAX_ROWS + " rows of " + page.getTotalElements()
                                + ". Narrow the filter to export the rest.");
                        return bytes(out);
                    }
                    printer.printRecord(row(bug));
                    written++;
                }
            } while (page.hasNext());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes(out);
    }

    private static Object[] row(BugReportResponse bug) {
        String labels = bug.labels() == null ? "" : String.join(", ", new TreeSet<>(bug.labels()));
        return new Object[]{bug.key(), CsvCells.safe(bug.title()), bug.status(), bug.resolution(), bug.priority(),
                CsvCells.safe(bug.environment()), CsvCells.safe(bug.assigneeName()), CsvCells.safe(bug.reporterName()),
                CsvCells.safe(bug.testRunName()), CsvCells.safe(labels), CsvCells.safe(bug.foundInPlanName()),
                CsvCells.safe(bug.fixedInPlanName()), bug.createdAt(), bug.updatedAt()};
    }

    private static byte[] bytes(StringWriter out) {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
