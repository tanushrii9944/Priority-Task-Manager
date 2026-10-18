[README_PriorityTaskManager.md](https://github.com/user-attachments/files/33628382/README_PriorityTaskManager.md)

# Priority Task Manager

A lightweight, console-based Java to-do application that tells you **what to work on next**. Every task has a priority and a deadline, and a **PriorityQueue** ranks them so the most urgent work always comes first.

## Features

- Add tasks with a title, priority (HIGH / MEDIUM / LOW) and deadline
- **"What should I do next?"** shows your top 3 tasks, ranked by a PriorityQueue
- Smart ordering:
  1. Overdue tasks first
  2. Then higher priority (HIGH > MEDIUM > LOW)
  3. Then the nearest deadline
- Status labels: OVERDUE, DUE TODAY, DUE SOON (3 days or less), ON TRACK, DONE
- Startup alert for overdue and due-today tasks
- Mark complete, edit (title, priority, deadline), delete and search tasks
- Completed-task history
- Dashboard with pending/completed counts, completion rate, overdue, due this week and a priority breakdown
- Quick date entry: `2026-12-31`, `today`, `tomorrow` or `+5` (5 days from now)
- Data saved to a CSV file - no database required

## Tech Stack

- Java 8 or above
- Java standard library only (no external dependencies)
- Data structures: `PriorityQueue`, `Comparator`, `ArrayList`

## Getting Started

### Prerequisites
- JDK 8+ installed (`java -version` to check)

### Compile
```bash
javac PriorityTaskManager.java
```

### Run
```bash
java PriorityTaskManager
```

## Usage

```
=========================================
          PRIORITY TASK MANAGER
=========================================

---------------- MENU ----------------
1. Add task
2. What should I do next?
3. View all pending tasks
4. Mark task as completed
5. Edit task
6. Delete task
7. View completed tasks
8. Search tasks
9. Dashboard
0. Exit
--------------------------------------
```

### Sample output

```
! 1 task(s) are OVERDUE
! 1 task(s) are due TODAY

== DO THESE NEXT ==
1. [LOW] Pay electricity bill  (due 2026-10-16, 2 day(s) overdue)
2. [HIGH] Submit project report  (due 2026-10-18, due today)
3. [MEDIUM] Prepare for interview  (due 2026-10-21, 3 days left)

== DASHBOARD ==
Pending tasks    : 5
Completed tasks  : 7
Completion rate  : 58%
Overdue          : 1
Due today        : 1
Due in 7 days    : 4
Pending by priority -> HIGH: 2, MEDIUM: 2, LOW: 1
```

## Project Structure

```
.
├── PriorityTaskManager.java   # Complete application source
├── README.md                  # Project documentation
└── tasks.csv                  # Auto-generated data file (created on first save)
```

## Data Format

```
id,title,priority,deadline,status,createdDate,completedDate
1,Submit project report,HIGH,2026-10-18,PENDING,2026-10-15,
2,Buy groceries,LOW,2026-10-12,DONE,2026-10-10,2026-10-11
```

## How It Works

1. Tasks are loaded from `tasks.csv` at startup.
2. All pending tasks are added to a `PriorityQueue` that uses a custom `Comparator`.
3. Polling the queue returns tasks in the exact order you should tackle them.
4. The comparator re-checks overdue status every time, so a task that passes its deadline automatically jumps ahead.
5. Every change is saved immediately.

## Future Improvements

- Recurring tasks (daily / weekly)
- Categories, tags and sub-tasks
- Email or desktop reminders
- Estimated time per task and daily workload planning
- GUI using JavaFX or Swing
- Database integration (SQLite / MySQL)

## License

This project is open source and available under the [MIT License](https://opensource.org/licenses/MIT).
