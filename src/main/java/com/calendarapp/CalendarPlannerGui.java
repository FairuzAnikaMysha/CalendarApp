package com.calendarapp;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class CalendarPlannerGui {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter ALT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FLEX_DATE_TIME_T_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d'T'H:mm");
    private static final DateTimeFormatter FLEX_DATE_TIME_SPACE_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d H:mm");
    private static final DateTimeFormatter FLEX_DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final EventStore store;
    private final DefaultTableModel tableModel;
    private final JTable eventTable;

    private final JTextField titleField = new JTextField(20);
    private final JTextField descriptionField = new JTextField(20);
    private final JTextField startField = new JTextField(20);
    private final JTextField endField = new JTextField(20);
    private final JCheckBox recurrenceCheck = new JCheckBox("Enable recurrence");
    private final JTextField intervalField = new JTextField(6);
    private final JTextField timesField = new JTextField(4);
    private final JTextField recurrenceEndField = new JTextField(10);

    private final JComboBox<String> monthSelector = new JComboBox<>();
    private final JComboBox<Integer> yearSelector = new JComboBox<>();
    private final JTextArea calendarArea = new JTextArea(12, 30);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CalendarPlannerGui().show());
    }

    public CalendarPlannerGui() {
        store = new EventStore(Paths.get("data"));
        loadStore();

        tableModel = new DefaultTableModel(new Object[] {"ID", "Title", "Start", "End"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        eventTable = new JTable(tableModel);
        eventTable.setPreferredScrollableViewportSize(new Dimension(520, 200));
        refreshTable();
    }

    private void show() {
        JFrame frame = new JFrame("Calendar Planner");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Events", buildEventsPanel());
        tabs.add("Calendar", buildCalendarPanel());

        frame.add(tabs, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel buildEventsPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addField(form, gbc, row++, "Title", titleField);
        addField(form, gbc, row++, "Description", descriptionField);
        addField(form, gbc, row++, "Start (yyyy-MM-dd HH:mm)", startField);
        addField(form, gbc, row++, "End (yyyy-MM-dd HH:mm)", endField);

        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        form.add(recurrenceCheck, gbc);
        gbc.gridwidth = 1;

        addField(form, gbc, row++, "Interval (e.g. 1d, 1w)", intervalField);
        addField(form, gbc, row++, "Repeat times (0 for end date)", timesField);
        addField(form, gbc, row++, "Recurrence end date", recurrenceEndField);

        JButton createButton = new JButton("Create");
        JButton updateButton = new JButton("Update");
        JButton deleteButton = new JButton("Delete");
        JButton clearButton = new JButton("Clear");

        createButton.addActionListener(event -> createEvent());
        updateButton.addActionListener(event -> updateEvent());
        deleteButton.addActionListener(event -> deleteEvent());
        clearButton.addActionListener(event -> clearForm());

        JPanel buttonRow = new JPanel();
        buttonRow.add(createButton);
        buttonRow.add(updateButton);
        buttonRow.add(deleteButton);
        buttonRow.add(clearButton);

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Events"));
        tablePanel.add(new JScrollPane(eventTable), BorderLayout.CENTER);

        eventTable.getSelectionModel().addListSelectionListener(event -> populateFormFromSelection());

        panel.add(form, BorderLayout.WEST);
        panel.add(tablePanel, BorderLayout.CENTER);
        panel.add(buttonRow, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildCalendarPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        for (int i = 0; i < 12; i++) {
            monthSelector.addItem(YearMonth.now().withMonth(i + 1).getMonth().name());
        }
        monthSelector.setSelectedIndex(YearMonth.now().getMonthValue() - 1);
        int currentYear = YearMonth.now().getYear();
        for (int year = currentYear - 5; year <= currentYear + 5; year++) {
            yearSelector.addItem(year);
        }
        yearSelector.setSelectedItem(currentYear);
        JButton renderButton = new JButton("Render calendar");
        renderButton.addActionListener(event -> renderCalendar());

        JPanel top = new JPanel();
        top.add(new JLabel("Month:"));
        top.add(monthSelector);
        top.add(new JLabel("Year:"));
        top.add(yearSelector);
        top.add(renderButton);

        calendarArea.setEditable(false);
        calendarArea.setFont(calendarArea.getFont().deriveFont(14f));
        renderCalendar();

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(calendarArea), BorderLayout.CENTER);
        return panel;
    }

    private void renderCalendar() {
        int monthIndex = monthSelector.getSelectedIndex() + 1;
        int year = (int) yearSelector.getSelectedItem();
        YearMonth month = YearMonth.of(year, monthIndex);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        Map<LocalDate, List<EventOccurrence>> occurrences = store.occurrencesBetween(start, end);
        java.util.Set<LocalDate> highlightedDates = buildHighlightedDates(start, end);

        StringBuilder builder = new StringBuilder();
        builder.append(month.getMonth()).append(" ").append(month.getYear()).append("\n");
        builder.append("Su Mo Tu We Th Fr Sa\n");
        int startOffset = start.getDayOfWeek().getValue() % 7;
        for (int i = 0; i < startOffset; i++) {
            builder.append("   ");
        }
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate current = month.atDay(day);
            boolean hasEvent = highlightedDates.contains(current);
            String marker = hasEvent ? "*" : " ";
            builder.append(String.format("%2d%s", day, marker));
            if ((day + startOffset) % 7 == 0) {
                builder.append("\n");
            } else {
                builder.append(" ");
            }
        }
        builder.append("\n\nEvents:\n");
        for (Map.Entry<LocalDate, List<EventOccurrence>> entry : occurrences.entrySet()) {
            for (EventOccurrence occurrence : entry.getValue()) {
                LocalDate startDate = occurrence.getStart().toLocalDate();
                LocalDate endDate = occurrence.getEnd().toLocalDate();
                builder.append("* ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(occurrence.getTitle())
                        .append(" (")
                        .append(occurrence.getStart().format(TIME_FORMAT))
                        .append(" - ")
                        .append(occurrence.getEnd().format(TIME_FORMAT))
                        .append(")");
                if (!startDate.equals(endDate)) {
                    builder.append(" [")
                            .append(startDate)
                            .append(" to ")
                            .append(endDate)
                            .append("]");
                }
                builder.append("\n");
            }
        }
        calendarArea.setText(builder.toString());
    }

    private java.util.Set<LocalDate> buildHighlightedDates(LocalDate monthStart, LocalDate monthEnd) {
        java.util.Set<LocalDate> highlighted = new java.util.HashSet<>();
        Map<LocalDate, List<EventOccurrence>> occurrences = store.occurrencesBetween(monthStart, monthEnd);
        for (List<EventOccurrence> dayOccurrences : occurrences.values()) {
            for (EventOccurrence occurrence : dayOccurrences) {
                LocalDate eventStart = occurrence.getStart().toLocalDate();
                LocalDate eventEnd = occurrence.getEnd().toLocalDate();
                LocalDate cursor = eventStart;
                while (!cursor.isAfter(eventEnd)) {
                    if (!cursor.isBefore(monthStart) && !cursor.isAfter(monthEnd)) {
                        highlighted.add(cursor);
                    }
                    cursor = cursor.plusDays(1);
                }
            }
        }
        return highlighted;
    }

    private void createEvent() {
        try {
            Event event = store.createEvent(
                    titleField.getText().trim(),
                    descriptionField.getText().trim(),
                    parseDateTimeInput(startField.getText().trim()),
                    parseDateTimeInput(endField.getText().trim())
            );
            applyRecurrence(event.getId());
            saveStore();
            refreshTable();
            clearForm();
            renderCalendar();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void updateEvent() {
        int row = eventTable.getSelectedRow();
        if (row < 0) {
            showError("Select an event first.");
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        Event event = store.findEvent(id).orElse(null);
        if (event == null) {
            showError("Event not found.");
            return;
        }
        try {
            event.setTitle(titleField.getText().trim());
            event.setDescription(descriptionField.getText().trim());
            event.setStart(parseDateTimeInput(startField.getText().trim()));
            event.setEnd(parseDateTimeInput(endField.getText().trim()));
            if (recurrenceCheck.isSelected()) {
                applyRecurrence(event.getId());
            } else {
                store.clearRecurrence(event.getId());
            }
            saveStore();
            refreshTable();
            renderCalendar();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void deleteEvent() {
        int row = eventTable.getSelectedRow();
        if (row < 0) {
            showError("Select an event first.");
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        store.deleteEvent(id);
        saveStore();
        refreshTable();
        clearForm();
        renderCalendar();
    }

    private void applyRecurrence(int eventId) {
        if (!recurrenceCheck.isSelected()) {
            store.clearRecurrence(eventId);
            return;
        }
        String interval = intervalField.getText().trim();
        if (interval.isEmpty()) {
            throw new IllegalArgumentException("Interval is required for recurrence.");
        }
        int times = parseInteger(timesField.getText().trim(), "Repeat times must be a number.");
        String endDate = "0";
        if (times == 0) {
            LocalDate parsed = parseDateInput(recurrenceEndField.getText().trim());
            if (parsed == null) {
                throw new IllegalArgumentException("Recurrence end date is invalid.");
            }
            endDate = parsed.toString();
        }
        RecurrenceRule rule = RecurrenceRule.parse(eventId, interval, times, endDate);
        store.setRecurrence(rule);
    }

    private void populateFormFromSelection() {
        int row = eventTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        Event event = store.findEvent(id).orElse(null);
        if (event == null) {
            return;
        }
        titleField.setText(event.getTitle());
        descriptionField.setText(event.getDescription());
        startField.setText(event.getStart().format(DATE_TIME_FORMAT));
        endField.setText(event.getEnd().format(DATE_TIME_FORMAT));
        recurrenceCheck.setSelected(store.findRecurrence(id).isPresent());
    }

    private void clearForm() {
        titleField.setText("");
        descriptionField.setText("");
        startField.setText("");
        endField.setText("");
        recurrenceCheck.setSelected(false);
        intervalField.setText("");
        timesField.setText("");
        recurrenceEndField.setText("");
        eventTable.clearSelection();
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (Event event : store.listEvents()) {
            tableModel.addRow(new Object[] {
                    event.getId(),
                    event.getTitle(),
                    event.getStart().format(DATE_TIME_FORMAT),
                    event.getEnd().format(DATE_TIME_FORMAT)
            });
        }
    }

    private void saveStore() {
        try {
            store.save();
        } catch (IOException ex) {
            showError(ex.getMessage());
        }
    }

    private void loadStore() {
        try {
            store.load();
        } catch (IOException ex) {
            showError(ex.getMessage());
        }
    }

    private void addField(JPanel panel, GridBagConstraints gbc, int row, String labelText, JTextField field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel(labelText + ":"), gbc);
        gbc.gridx = 1;
        panel.add(field, gbc);
    }

    private LocalDateTime parseDateTimeInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Date/time is required.");
        }
        try {
            return LocalDateTime.parse(input, DATE_TIME_FORMAT);
        } catch (Exception ex) {
            // ignore and try alternate format
        }
        try {
            return LocalDateTime.parse(input, ALT_DATE_TIME_FORMAT);
        } catch (Exception ex) {
            // ignore and try date-only format
        }
        try {
            return LocalDate.parse(input, DATE_ONLY_FORMAT).atStartOfDay();
        } catch (Exception ex) {
            // ignore and try flexible date-only format
        }
        try {
            return LocalDate.parse(input, FLEX_DATE_ONLY_FORMAT).atStartOfDay();
        } catch (Exception ex) {
            // ignore and try flexible date time formats
        }
        try {
            return LocalDateTime.parse(input, FLEX_DATE_TIME_T_FORMAT);
        } catch (Exception ex) {
            // ignore and try flexible date time format with space
        }
        try {
            return LocalDateTime.parse(input, FLEX_DATE_TIME_SPACE_FORMAT);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid date/time format.");
        }
    }

    private LocalDate parseDateInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(input);
        } catch (Exception ex) {
            // ignore and try to parse date-time instead
        }
        try {
            return LocalDate.parse(input, FLEX_DATE_ONLY_FORMAT);
        } catch (Exception ex) {
            // ignore and try to parse date-time instead
        }
        LocalDateTime parsed = null;
        try {
            parsed = parseDateTimeInput(input);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        return parsed.toLocalDate();
    }

    private int parseInteger(String input, String errorMessage) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "Calendar Planner", JOptionPane.ERROR_MESSAGE);
    }
}
