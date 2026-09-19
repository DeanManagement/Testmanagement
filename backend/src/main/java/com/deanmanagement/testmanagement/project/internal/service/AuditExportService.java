package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.audit.AuditEntryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.audit.AuditFilter;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The filtered activity as CSV (PRD-046 §3.3). Capped, with a last line saying so, since an audit
 * log grows without bound and a spreadsheet does not need all of it.
 */
@Service
@RequiredArgsConstructor
public class AuditExportService {

    static final int MAX_ROWS = 10_000;
    private static final int PAGE_SIZE = 500;
    private static final String[] HEADERS = {"time", "user", "action", "type", "object", "details", "changes"};

    private final AuditService auditService;

    @Transactional(readOnly = true)
    public byte[] exportCsv(UUID projectId, AuditFilter filter, Sort sort) {
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {
            int written = 0;
            Page<AuditEntryResponse> page;
            int pageNumber = 0;
            do {
                page = auditService.find(projectId, filter, PageRequest.of(pageNumber++, PAGE_SIZE, sort));
                for (AuditEntryResponse entry : page.getContent()) {
                    if (written == MAX_ROWS) {
                        printer.printRecord("Stopped after " + MAX_ROWS + " rows of " + page.getTotalElements()
                                + ". Narrow the filter to export the rest.");
                        return bytes(out);
                    }
                    printer.printRecord(row(entry));
                    written++;
                }
            } while (page.hasNext());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes(out);
    }

    private static Object[] row(AuditEntryResponse entry) {
        String changes = entry.changes().stream()
                .map(c -> c.field() + ": " + nullToDash(c.from()) + " → " + nullToDash(c.to()))
                .collect(Collectors.joining("; "));
        return new Object[]{entry.createdAt(), CsvCells.safe(entry.userDisplayName()), entry.action(),
                entry.entityType(), CsvCells.safe(entry.entityName()), CsvCells.safe(entry.details()),
                CsvCells.safe(changes)};
    }

    private static String nullToDash(String value) {
        return value == null ? "–" : value;
    }

    private static byte[] bytes(StringWriter out) {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
