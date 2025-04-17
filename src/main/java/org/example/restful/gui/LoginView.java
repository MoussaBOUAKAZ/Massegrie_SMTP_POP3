package org.example.restful.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.springframework.web.client.RestTemplate;

public class LoginView {
    private JFrame frame;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JLabel statusLabel;

    public LoginView() {
        initializeGUI();
    }

    private void initializeGUI() {
        frame = new JFrame("Login");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);
        frame.setLayout(new BorderLayout(10, 10));

        // Form panel
        JPanel formPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel usernameLabel = new JLabel("Nom d'utilisateur:");
        usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("Mot de passe:");
        passwordField = new JPasswordField();
        JButton loginButton = new JButton("Se connecter");

        formPanel.add(usernameLabel);
        formPanel.add(usernameField);
        formPanel.add(passwordLabel);
        formPanel.add(passwordField);
        formPanel.add(new JLabel()); // Empty cell
        formPanel.add(loginButton);

        // Status label
        statusLabel = new JLabel(" ");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);

        frame.add(formPanel, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);

        // Login button action
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleLogin();
            }
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void handleLogin() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());

        // Call REST API
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:8080/restful/controllers/AuthController/login?username=" + username + "&password=" + password;

        try {
            Boolean response = restTemplate.postForObject(url, null, Boolean.class);
            if (response != null && response) {
                statusLabel.setText("Connexion réussie !");
                statusLabel.setForeground(Color.GREEN.darker());
                frame.dispose();
                new EmailView(); // Open EmailView on successful login
            } else {
                statusLabel.setText("Identifiants invalides.");
            }
        } catch (Exception ex) {
            statusLabel.setText("Erreur : " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginView::new);
    }
}