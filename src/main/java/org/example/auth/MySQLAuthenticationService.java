package org.example.auth;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MySQLAuthenticationService extends UnicastRemoteObject implements org.example.auth.AuthenticationService {
    private Connection connection;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/bdd-server";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = ""; // Change this to your actual password

    public MySQLAuthenticationService() throws RemoteException {
        super();
        try {
            // Load the MySQL JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // Initialize database connection
            initializeDatabase();
        } catch (ClassNotFoundException e) {
            throw new RemoteException("MySQL JDBC Driver not found", e);
        } catch (SQLException e) {
            throw new RemoteException("Failed to initialize database", e);
        }
    }

    @Override
    public boolean initializeDatabase() throws RemoteException, SQLException {
        try {
            // Establish connection to MySQL
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            
            // Create users table if it doesn't exist
            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) UNIQUE NOT NULL, " +
                    "password_hash VARCHAR(64) NOT NULL, " +
                    "password_clear VARCHAR(50) NOT NULL, " + // Not recommended for production
                    "status VARCHAR(20) DEFAULT 'active', " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createUsersTable);
            }

            // Create emails table if it doesn't exist
            String createEmailsTable = "CREATE TABLE IF NOT EXISTS emails (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "sender VARCHAR(50) NOT NULL, " +
                    "recipient VARCHAR(50) NOT NULL, " +
                    "subject VARCHAR(100), " +
                    "content TEXT, " +
                    "date VARCHAR(50), " +
                    "sender_id INT, " +
                    "recipient_id INT, " +
                    "is_deleted BOOLEAN DEFAULT FALSE, " +
                    "FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE)";
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createEmailsTable);
            }
            
            return true;
        } catch (SQLException e) {
            throw new RemoteException("Database initialization failed", e);
        }
    }

    @Override
    public boolean verifyUser(String username) throws RemoteException {
        try {
            String query = "SELECT COUNT(*) FROM users WHERE username = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, username);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new RemoteException("Database error while verifying user", e);
        }
    }

    @Override
    public boolean verifyPass(String username, String password) throws RemoteException {
        try {
            String query = "SELECT password_clear FROM users WHERE username = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, username);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String storedPassword = rs.getString("password_clear");
                        return storedPassword.equals(password);
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new RemoteException("Database error while verifying password", e);
        }
    }

    @Override
    public boolean verifyCredentials(String username, String password) throws RemoteException {
        try {
            String query = "SELECT password_hash FROM users WHERE username = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, username);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String storedHash = rs.getString("password_hash");
                        String inputHash = hashPassword(password);
                        return storedHash.equals(inputHash);
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new RemoteException("Database error while verifying credentials", e);
        }
    }

    @Override
    public boolean createAccount(String username, String password) throws RemoteException {
        try {
            if (verifyUser(username)) {
                return false; // User already exists
            }
            
            String query = "INSERT INTO users (username, password_hash, password_clear) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, username);
                pstmt.setString(2, hashPassword(password));
                pstmt.setString(3, password); // Not recommended for production
                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while creating account", e);
        }
    }

    @Override
    public boolean updateAccount(String username, String newPassword) throws RemoteException {
        try {
            String query = "UPDATE users SET password_hash = ?, password_clear = ? WHERE username = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, hashPassword(newPassword));
                pstmt.setString(2, newPassword); // Not recommended for production
                pstmt.setString(3, username);
                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while updating account", e);
        }
    }

    @Override
    public boolean deleteAccount(String username) throws RemoteException {
        try {
            String query = "DELETE FROM users WHERE username = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, username);
                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while deleting account", e);
        }
    }

    @Override
    public Map<String, Object> getUserDetails(String username) throws RemoteException {
        try {
            String query = "SELECT * FROM users WHERE username = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, username);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> userDetails = new HashMap<>();
                        userDetails.put("id", rs.getInt("id"));
                        userDetails.put("username", rs.getString("username"));
                        userDetails.put("status", rs.getString("status"));
                        userDetails.put("created_at", rs.getTimestamp("created_at"));
                        return userDetails;
                    }
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RemoteException("Database error while retrieving user details", e);
        }
    }

    @Override
    public boolean changeUserStatus(String username, String status) throws RemoteException {
        try {
            String query = "UPDATE users SET status = ? WHERE username = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, status);
                pstmt.setString(2, username);
                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while changing user status", e);
        }
    }

    @Override
    public void closeConnection() throws RemoteException {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RemoteException("Error closing database connection", e);
        }
    }

    @Override
    public boolean storeEmail(String sender, String recipient, String subject, String content, String date) throws RemoteException, SQLException {
        try {
            String query = "INSERT INTO emails (sender, recipient, subject, content, date) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, sender);
                pstmt.setString(2, recipient);
                pstmt.setString(3, subject);
                pstmt.setString(4, content);
                pstmt.setString(5, date);
                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while storing email", e);
        }
    }

    @Override
    public List<Map<String, Object>> getEmailsForRecipient(String recipient) throws RemoteException, SQLException {
        try {
            String query = "SELECT * FROM emails WHERE recipient = ? AND is_deleted = FALSE";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, recipient);
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<Map<String, Object>> emails = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> email = new HashMap<>();
                        email.put("id", rs.getInt("id"));
                        email.put("sender", rs.getString("sender"));
                        email.put("recipient", rs.getString("recipient"));
                        email.put("subject", rs.getString("subject"));
                        email.put("content", rs.getString("content"));
                        email.put("date", rs.getString("date"));
                        emails.add(email);
                    }
                    return emails;
                }
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while retrieving emails for recipient", e);
        }
    }

    @Override
    public List<Map<String, Object>> getMessageIdsAndLengths(String recipient) throws RemoteException, SQLException {
        try {
            String query = "SELECT id, LENGTH(content) AS length FROM emails WHERE recipient = ? AND is_deleted = FALSE";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, recipient);
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<Map<String, Object>> messages = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> message = new HashMap<>();
                        message.put("id", rs.getInt("id"));
                        message.put("length", rs.getInt("length"));
                        messages.add(message);
                    }
                    return messages;
                }
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while retrieving message IDs and lengths", e);
        }
    }

    @Override
    public Map<String, Integer> getStatisticsForRecipient(String recipient) throws RemoteException, SQLException {
        try {
            String query = "SELECT COUNT(*) AS email_count, SUM(LENGTH(content)) AS total_size FROM emails WHERE recipient = ? AND is_deleted = FALSE";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, recipient);
                try (ResultSet rs = pstmt.executeQuery()) {
                    Map<String, Integer> stats = new HashMap<>();
                    if (rs.next()) {
                        stats.put("email_count", rs.getInt("email_count"));
                        stats.put("total_size", rs.getInt("total_size"));
                    }
                    return stats;
                }
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while retrieving statistics for recipient", e);
        }
    }

    @Override
    public Map<String, Integer> getStatisticsForRecipientExcludingDeleted(String recipient) throws RemoteException, SQLException {
        try {
            String query = "SELECT COUNT(*) AS email_count, SUM(LENGTH(content)) AS total_size FROM emails WHERE recipient = ? AND is_deleted = FALSE";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, recipient);
                try (ResultSet rs = pstmt.executeQuery()) {
                    Map<String, Integer> stats = new HashMap<>();
                    if (rs.next()) {
                        stats.put("email_count", rs.getInt("email_count"));
                        stats.put("total_size", rs.getInt("total_size"));
                    }
                    return stats;
                }
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while retrieving statistics excluding deleted emails", e);
        }
    }

    @Override
    public List<Map<String, Object>> getMessageIdsAndLengthsExcludingDeleted(String recipient) throws RemoteException, SQLException {
        try {
            String query = "SELECT id, LENGTH(content) AS length FROM emails WHERE recipient = ? AND is_deleted = FALSE";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, recipient);
                try (ResultSet rs = pstmt.executeQuery()) {
                    List<Map<String, Object>> messages = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> message = new HashMap<>();
                        message.put("id", rs.getInt("id"));
                        message.put("length", rs.getInt("length"));
                        messages.add(message);
                    }
                    return messages;
                }
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while retrieving message IDs and lengths excluding deleted emails", e);
        }
    }

    @Override
    public boolean deleteEmail(int emailId) throws RemoteException, SQLException {
        try {
            String query = "DELETE FROM emails WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setInt(1, emailId);
                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while deleting email", e);
        }
    }

    @Override
    public Map<String, Object> getEmailById(int emailId) throws RemoteException, SQLException {
        try {
            String query = "SELECT * FROM emails WHERE id = ? AND is_deleted = FALSE";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setInt(1, emailId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> email = new HashMap<>();
                        email.put("id", rs.getInt("id"));
                        email.put("sender", rs.getString("sender"));
                        email.put("recipient", rs.getString("recipient"));
                        email.put("subject", rs.getString("subject"));
                        email.put("content", rs.getString("content"));
                        email.put("date", rs.getString("date"));
                        return email;
                    }
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RemoteException("Database error while retrieving email by ID", e);
        }
    }

    @Override
    public Map<String, Object> getEmailContentById(int emailId) throws RemoteException, SQLException {
        try {
            String query = "SELECT * FROM emails WHERE id = ? AND is_deleted = FALSE";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setInt(1, emailId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> email = new HashMap<>();
                        email.put("id", rs.getInt("id"));
                        email.put("sender", rs.getString("sender"));
                        email.put("recipient", rs.getString("recipient"));
                        email.put("subject", rs.getString("subject"));
                        email.put("content", rs.getString("content"));
                        email.put("date", rs.getString("date"));
                        return email;
                    }
                }
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while retrieving email content by ID", e);
        }
        return null; // Return null if no email is found
    }

    @Override
    public boolean markEmailAsDeleted(int emailId) throws RemoteException, SQLException {
        try {
            String query = "UPDATE emails SET is_deleted = TRUE WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setInt(1, emailId);
                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while marking email as deleted", e);
        }
    }

    @Override
    public boolean unmarkAllDeletedEmails(String recipient) throws RemoteException, SQLException {
        try {
            String query = "UPDATE emails SET is_deleted = FALSE WHERE recipient = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, recipient);
                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0; // Return true if any rows were updated
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while unmarking deleted emails", e);
        }
    }

    @Override
    public boolean deleteMarkedEmails(String recipient) throws RemoteException, SQLException {
        try {
            String query = "DELETE FROM emails WHERE recipient = ? AND is_deleted = TRUE";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, recipient);
                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected > 0; // Return true if any rows were deleted
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while deleting marked emails", e);
        }
    }

    @Override
    public List<Map<String, Object>> getSentEmails(String sender) throws RemoteException, SQLException {
        List<Map<String, Object>> emails = new ArrayList<>();
        String query = "SELECT * FROM emails WHERE sender = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, sender);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Map<String, Object> email = new HashMap<>();
                email.put("id", resultSet.getInt("id"));
                email.put("sender", resultSet.getString("sender"));
                email.put("recipient", resultSet.getString("recipient"));
                email.put("subject", resultSet.getString("subject"));
                email.put("body", resultSet.getString("body"));
                email.put("date", resultSet.getString("date"));
                emails.add(email);
            }
        }
        return emails;
    }

    // Helper method to hash passwords
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to plain text if hashing fails
            return password;
        }
    }

    /**
     * Tests the database connection
     * @return true if connection is valid, false otherwise
     * @throws RemoteException if a remote error occurs
     */
    public boolean testConnection() throws RemoteException {
        try {
            if (connection == null || connection.isClosed()) {
                // Try to reconnect if connection is closed or null
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            }
            
            // Check if connection is valid with a 5 second timeout
            return connection.isValid(5);
        } catch (SQLException e) {
            throw new RemoteException("Database connection test failed", e);
        }
    }
}