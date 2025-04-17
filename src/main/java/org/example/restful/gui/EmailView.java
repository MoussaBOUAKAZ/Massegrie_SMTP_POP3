package org.example.restful.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.springframework.web.client.RestTemplate;



class EmailView {
    private JFrame frame;
    private JTextField recipientField;
    private JTextField subjectField;
    private JTextArea contentArea;
    private JLabel statusLabel;

    public EmailView() {
        initializeGUI();
    }

    private void initializeGUI() {
        frame = new JFrame("Gestion des Emails");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout(10, 10));

        // Form panel
        JPanel formPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel recipientLabel = new JLabel("Destinataire:");
        recipientField = new JTextField();
        JLabel subjectLabel = new JLabel("Sujet:");
        subjectField = new JTextField();
        JLabel contentLabel = new JLabel("Contenu:");
        contentArea = new JTextArea(5, 20);

        formPanel.add(recipientLabel);
        formPanel.add(recipientField);
        formPanel.add(subjectLabel);
        formPanel.add(subjectField);
        formPanel.add(contentLabel);
        formPanel.add(new JScrollPane(contentArea));

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton sendButton = new JButton("Envoyer");
        buttonPanel.add(sendButton);

        // Status label
        statusLabel = new JLabel(" ");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);

        frame.add(formPanel, BorderLayout.CENTER);
        frame.add(buttonPanel, BorderLayout.SOUTH);
        frame.add(statusLabel, BorderLayout.NORTH);

        // Send button action
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleSendEmail();
            }
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void handleSendEmail() {
        String recipient = recipientField.getText();
        String subject = subjectField.getText();
        String content = contentArea.getText();

        // Call REST API
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:8080/api/emails/send?recipient=" + recipient + "&subject=" + subject + "&content=" + content;

        try {
            Boolean response = restTemplate.postForObject(url, null, Boolean.class);
            if (response != null && response) {
                statusLabel.setText("Email envoyé avec succès !");
                statusLabel.setForeground(Color.GREEN.darker());
            } else {
                statusLabel.setText("Échec de l'envoi de l'email.");
            }
        } catch (Exception ex) {
            statusLabel.setText("Erreur : " + ex.getMessage());
        }
    }
}
