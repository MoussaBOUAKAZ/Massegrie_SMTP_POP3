package org.example.restful.controllers;

import org.example.restful.services.RMIClientService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final RMIClientService rmiClientService;

    public AuthController() {
        this.rmiClientService = new RMIClientService();
    }

    @PostMapping("/login")
    public boolean login(@RequestParam String username, @RequestParam String password) {
        try {
            return rmiClientService.getAuthService().verifyCredentials(username, password);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la vérification des identifiants : " + e.getMessage(), e);
        }
    }

    @PostMapping("/register")
    public boolean register(@RequestParam String username, @RequestParam String password) {
        try {
            return rmiClientService.getAuthService().createAccount(username, password);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la création du compte : " + e.getMessage(), e);
        }
    }

    @PutMapping("/update")
    public boolean updatePassword(@RequestParam String username, @RequestParam String newPassword) {
        try {
            return rmiClientService.getAuthService().updateAccount(username, newPassword);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la mise à jour du mot de passe : " + e.getMessage(), e);
        }
    }

    @DeleteMapping("/delete")
    public boolean deleteAccount(@RequestParam String username) {
        try {
            return rmiClientService.getAuthService().deleteAccount(username);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la suppression du compte : " + e.getMessage(), e);
        }
    }
}
