# CalendarApp

Java-based CLI calendar and scheduler application for the FOP Group Assignment 2025/26.

## Team Members

- Fairuz Anika Mysha (22111992) – Repo Lead
- Lincoln () – Storage
- Ulvee () – Recurrence
- Mark() – Search & Views

## How to Run

Compile and run from the repository root:

```bash
javac -d out src/main/java/com/calendarapp/*.java
java -cp out com.calendarapp.Main
```

Or run with Maven (NetBeans-friendly):

```bash
mvn -q exec:java
```

Launch the GUI directly:

```bash
java -cp out com.calendarapp.CalendarPlannerGui
```

Or via Maven:

```bash
mvn -q -Dexec.mainClass=com.calendarapp.CalendarPlannerGui exec:java
```

Data is stored in the `data/` directory relative to the repository root.

Event IDs are auto-generated and stored in `data/event.csv`; you do not need to specify them manually.
