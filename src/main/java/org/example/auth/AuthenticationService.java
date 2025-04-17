package org.example.auth;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface AuthenticationService extends Remote {
    boolean verifyUser(String username) throws RemoteException;
    public boolean verifyPass(String username, String password) throws RemoteException ;
    boolean verifyCredentials(String username, String password) throws RemoteException;
    boolean createAccount(String username, String password) throws RemoteException;
    boolean updateAccount(String username, String newPassword) throws RemoteException;
    boolean deleteAccount(String username) throws RemoteException;
    
    // New methods for database operations
    boolean initializeDatabase() throws RemoteException, SQLException;
    Map<String, Object> getUserDetails(String username) throws RemoteException;
    boolean changeUserStatus(String username, String status) throws RemoteException;
    void closeConnection() throws RemoteException;

    // Methods for SMTP and POP3 operations
    boolean storeEmail(String sender, String recipient, String subject, String content, String date) throws RemoteException, SQLException;
    List<Map<String, Object>> getEmailsForRecipient(String recipient) throws RemoteException, SQLException;
    boolean deleteEmail(int emailId) throws RemoteException, SQLException;

    // New method to get email by ID
    Map<String, Object> getEmailById(int emailId) throws RemoteException, SQLException;
    Map<String, Object> getEmailContentById(int emailId) throws RemoteException, SQLException;

    boolean markEmailAsDeleted(int emailId) throws RemoteException, SQLException;
    boolean unmarkAllDeletedEmails(String recipient) throws RemoteException, SQLException;
    boolean deleteMarkedEmails(String recipient) throws RemoteException, SQLException;

    Map<String, Integer> getStatisticsForRecipient(String recipient) throws RemoteException, SQLException;
    List<Map<String, Object>> getMessageIdsAndLengths(String recipient) throws RemoteException, SQLException ;
    Map<String, Integer> getStatisticsForRecipientExcludingDeleted(String recipient) throws RemoteException, SQLException;
    List<Map<String, Object>> getMessageIdsAndLengthsExcludingDeleted(String recipient) throws RemoteException, SQLException;

    List<Map<String, Object>> getSentEmails(String sender) throws RemoteException, SQLException;
}
