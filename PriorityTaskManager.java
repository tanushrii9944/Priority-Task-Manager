import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Priority Task Manager
 * ---------------------
 * A console-based to-do application where every task has a priority and a
 * deadline. A PriorityQueue decides what you should work on next:
 *
 *   1. Overdue tasks come first
 *   2. Then higher priority (HIGH > MEDIUM > LOW)
 *   3. Then the nearest deadline
 *
 * Tasks are saved to tasks.csv so they persist between runs.
 * Requires Java 8 or above. No external libraries needed.
 */
public class PriorityTaskManager {

    // ---------------------------------------------------------------
    // Priority levels
    // ---------------------------------------------------------------
    enum Priority {
        HIGH(1), MEDIUM(2), LOW(3);

        private final int rank; // lower rank = more important

        Priority(int rank) {
            this.rank = rank;
        }

        int getRank() {
            return rank;
        }
    }

    // ---------------------------------------------------------------
    // Task model
    // ---------------------------------------------------------------
    static class Task {
        private final int id;
        private String title;
        private Priority priority;
        private LocalDate deadline;
        private boolean done;
        private final LocalDate createdDate;
        private LocalDate completedDate; // null until the task is completed

        Task(int id, String title, Priority priority, LocalDate deadline,
             boolean done, LocalDate createdDate, LocalDate completedDate) {
            this.id = id;
            this.title = title;
            this.priority = priority;
            this.deadline = deadline;
            this.done = done;
            this.createdDate = createdDate;
            this.completedDate = completedDate;
        }

        int getId() { return id; }
        String getTitle() { return title; }
        Priority getPriority() { return priority; }
        LocalDate getDeadline() { return deadline; }
        boolean isDone() { return done; }
        LocalDate getCompletedDate() { return completedDate; }

        void setTitle(String title) { this.title = title; }
        void setPriority(Priority priority) { this.priority = priority; }
        void setDeadline(LocalDate deadline) { this.deadline = deadline; }

        void complete() {
            this.done = true;
            this.completedDate = LocalDate.now();
        }

        long daysLeft() {
            return ChronoUnit.DAYS.between(LocalDate.now(), deadline);
        }

        boolean isOverdue() {
            return !done && daysLeft() < 0;
        }

        String statusLabel() {
            if (done) return "DONE";
            long d = daysLeft();
            if (d < 0) return "OVERDUE";
            if (d == 0) return "DUE TODAY";
            if (d <= 3) return "DUE SOON";
            return "ON TRACK";
        }

        String toCsv() {
            return id + "," + clean(title) + "," + priority + "," + deadline + ","
                    + (done ? "DONE" : "PENDING") + "," + createdDate + ","
                    + (completedDate == null ? "" : completedDate.toString());
        }

        static Task fromCsv(String line) {
            String[] p = line.split(",", -1);
            if (p.length != 7) {
                throw new IllegalArgumentException("Invalid task record: " + line);
            }
            LocalDate completed = p[6].trim().isEmpty() ? null : LocalDate.parse(p[6].trim());
            return new Task(
                    Integer.parseInt(p[0].trim()),
                    p[1].trim(),
                    Priority.valueOf(p[2].trim()),
                    LocalDate.parse(p[3].trim()),
                    p[4].trim().equals("DONE"),
                    LocalDate.parse(p[5].trim()),
                    completed);
        }

        private static String clean(String s) {
            return s.replace(",", " ").trim();
        }
    }

    // ---------------------------------------------------------------
    // Ordering used by the PriorityQueue: "what should I do next?"
    // ---------------------------------------------------------------
    private static final Comparator<Task> NEXT_UP = Comparator
            .comparing((Task t) -> !t.isOverdue())            // overdue (false) comes first
            .thenComparing(t -> t.getPriority().getRank())    // HIGH before MEDIUM before LOW
            .thenComparing(Task::getDeadline)                 // nearest deadline first
            .thenComparing(Task::getId);                      // stable tie-breaker

    // ---------------------------------------------------------------
    // Task manager (business logic + persistence)
    // ---------------------------------------------------------------
    static class TaskManager {
        private final List<Task> tasks = new ArrayList<>();
        private final Path storagePath;
        private int nextId = 1;

        TaskManager(String fileName) {
            this.storagePath = Paths.get(fileName);
            load();
        }

        Task addTask(String title, Priority priority, LocalDate deadline) {
            Task t = new Task(nextId++, title.trim(), priority, deadline, false, LocalDate.now(), null);
            tasks.add(t);
            save();
            return t;
        }

        Task findById(int id) {
            for (Task t : tasks) {
                if (t.getId() == id) return t;
            }
            return null;
        }

        boolean delete(int id) {
            boolean removed = tasks.removeIf(t -> t.getId() == id);
            if (removed) save();
            return removed;
        }

        void saveChanges() {
            save();
        }

        /** Builds a priority queue of all pending tasks. */
        PriorityQueue<Task> pendingQueue() {
            PriorityQueue<Task> queue = new PriorityQueue<>(NEXT_UP);
            for (Task t : tasks) {
                if (!t.isDone()) queue.add(t);
            }
            return queue;
        }

        /** Returns the top N tasks by polling the priority queue. */
        List<Task> nextUp(int n) {
            PriorityQueue<Task> queue = pendingQueue();
            List<Task> result = new ArrayList<>();
            while (!queue.isEmpty() && result.size() < n) {
                result.add(queue.poll());
            }
            return result;
        }

        List<Task> allPendingOrdered() {
            return nextUp(Integer.MAX_VALUE);
        }

        List<Task> completed() {
            return tasks.stream()
                    .filter(Task::isDone)
                    .sorted(Comparator.comparing(Task::getCompletedDate).reversed())
                    .collect(Collectors.toList());
        }

        List<Task> search(String keyword) {
            String k = keyword.toLowerCase();
            return tasks.stream()
                    .filter(t -> t.getTitle().toLowerCase().contains(k))
                    .sorted(Comparator.comparing(Task::getDeadline))
                    .collect(Collectors.toList());
        }

        long countPending() { return tasks.stream().filter(t -> !t.isDone()).count(); }
        long countCompleted() { return tasks.stream().filter(Task::isDone).count(); }
        long countOverdue() { return tasks.stream().filter(Task::isOverdue).count(); }

        long countDueToday() {
            return tasks.stream().filter(t -> !t.isDone() && t.daysLeft() == 0).count();
        }

        long countDueWithin(int days) {
            return tasks.stream().filter(t -> !t.isDone() && t.daysLeft() >= 0 && t.daysLeft() <= days).count();
        }

        long countByPriority(Priority p) {
            return tasks.stream().filter(t -> !t.isDone() && t.getPriority() == p).count();
        }

        // ---------- persistence ----------
        private void load() {
            if (!Files.exists(storagePath)) return;
            try {
                for (String line : Files.readAllLines(storagePath, StandardCharsets.UTF_8)) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        Task t = Task.fromCsv(line);
                        tasks.add(t);
                        nextId = Math.max(nextId, t.getId() + 1);
                    } catch (RuntimeException ex) {
                        System.out.println("Skipping bad record -> " + ex.getMessage());
                    }
                }
            } catch (IOException e) {
                System.out.println("Could not read data file: " + e.getMessage());
            }
        }

        private void save() {
            List<String> lines = tasks.stream().map(Task::toCsv).collect(Collectors.toList());
            try {
                Files.write(storagePath, lines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("Could not save data: " + e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------
    // Console UI
    // ---------------------------------------------------------------
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        TaskManager manager = new TaskManager("tasks.csv");

        System.out.println("=========================================");
        System.out.println("          PRIORITY TASK MANAGER          ");
        System.out.println("=========================================");

        showStartupAlerts(manager);

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Enter choice: ");
            switch (choice) {
                case 1: addTask(manager); break;
                case 2: whatNext(manager); break;
                case 3: printTable(manager.allPendingOrdered(), "ALL PENDING TASKS (in priority order)"); break;
                case 4: completeTask(manager); break;
                case 5: editTask(manager); break;
                case 6: deleteTask(manager); break;
                case 7: printTable(manager.completed(), "COMPLETED TASKS"); break;
                case 8: searchTasks(manager); break;
                case 9: showDashboard(manager); break;
                case 0:
                    running = false;
                    System.out.println("Goodbye! Tasks saved to tasks.csv");
                    break;
                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n---------------- MENU ----------------");
        System.out.println("1. Add task");
        System.out.println("2. What should I do next?");
        System.out.println("3. View all pending tasks");
        System.out.println("4. Mark task as completed");
        System.out.println("5. Edit task");
        System.out.println("6. Delete task");
        System.out.println("7. View completed tasks");
        System.out.println("8. Search tasks");
        System.out.println("9. Dashboard");
        System.out.println("0. Exit");
        System.out.println("--------------------------------------");
    }

    private static void showStartupAlerts(TaskManager m) {
        long overdue = m.countOverdue();
        long today = m.countDueToday();
        if (overdue == 0 && today == 0) {
            System.out.println("\nNo overdue tasks and nothing due today.");
            return;
        }
        System.out.println();
        if (overdue > 0) System.out.println("! " + overdue + " task(s) are OVERDUE");
        if (today > 0) System.out.println("! " + today + " task(s) are due TODAY");
        whatNext(m);
    }

    private static void addTask(TaskManager m) {
        System.out.println("\n-- Add New Task --");
        String title = readNonEmpty("Task title: ");
        Priority priority = readPriority();
        LocalDate deadline = readDate("Deadline (YYYY-MM-DD, or 'today', 'tomorrow', '+3' for 3 days from now): ");
        Task t = m.addTask(title, priority, deadline);
        System.out.println("Added task #" + t.getId() + " - " + t.statusLabel());
    }

    private static void whatNext(TaskManager m) {
        List<Task> top = m.nextUp(3);
        if (top.isEmpty()) {
            System.out.println("\nYou have no pending tasks. Enjoy your free time!");
            return;
        }
        System.out.println("\n== DO THESE NEXT ==");
        int rank = 1;
        for (Task t : top) {
            System.out.printf("%d. [%s] %s  (due %s, %s)%n", rank++, t.getPriority(), t.getTitle(),
                    t.getDeadline(), describeDays(t));
        }
    }

    private static String describeDays(Task t) {
        long d = t.daysLeft();
        if (d < 0) return Math.abs(d) + " day(s) overdue";
        if (d == 0) return "due today";
        if (d == 1) return "due tomorrow";
        return d + " days left";
    }

    private static void printTable(List<Task> list, String title) {
        System.out.println("\n== " + title + " ==");
        if (list.isEmpty()) {
            System.out.println("No tasks found.");
            return;
        }
        String fmt = "%-4s %-30s %-9s %-12s %-10s %-10s%n";
        System.out.printf(fmt, "ID", "TITLE", "PRIORITY", "DEADLINE", "DAYS LEFT", "STATUS");
        System.out.println(repeat('-', 80));
        for (Task t : list) {
            System.out.printf(fmt, t.getId(), trim(t.getTitle(), 30), t.getPriority(), t.getDeadline(),
                    t.isDone() ? "-" : String.valueOf(t.daysLeft()), t.statusLabel());
        }
    }

    private static void completeTask(TaskManager m) {
        int id = readInt("Task ID to complete: ");
        Task t = m.findById(id);
        if (t == null) {
            System.out.println("Task not found.");
        } else if (t.isDone()) {
            System.out.println("That task is already completed.");
        } else {
            t.complete();
            m.saveChanges();
            System.out.println("Well done! Task #" + id + " marked as completed.");
        }
    }

    private static void editTask(TaskManager m) {
        int id = readInt("Task ID to edit: ");
        Task t = m.findById(id);
        if (t == null) {
            System.out.println("Task not found.");
            return;
        }
        if (t.isDone()) {
            System.out.println("Completed tasks cannot be edited.");
            return;
        }
        System.out.println("1. Change title  2. Change priority  3. Change deadline");
        int option = readInt("Choose what to edit: ");
        switch (option) {
            case 1: t.setTitle(readNonEmpty("New title: ")); break;
            case 2: t.setPriority(readPriority()); break;
            case 3: t.setDeadline(readDate("New deadline (YYYY-MM-DD, 'today', 'tomorrow', '+N'): ")); break;
            default:
                System.out.println("Invalid option.");
                return;
        }
        m.saveChanges();
        System.out.println("Task updated.");
    }

    private static void deleteTask(TaskManager m) {
        int id = readInt("Task ID to delete: ");
        System.out.println(m.delete(id) ? "Task deleted." : "Task not found.");
    }

    private static void searchTasks(TaskManager m) {
        String keyword = readNonEmpty("Search keyword: ");
        printTable(m.search(keyword), "SEARCH RESULTS");
    }

    private static void showDashboard(TaskManager m) {
        long pending = m.countPending();
        long completed = m.countCompleted();
        long total = pending + completed;
        System.out.println("\n== DASHBOARD ==");
        System.out.println("Pending tasks    : " + pending);
        System.out.println("Completed tasks  : " + completed);
        System.out.println("Completion rate  : " + (total == 0 ? "N/A" : (completed * 100 / total) + "%"));
        System.out.println("Overdue          : " + m.countOverdue());
        System.out.println("Due today        : " + m.countDueToday());
        System.out.println("Due in 7 days    : " + m.countDueWithin(7));
        System.out.println("Pending by priority -> HIGH: " + m.countByPriority(Priority.HIGH)
                + ", MEDIUM: " + m.countByPriority(Priority.MEDIUM)
                + ", LOW: " + m.countByPriority(Priority.LOW));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }

    private static String repeat(char c, int n) {
        return new String(new char[n]).replace('\0', c);
    }

    private static String readNonEmpty(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            if (!s.isEmpty()) return s;
            System.out.println("Input cannot be empty.");
        }
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private static Priority readPriority() {
        while (true) {
            int n = readInt("Priority (1 = HIGH, 2 = MEDIUM, 3 = LOW): ");
            if (n == 1) return Priority.HIGH;
            if (n == 2) return Priority.MEDIUM;
            if (n == 3) return Priority.LOW;
            System.out.println("Please enter 1, 2 or 3.");
        }
    }

    private static LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim().toLowerCase();
            try {
                if (s.equals("today")) return LocalDate.now();
                if (s.equals("tomorrow")) return LocalDate.now().plusDays(1);
                if (s.startsWith("+")) return LocalDate.now().plusDays(Long.parseLong(s.substring(1)));
                return LocalDate.parse(s);
            } catch (DateTimeParseException | NumberFormatException e) {
                System.out.println("Invalid date. Try 2026-12-31, today, tomorrow or +5.");
            }
        }
    }
}
