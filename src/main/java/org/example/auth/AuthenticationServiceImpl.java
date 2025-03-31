package org.example.auth;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class AuthenticationServiceImpl extends UnicastRemoteObject implements AuthenticationService {
    private final Map<String, String> userDatabase;
    private static final String FILE_PATH = "resources/userDatabase.json";

    protected AuthenticationServiceImpl() throws RemoteException {
        super();
        userDatabase = new HashMap<>();
        loadUserDatabase();
    }

    private void loadUserDatabase() {
        try {
            if (Files.exists(Paths.get(FILE_PATH))) {
                FileReader reader = new FileReader(FILE_PATH);
                Map<String, String> data = new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
                if (data != null) {
                    userDatabase.putAll(data);
                }
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveUserDatabase() {
        try {
            FileWriter writer = new FileWriter(FILE_PATH);
            new Gson().toJson(userDatabase, writer);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean verifyCredentials(String username, String password) throws RemoteException {
        return userDatabase.containsKey(username) && userDatabase.get(username).equals(password);
    }

    @Override
    public boolean createAccount(String username, String password) throws RemoteException {
        if (userDatabase.containsKey(username)) {
            return false; // L'utilisateur existe déjà
        }
        userDatabase.put(username, password);
        saveUserDatabase();
        return true;
    }

    @Override
    public boolean updateAccount(String username, String newPassword) throws RemoteException {
        if (!userDatabase.containsKey(username)) {
            return false; // L'utilisateur n'existe pas
        }
        userDatabase.put(username, newPassword);
        saveUserDatabase();
        return true;
    }

    @Override
    public boolean deleteAccount(String username) throws RemoteException {
        boolean result = userDatabase.remove(username) != null;
        if (result) {
            saveUserDatabase();
        }
        return result;
    }

    @Override
    public boolean verifyUser(String username) throws RemoteException {
        return userDatabase.containsKey(username); // Check if the user exists
    }

    @Override
    public boolean verifyPass(String password) throws RemoteException {
        return userDatabase.containsValue(password); // Check if the password exists
    }
}