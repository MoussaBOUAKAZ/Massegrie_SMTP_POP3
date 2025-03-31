package org.example.auth;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AuthenticationService extends Remote {
    boolean verifyUser(String username) throws RemoteException;
    boolean verifyPass(String password) throws RemoteException;
    boolean verifyCredentials(String username, String password) throws RemoteException;
    boolean createAccount(String username, String password) throws RemoteException;
    boolean updateAccount(String username, String newPassword) throws RemoteException;
    boolean deleteAccount(String username) throws RemoteException;
}
