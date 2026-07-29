package com.miniproject.backend.integrations;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a single pasted ticket row out of an Excel-exported CSV — the other
 * common analyst intake source alongside Jira/email/meeting notes (many
 * teams track requirements in a spreadsheet before, instead of, or in
 * parallel with Jira). Header-driven column mapping, not fixed positions,
 * so the analyst can paste whatever column order their spreadsheet uses.
 * Returns the same {@link JiraTicketImportResponse} shape as the Jira
 * importer so the frontend's ticket-mapping code doesn't need a second path.
 */
@Service
public class CsvTicketImportService {

    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("key", "ticketKey"),
            Map.entry("ticketkey", "ticketKey"),
            Map.entry("id", "ticketKey"),
            Map.entry("title", "ticketTitle"),
            Map.entry("summary", "ticketTitle"),
            Map.entry("tickettitle", "ticketTitle"),
            Map.entry("priority", "priority"),
            Map.entry("reporter", "reporter"),
            Map.entry("assignee", "reporter"),
            Map.entry("owner", "reporter"),
            Map.entry("description", "description"),
            Map.entry("desc", "description"),
            Map.entry("acceptancecriteria", "acceptanceCriteria"),
            Map.entry("ac", "acceptanceCriteria"),
            Map.entry("comments", "comments"),
            Map.entry("notes", "comments"),
            Map.entry("comment", "comments"));

    public JiraTicketImportResponse importTicket(CsvTicketImportRequest request) {
        String text = request == null ? null : request.csvText();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("csvText is required");
        }

        List<List<String>> rows = parseCsv(text);
        if (rows.size() < 2) {
            throw new IllegalArgumentException("CSV must include a header row and at least one data row");
        }

        Map<Integer, String> columns = mapColumns(rows.get(0));
        if (columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "No recognized column headers found (expected e.g. key, title, priority, reporter, description, acceptance criteria, comments)");
        }

        List<String> dataRow = rows.get(1);
        Map<String, String> fields = new HashMap<>();
        columns.forEach((index, field) -> {
            if (index < dataRow.size()) {
                fields.put(field, dataRow.get(index).trim());
            }
        });

        String ticketTitle = fields.getOrDefault("ticketTitle", "");
        String description = fields.getOrDefault("description", "");
        if (ticketTitle.isBlank() && description.isBlank()) {
            throw new IllegalArgumentException("The first data row needs at least a title or description");
        }

        return new JiraTicketImportResponse(
                fields.getOrDefault("ticketKey", ""),
                ticketTitle,
                defaultIfBlank(fields.get("priority"), "Medium"),
                defaultIfBlank(fields.get("reporter"), "Spreadsheet import"),
                description,
                fields.getOrDefault("acceptanceCriteria", ""),
                fields.getOrDefault("comments", ""),
                "csv",
                "CSV",
                "Spreadsheet import",
                "",
                "Imported from spreadsheet",
                false,
                "Imported ticket from pasted CSV.");
    }

    private static Map<Integer, String> mapColumns(List<String> headerRow) {
        Map<Integer, String> columns = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String normalized = headerRow.get(i).trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
            String field = HEADER_ALIASES.get(normalized);
            if (field != null) {
                columns.put(i, field);
            }
        }
        return columns;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Minimal RFC4180-style parser: quoted fields, escaped "" quotes, commas/newlines inside quotes. */
    static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean rowStarted = false;

        int i = 0;
        int length = text.length();
        while (i < length) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < length && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                rowStarted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                rowStarted = true;
            } else if (c == '\r') {
                // ignore; \n (bare or following \r) ends the row
            } else if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                rowStarted = false;
            } else {
                field.append(c);
                rowStarted = true;
            }
            i++;
        }
        if (rowStarted || field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
