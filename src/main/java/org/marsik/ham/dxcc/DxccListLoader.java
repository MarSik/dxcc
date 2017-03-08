package org.marsik.ham.dxcc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;

public class DxccListLoader {
    private static final Pattern ENTITY_RE = Pattern.compile("^\\s*" +
            "(?<prefixes>[A-Z0-9/,_-]+)?" +
            "((?<notes>(\\(\\d+\\),?)+)?|(?<arrl>[*#^]+)?){0,2}" +
            "\\s+" +
            "(?<name>[A-Z0-9,&()'. /-]*[A-Z).])" +
            "\\s+" +
            "(?<continent>((AS|EU|AF|OC|AN|NA|SA),?)+)" +
            "\\s+" +
            "(?<itu>(\\d{2}(,|-\\d{2})?)+|\\([A-Z]\\))" +
            "\\s+" +
            "(?<cq>(\\d{2}(,|-\\d{2})?)+|\\([A-Z]\\))" +
            "\\s+" +
            "(?<id>\\d{3})" +
            "\\s*" +
            "$", Pattern.CASE_INSENSITIVE);

    private static final Pattern NOTE_ID_RE = Pattern.compile("\\((?<id>\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL_ENTITY_RE = Pattern.compile("entities total:\\s+(?<count>\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern NOTE_RE = Pattern.compile("\\s+(?<id>\\d+)\\s+(?<text>.*)");

    private static enum ParserState {
        PREAMBLE,
        DXCC,
        NOTES,
        OTHER
    }

    @AllArgsConstructor
    private static class Column {
        int start; // included
        int end; // excluded
    }

    private static class Columns {
        Column prefix;
        Column entity;
        Column continent;
        Column itu;
        Column cq;
        Column id;
    }

    public DxccList loadFromArrlDxccList(InputStream is) throws IOException {
        DxccList dxccList = new DxccList();

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        boolean deleted = false;
        ParserState state = ParserState.PREAMBLE;
        Columns columns = null;

        while((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                state = ParserState.OTHER;
                continue;
            }

            // Full DXCC line
            Matcher matcher = ENTITY_RE.matcher(line);
            if (matcher.matches()) {
                state = ParserState.DXCC;
                dxccList.getList().add(new Dxcc(
                        Integer.parseInt(matcher.group("id")),
                        matcher.group("prefixes"),
                        matcher.group("arrl"),
                        matcher.group("name"),
                        parseCsv(matcher.group("cq")).collect(Collectors.toList()),
                        parseCsv(matcher.group("itu")).collect(Collectors.toList()),
                        parseContinents(matcher.group("continent")).collect(Collectors.toList()),
                        deleted,
                        null,
                        null,
                        findNotes(matcher.group("notes"))
                ));

                // Record column info
                if (columns == null) {
                    columns = new Columns();
                    columns.id = new Column(matcher.start("id"), line.length());
                    columns.cq = new Column(matcher.start("cq"), columns.id.start);
                    columns.itu = new Column(matcher.start("itu"), columns.cq.start);
                    columns.continent = new Column(matcher.start("continent"), columns.itu.start);
                    columns.entity = new Column(matcher.start("name"), columns.continent.start);
                    columns.prefix = new Column(matcher.start("prefixes"), columns.entity.start);
                }

                continue;
            }

            // Partial DXCC line, append the last processed entity
            if (state == ParserState.DXCC && columns != null) {
                // Fill up the line
                line += CharBuffer.allocate(columns.id.end - line.length())
                        .toString().replace('\0', ' ');
                Dxcc last = dxccList.getList().get(dxccList.getList().size() - 1);
                parseContinents(line.substring(columns.continent.start, columns.continent.end).trim())
                        .forEach(last.getContinent()::add);
                parseCsv(line.substring(columns.cq.start, columns.cq.end).trim())
                        .forEach(last.getCq()::add);
                parseCsv(line.substring(columns.itu.start, columns.itu.end).trim())
                        .forEach(last.getItu()::add);
            }

            // Total entity count line
            matcher = TOTAL_ENTITY_RE.matcher(line);
            if (matcher.find()) {
                state = ParserState.PREAMBLE;
                dxccList.setCount(Integer.parseInt(matcher.group("count")));
                continue;
            }

            // Notes
            matcher = NOTE_RE.matcher(line);
            if (matcher.matches()) {
                state = ParserState.NOTES;
                dxccList.getNotes().put(Integer.parseInt(matcher.group("id")), matcher.group("text"));
            }

            // Document title line
            if ("CURRENT ENTITIES".equalsIgnoreCase(line)) {
                state = ParserState.PREAMBLE;
                deleted = false;
            } else if ("DELETED ENTITIES".equalsIgnoreCase(line)) {
                state = ParserState.PREAMBLE;
                deleted = true;
            }
        }

        return dxccList;
    }

    private Stream<String> parseCsv(String input) {
        return Stream.of(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    private Stream<String> parseContinents(String input) {
        return Stream.of(input.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase);
    }

    private List<Integer> findNotes(String notes) {
        List<Integer> notesList = new ArrayList<>();
        if (notes == null) {
            return notesList;
        }

        Matcher m = NOTE_ID_RE.matcher(notes);
        while (m.find()) {
            notesList.add(Integer.parseInt(m.group("id")));
        }
        return notesList;
    }
}
