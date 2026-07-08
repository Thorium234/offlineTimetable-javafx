# Thorium Timetable Generator

Offline desktop school timetable generator built with Java 21, JavaFX, SQLite, and Clean Architecture.

## Architecture

| Module | Responsibility |
|--------|----------------|
| `thorium-domain` | Entities, constraints, scheduling engine (greedy + backtracking) |
| `thorium-application` | Use cases and repository ports |
| `thorium-infrastructure` | SQLite persistence, PDF/Excel export |
| `thorium-ui` | JavaFX views and controllers |

Design documentation: `.thor/design/`

## Requirements

- Java 21+ (project will not build on Java 17)
- Maven 3.9+

## Build

This project requires **JDK 21**. If your default Java is 17, set `JAVA_HOME` first:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
java -version   # should show 21
```

If you use SDKMAN:

```bash
sdk env install   # reads .sdkmanrc
```

Build and run all JUnit tests:

```bash
mvn clean test
```

Full install:

```bash
mvn clean install
```

## Run

```bash
cd thorium-ui
mvn javafx:run
```

Both build and run must use Java 21 — otherwise you will see `release version 21 not supported` or `UnsupportedClassVersionError`.

Database file: `~/.thorium/timetable.db`

## Features

- Teacher, subject, class, and assignment management
- Period and break configuration
- Teacher availability (unavailable slots)
- Timetable generation with hard constraints:
  - No teacher/class clashes
  - Teacher availability respected
  - Weekly lesson counts enforced
  - CBC no-double-lesson rule
- Soft constraint scoring (spread, workload, consecutive lessons)
- PDF and Excel export
- Phase 3 optimization extension points (stubs only)

## Scheduling Phases

1. **Greedy** — places hardest assignments first using soft-constraint scoring
2. **Backtracking** — resolves conflicts when greedy placement is incomplete
3. **Optimization** — extension points prepared for GA, Tabu Search, Simulated Annealing
