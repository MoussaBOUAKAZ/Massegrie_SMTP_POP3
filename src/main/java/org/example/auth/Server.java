package org.example.auth;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class Server {
    public static void main(String[] args) {
        try {
            LocateRegistry.createRegistry(1099); // Démarrer le registre RMI sur le port 1099
            AuthenticationService authService = new MySQLAuthenticationService();
            Naming.rebind("rmi://localhost/AuthenticationService", authService);
            System.out.println("Serveur RMI démarré et prêt.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
