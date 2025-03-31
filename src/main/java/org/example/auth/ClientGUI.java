package org.example.auth;

import org.example.auth.AuthenticationService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.rmi.Naming;

public class ClientGUI {
    private AuthenticationService authService;

    public ClientGUI() {
        try {
            authService = (AuthenticationService) Naming.lookup("rmi://localhost/AuthenticationService");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Erreur de connexion au serveur RMI : " + e.getMessage());
            System.exit(1);
        }

        JFrame frame = new JFrame("Gestion des Comptes Utilisateurs");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(6, 2));

        JLabel usernameLabel = new JLabel("Nom d'utilisateur:");
        JTextField usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("Mot de passe:");
        JPasswordField passwordField = new JPasswordField();

        JButton createButton = new JButton("Créer un compte");
        JButton updateButton = new JButton("Mettre à jour le mot de passe");
        JButton deleteButton = new JButton("Supprimer un compte");
        JButton verifyButton = new JButton("Vérifier les identifiants");

        JTextArea resultArea = new JTextArea();
        resultArea.setEditable(false);

        createButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    boolean success = authService.createAccount(usernameField.getText(), new String(passwordField.getPassword()));
                    resultArea.setText(success ? "Compte créé avec succès." : "Le compte existe déjà.");
                } catch (Exception ex) {
                    resultArea.setText("Erreur : " + ex.getMessage());
                }
            }
        });

        updateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    boolean success = authService.updateAccount(usernameField.getText(), new String(passwordField.getPassword()));
                    resultArea.setText(success ? "Mot de passe mis à jour avec succès." : "Le compte n'existe pas.");
                } catch (Exception ex) {
                    resultArea.setText("Erreur : " + ex.getMessage());
                }
            }
        });

        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    boolean success = authService.deleteAccount(usernameField.getText());
                    resultArea.setText(success ? "Compte supprimé avec succès." : "Le compte n'existe pas.");
                } catch (Exception ex) {
                    resultArea.setText("Erreur : " + ex.getMessage());
                }
            }
        });

        verifyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    boolean success = authService.verifyCredentials(usernameField.getText(), new String(passwordField.getPassword()));
                    resultArea.setText(success ? "Identifiants valides." : "Identifiants invalides.");
                } catch (Exception ex) {
                    resultArea.setText("Erreur : " + ex.getMessage());
                }
            }
        });

        panel.add(usernameLabel);
        panel.add(usernameField);
        panel.add(passwordLabel);
        panel.add(passwordField);
        panel.add(createButton);
        panel.add(updateButton);
        panel.add(deleteButton);
        panel.add(verifyButton);
        panel.add(new JLabel("Résultat:"));
        panel.add(new JScrollPane(resultArea));

        frame.add(panel);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}
