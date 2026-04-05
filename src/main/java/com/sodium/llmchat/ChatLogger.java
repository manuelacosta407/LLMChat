package com.sodium.llmchat;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ChatLogger {

    private final JavaPlugin plugin;
    private final File logFolder;
    private final ExecutorService executor;
    private final DateTimeFormatter timeFormatter;
    private final DateTimeFormatter dateFormatter;
    private final AtomicBoolean shutdown;
    private final AtomicReference<LocalDate> currentDateRef;
    private volatile PrintWriter currentWriter;
    private final Object writeLock;

    public ChatLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logFolder = new File(plugin.getDataFolder(), "logs");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LLMChat-Logger");
            t.setDaemon(true);
            return t;
        });
        this.timeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd-HH:mm:ss");
        this.dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        this.shutdown = new AtomicBoolean(false);
        this.writeLock = new Object();
        this.currentDateRef = new AtomicReference<>(null);
        this.currentWriter = null;

        if (!logFolder.exists()) {
            if (!logFolder.mkdirs()) {
                plugin.getLogger().warning("Failed to create log folder: " + logFolder.getAbsolutePath());
            }
        }
    }

    public void log(String playerName, String command) {
        if (shutdown.get()) {
            return;
        }

        executor.submit(() -> writeLog(playerName, command));
    }

    private void writeLog(String playerName, String command) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDate today = now.toLocalDate();
            String timeStr = now.format(timeFormatter);

            String logLine = String.format("[%s] {%s} %s", timeStr, playerName, command);

            synchronized (writeLock) {
                if (shutdown.get()) {
                    return;
                }
                
                LocalDate cachedDate = currentDateRef.get();
                if (cachedDate == null || !cachedDate.equals(today)) {
                    checkDateRoll(today);
                }
                
                if (currentWriter != null) {
                    currentWriter.println(logLine);
                    currentWriter.flush();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to write chat log: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    private void checkDateRoll(LocalDate today) throws IOException {
        closeCurrentWriter();
        currentDateRef.set(today);
        String fileName = "chat-" + today.format(dateFormatter) + ".log";
        File logFile = new File(logFolder, fileName);
        try {
            FileOutputStream fos = new FileOutputStream(logFile, true);
            OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            currentWriter = new PrintWriter(new BufferedWriter(osw, 8192));
        } catch (FileNotFoundException e) {
            plugin.getLogger().warning("Failed to create log file: " + logFile.getAbsolutePath() + " - " + e.getMessage());
            throw e;
        }
    }

    private void closeCurrentWriter() {
        if (currentWriter != null) {
            try {
                currentWriter.flush();
                currentWriter.close();
            } catch (Exception e) {
                plugin.getLogger().warning("Error closing log file: " + e.getMessage());
            }
            currentWriter = null;
        }
    }

    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("ChatLogger executor did not terminate gracefully");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        synchronized (writeLock) {
            closeCurrentWriter();
        }
    }
}
