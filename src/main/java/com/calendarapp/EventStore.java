package com.calendarapp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EventStore {
    private final Path dataDirectory;
    private final Path eventFile;
    private final Path recurrenceFile;
    private final Map<Integer, Event> events = new HashMap<>();
    private final Map<Integer, RecurrenceRule> recurrences = new HashMap<>();
    private int nextId = 1;

    public EventStore(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.eventFile = dataDirectory.resolve("event.csv");
        this.recurrenceFile = dataDirectory.resolve("recurrent.csv");
    }

    public void load() throws IOException {
        Files.createDirectories(dataDirectory);
        events.clear();
        recurrences.clear();
        nextId = 1;

        if (Files.exists(eventFile)) {
            List<String> lines = Files.readAllLines(eventFile);
            for (int i = 1; i < lines.size(); i++) {
                List<String> fields = CsvUtil.parseLine(lines.get(i));
                if (fields.size() < 5) {
                    continue;
                }
                int id = Integer.parseInt(fields.get(0));
                Event event = new Event(
                        id,
                        fields.get(1),
                        fields.get(2),
                        LocalDateTime.parse(fields.get(3)),
                        LocalDateTime.parse(fields.get(4))
                );
                events.put(id, event);
                nextId = Math.max(nextId, id + 1);
            }
        }

        if (Files.exists(recurrenceFile)) {
            List<String> lines = Files.readAllLines(recurrenceFile);
            for (int i = 1; i < lines.size(); i++) {
                List<String> fields = CsvUtil.parseLine(lines.get(i));
                if (fields.size() < 4) {
                    continue;
                }
                int eventId = Integer.parseInt(fields.get(0));
                RecurrenceRule rule = RecurrenceRule.parse(
                        eventId,
                        fields.get(1),
                        Integer.parseInt(fields.get(2)),
                        fields.get(3)
                );
                recurrences.put(eventId, rule);
            }
        }
    }

    public Event createEvent(String title, String description, LocalDateTime start, LocalDateTime end) {
        Event event = new Event(nextId++, title, description, start, end);
        events.put(event.getId(), event);
        return event;
    }

    public Optional<Event> findEvent(int id) {
        return Optional.ofNullable(events.get(id));
    }

    public List<Event> listEvents() {
        List<Event> list = new ArrayList<>(events.values());
        list.sort(Comparator.comparing(Event::getStart));
        return list;
    }

    public void deleteEvent(int id) {
        events.remove(id);
        recurrences.remove(id);
    }

    public void setRecurrence(RecurrenceRule rule) {
        if (rule == null) {
            return;
        }
        recurrences.put(rule.getEventId(), rule);
    }

    public void clearRecurrence(int eventId) {
        recurrences.remove(eventId);
    }

    public Optional<RecurrenceRule> findRecurrence(int eventId) {
        return Optional.ofNullable(recurrences.get(eventId));
    }

    public List<RecurrenceRule> listRecurrences() {
        return new ArrayList<>(recurrences.values());
    }

    public void save() throws IOException {
        Files.createDirectories(dataDirectory);
        writeEventsFile();
        writeRecurrenceFile();
    }

    private void writeEventsFile() throws IOException {
        Path tempFile = eventFile.resolveSibling("event.csv.tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
            writer.write("eventId,title,description,startDateTime,endDateTime");
            writer.newLine();
            for (Event event : listEvents()) {
                writer.write(event.getId() + "," +
                        CsvUtil.toCsvField(event.getTitle()) + "," +
                        CsvUtil.toCsvField(event.getDescription()) + "," +
                        event.getStart() + "," +
                        event.getEnd());
                writer.newLine();
            }
        }
        moveTempFile(tempFile, eventFile);
    }

    private void writeRecurrenceFile() throws IOException {
        Path tempFile = recurrenceFile.resolveSibling("recurrent.csv.tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
            writer.write("eventId,recurrentInterval,recurrentTimes,recurrentEndDate");
            writer.newLine();
            for (RecurrenceRule rule : listRecurrences()) {
                writer.write(rule.getEventId() + "," +
                        rule.toIntervalString() + "," +
                        rule.getTimes() + "," +
                        (rule.getEndDate() == null ? 0 : rule.getEndDate()));
                writer.newLine();
            }
        }
        moveTempFile(tempFile, recurrenceFile);
    }

    private void moveTempFile(Path tempFile, Path targetFile) throws IOException {
        try {
            Files.move(tempFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.FileSystemException ex) {
            throw new IOException("Unable to save " + targetFile.getFileName()
                    + ". Please close any program using the file and try again.", ex);
        }
    }

    public void backup(Path backupFile) throws IOException {
        Files.createDirectories(backupFile.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(backupFile)) {
            writer.write("#EVENTS");
            writer.newLine();
            if (Files.exists(eventFile)) {
                for (String line : Files.readAllLines(eventFile)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            writer.write("#RECURRENCES");
            writer.newLine();
            if (Files.exists(recurrenceFile)) {
                for (String line : Files.readAllLines(recurrenceFile)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        }
    }

    public void restore(Path backupFile, boolean replace) throws IOException {
        List<String> lines = Files.readAllLines(backupFile);
        List<String> eventLines = new ArrayList<>();
        List<String> recurrenceLines = new ArrayList<>();
        boolean inEvents = false;
        boolean inRecurrences = false;
        for (String line : lines) {
            if (line.equals("#EVENTS")) {
                inEvents = true;
                inRecurrences = false;
                continue;
            }
            if (line.equals("#RECURRENCES")) {
                inEvents = false;
                inRecurrences = true;
                continue;
            }
            if (inEvents) {
                eventLines.add(line);
            } else if (inRecurrences) {
                recurrenceLines.add(line);
            }
        }

        if (replace) {
            Files.createDirectories(dataDirectory);
            Files.write(eventFile, eventLines);
            Files.write(recurrenceFile, recurrenceLines);
        } else {
            Files.createDirectories(dataDirectory);
            appendLines(eventFile, eventLines);
            appendLines(recurrenceFile, recurrenceLines);
        }
        load();
    }

    private void appendLines(Path file, List<String> lines) throws IOException {
        if (lines.isEmpty()) {
            return;
        }
        if (!Files.exists(file)) {
            Files.write(file, lines);
            return;
        }
        List<String> existing = Files.readAllLines(file);
        if (existing.isEmpty()) {
            Files.write(file, lines);
            return;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(file, java.nio.file.StandardOpenOption.APPEND)) {
            int startIndex = 0;
            if (!existing.isEmpty() && !lines.isEmpty() && existing.get(0).equals(lines.get(0))) {
                startIndex = 1;
            }
            for (int i = startIndex; i < lines.size(); i++) {
                writer.newLine();
                writer.write(lines.get(i));
            }
        }
    }

    public Map<LocalDate, List<EventOccurrence>> occurrencesBetween(LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, List<EventOccurrence>> result = new HashMap<>();
        for (Event event : events.values()) {
            RecurrenceRule rule = recurrences.get(event.getId());
            List<EventOccurrence> occurrences = EventTimeline.expandOccurrences(event, rule, startDate, endDate);
            for (EventOccurrence occurrence : occurrences) {
                LocalDate date = occurrence.getStart().toLocalDate();
                result.computeIfAbsent(date, key -> new ArrayList<>()).add(occurrence);
            }
        }
        for (List<EventOccurrence> dayOccurrences : result.values()) {
            dayOccurrences.sort(Comparator.comparing(EventOccurrence::getStart));
        }
        return result;
    }
}
