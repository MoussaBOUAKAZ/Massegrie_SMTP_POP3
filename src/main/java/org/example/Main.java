package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/your_database";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    public static void main(String[] args) {
        try {
            // Step 1: Insert 10,000 emails into the database
            insertEmails(10000);

            // Step 2: Simulate 50 simultaneous connections for POP3 operations
            simulatePop3Connections(50);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void insertEmails(int emailCount) throws SQLException {
        System.out.println("Inserting " + emailCount + " emails into the database...");
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String insertQuery = "INSERT INTO emails (sender, recipient, subject, body) VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(insertQuery)) {
                for (int i = 1; i <= emailCount; i++) {
                    statement.setString(1, "sender" + i + "@example.com");
                    statement.setString(2, "recipient" + i + "@example.com");
                    statement.setString(3, "Subject " + i);
                    statement.setString(4, "This is the body of email " + i);
                    statement.addBatch();

                    if (i % 1000 == 0) {
                        statement.executeBatch();
                        System.out.println(i + " emails inserted...");
                    }
                }
                statement.executeBatch();
            }
        }
        System.out.println("Email insertion completed.");
    }

    private static void simulatePop3Connections(int connectionCount) {
        System.out.println("Simulating " + connectionCount + " simultaneous POP3 connections...");
        ExecutorService executor = Executors.newFixedThreadPool(connectionCount);

        for (int i = 0; i < connectionCount; i++) {
            executor.submit(() -> {
                try {
                    long startTime = System.currentTimeMillis();

                    // Simulate STAT command
                    simulateStatCommand();

                    // Simulate RETR command for the first 100 emails
                    for (int j = 1; j <= 100; j++) {
                        simulateRetrCommand(j);
                    }

                    long endTime = System.currentTimeMillis();
                    System.out.println("Thread completed in " + (endTime - startTime) + " ms");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            // Wait for all threads to finish
        }
        System.out.println("All POP3 connections completed.");
    }

    private static void simulateStatCommand() {
        // Simulate the STAT command (e.g., count emails)
        try {
            Thread.sleep(50); // Simulate network/database latency
            System.out.println("STAT command executed.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void simulateRetrCommand(int emailId) {
        // Simulate the RETR command (e.g., retrieve email content)
        try {
            Thread.sleep(100); // Simulate network/database latency
            System.out.println("RETR command executed for email ID: " + emailId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
