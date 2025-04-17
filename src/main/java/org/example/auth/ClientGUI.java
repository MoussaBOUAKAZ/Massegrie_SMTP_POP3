package org.example.auth;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.rmi.Naming;
import java.util.Map;

public class ClientGUI {
    private AuthenticationService authService;
    private JLabel connectionStatusLabel;

    public ClientGUI() {
        try {
            authService = (AuthenticationService) Naming.lookup("rmi://localhost/AuthenticationService");
            initializeGUI(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Erreur de connexion au serveur RMI : " + e.getMessage());
            initializeGUI(false);
        }
    }

    private void initializeGUI(boolean connected) {
        JFrame frame = new JFrame("Gestion des Comptes Utilisateurs");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout(10, 10));

        // Status panel at the top
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        connectionStatusLabel = new JLabel(connected ? "Connecté au serveur" : "Non connecté");
        connectionStatusLabel.setForeground(connected ? Color.GREEN.darker() : Color.RED);
        statusPanel.add(connectionStatusLabel);
        frame.add(statusPanel, BorderLayout.NORTH);

        // Main panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        frame.add(mainPanel, BorderLayout.CENTER);

        // Form panel
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new GridLayout(4, 2, 5, 5));
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel usernameLabel = new JLabel("Nom d'utilisateur:");
        JTextField usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("Mot de passe:");
        JPasswordField passwordField = new JPasswordField();
        JLabel statusLabel = new JLabel("Statut:");
        String[] statusOptions = {"active", "inactive", "suspended"};
        JComboBox<String> statusComboBox = new JComboBox<>(statusOptions);

        formPanel.add(usernameLabel);
        formPanel.add(usernameField);
        formPanel.add(passwordLabel);
        formPanel.add(passwordField);
        formPanel.add(statusLabel);
        formPanel.add(statusComboBox);

        // Button panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(2, 3, 5, 5));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton createButton = new JButton("Créer un compte");
        JButton updateButton = new JButton("Mettre à jour");
        JButton deleteButton = new JButton("Supprimer");
        JButton verifyButton = new JButton("Vérifier identifiants");
        JButton getUserButton = new JButton("Détails utilisateur");
        JButton clearButton = new JButton("Effacer");

        buttonPanel.add(createButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(verifyButton);
        buttonPanel.add(getUserButton);
        buttonPanel.add(clearButton);

        // Result area
        JTextArea resultArea = new JTextArea();
        resultArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(resultArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Résultat"));

        // Add components to main panel
        mainPanel.add(formPanel, BorderLayout.NORTH);
        mainPanel.add(buttonPanel, BorderLayout.CENTER);
        mainPanel.add(scrollPane, BorderLayout.SOUTH);

        // Disable all buttons if not connected
        if (!connected) {
            createButton.setEnabled(false);
            updateButton.setEnabled(false);
            deleteButton.setEnabled(false);
            verifyButton.setEnabled(false);
            getUserButton.setEnabled(false);
        }

        // Action listeners
        createButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    boolean success = authService.createAccount(usernameField.getText(), new String(passwordField.getPassword()));
                    resultArea.setText(success ? "Compte créé avec succès." : "Le compte existe déjà.");
                    if (success) {
                        authService.changeUserStatus(usernameField.getText(), statusComboBox.getSelectedItem().toString());
                    }
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
                    if (success) {
                        try {
                            boolean statusChanged = authService.changeUserStatus(usernameField.getText(), statusComboBox.getSelectedItem().toString());
                            if (statusChanged) {
                                resultArea.setText("Compte mis à jour avec succès.");
                            } else {
                                resultArea.setText("Compte mis à jour mais échec de la mise à jour du statut.");
                            }
                        } catch (Exception statusEx) {
                            resultArea.setText("Compte mis à jour mais erreur lors de la mise à jour du statut: " + statusEx.getMessage());
                        }
                    } else {
                        resultArea.setText("Le compte n'existe pas.");
                    }
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

        getUserButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    Map<String, Object> userDetails = authService.getUserDetails(usernameField.getText());
                    if (userDetails != null) {
                        StringBuilder details = new StringBuilder("Détails de l'utilisateur:\n");
                        for (Map.Entry<String, Object> entry : userDetails.entrySet()) {
                            details.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                        }
                        resultArea.setText(details.toString());
                        
                        // Update status combobox
                        if (userDetails.containsKey("status")) {
                            String status = userDetails.get("status").toString();
                            for (int i = 0; i < statusComboBox.getItemCount(); i++) {
                                if (statusComboBox.getItemAt(i).equals(status)) {
                                    statusComboBox.setSelectedIndex(i);
                                    break;
                                }
                            }
                        }
                    } else {
                        resultArea.setText("Utilisateur non trouvé.");
                    }
                } catch (Exception ex) {
                    resultArea.setText("Erreur : " + ex.getMessage());
                }
            }
        });

        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                usernameField.setText("");
                passwordField.setText("");
                statusComboBox.setSelectedIndex(0);
                resultArea.setText("");
            }
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}
