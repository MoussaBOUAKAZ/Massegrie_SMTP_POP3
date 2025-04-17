package org.example.restful.services;

import org.example.auth.AuthenticationService;

import java.rmi.Naming;

public class RMIClientService {
    private AuthenticationService authService;

    public RMIClientService() {
        try {
            authService = (AuthenticationService) Naming.lookup("rmi://localhost/AuthenticationService");
        } catch (Exception e) {
            throw new RuntimeException("Erreur de connexion au serveur RMI : " + e.getMessage(), e);
        }
    }

    public AuthenticationService getAuthService() {
        return authService;
    }
}
