package org.example;

import org.example.auth.AuthenticationService;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.rmi.Naming;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

public class POP3Server {
    private static final int PORT = 110;
    private static final int MAX_THREADS = 10; // Maximum number of threads
    private static final int QUEUE_CAPACITY = 20; // Maximum number of queued connections

    public static void main(String[] args) {
        AuthenticationService authService;
        try {
            authService = (AuthenticationService) Naming.lookup("rmi://localhost/AuthenticationService");
        } catch (Exception e) {
            System.err.println("Erreur de connexion au serveur RMI : " + e.getMessage());
            return;
        }

        ExecutorService threadPool = new ThreadPoolExecutor(
                MAX_THREADS, MAX_THREADS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                new ThreadPoolExecutor.AbortPolicy() // Reject new tasks when the queue is full
        );

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur POP3 en écoute sur le port " + PORT);
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(new POP3Handler(clientSocket, authService)); // Pass authService
                } catch (RejectedExecutionException e) {
                    System.err.println("Connexion rejetée : le pool de threads est saturé.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }
}

class POP3Handler implements Runnable {
    private enum State { AUTHORIZATION, TRANSACTION, UPDATE }

    private final Socket clientSocket;
    private final AuthenticationService authService; // Add authService field
    private BufferedReader in;
    private PrintWriter out;
    private State state;
    private String user = null;
    private List<Path> emails = new ArrayList<>();
    private Set<Integer> markedForDeletion = new HashSet<>();
    private String serverGreeting;

    public POP3Handler(Socket socket, AuthenticationService authService) {
        this.clientSocket = socket;
        this.authService = authService; // Initialize authService
        this.state = State.AUTHORIZATION;
        this.serverGreeting = generateTimestamp();
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            out.println("+OK POP3 server ready " + serverGreeting);

            String command;
            while ((command = in.readLine()) != null) {
                handleCommand(command);
                if (state == State.UPDATE) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    private void handleCommand(String command) throws IOException {
        String[] parts = command.split(" ", 2);
        String cmd = parts[0].toUpperCase();
        String arg = parts.length > 1 ? parts[1] : "";

        switch (state) {
            case AUTHORIZATION:
                switch (cmd) {
                    case "USER":
                        handleUser(arg);
                        break;
                    case "PASS":
                        handlePass(arg);
                        break;
                    case "APOP":
                        handleApop(arg);
                        break;
                    case "QUIT":
                        handleQuit();
                        break;
                    default:
                        out.println("-ERR Commande non autorisée en état d'autorisation");
                }
                break;

            case TRANSACTION:
                switch (cmd) {
                    case "STAT":
                        handleStat();
                        break;
                    case "LIST":
                        handleList(arg);
                        break;
                    case "RETR":
                        handleRetr(arg);
                        break;
                    case "DELE":
                        handleDele(arg);
                        break;
                    case "NOOP":
                        out.println("+OK");
                        break;
                    case "RSET":
                        handleRset();
                        break;
                    case "UIDL":
                        handleUidl(arg);
                        break;
                    case "TOP":
                        handleTop(arg);
                        break;
                    case "QUIT":
                        handleQuit();
                        break;
                    default:
                        out.println("-ERR Commande inconnue");
                }
                break;

            default:
                out.println("-ERR État invalide");
        }
    }

    private void handleUser(String username) {
        if (this.user != null) {
            out.println("-ERR Commande USER deja recue");
            return;
        }
        try {
            if (authService.verifyUser(username)) { // Verify user existence
                this.user = username  ;
                out.println("+OK Utilisateur accepte");
            } else {
                out.println("-ERR Utilisateur inconnu");
            }
        } catch (Exception e) {
            out.println("-ERR Erreur de connexion au serveur RMI");
        }
    }

    private void handlePass(String password) {
        if (user != null) {
            try {
                if (authService.verifyPass(user, password)) { 
                    // loadEmails();
                    state = State.TRANSACTION;
                    out.println("+OK Authentification reussie");
                } else {
                    out.println("-ERR Mot de passe incorrect");
                }
            } catch (Exception e) {
                out.println("-ERR Erreur de connexion au serveur RMI");
            }
        } else {
            out.println("-ERR Utilisateur non defini");
        }
    }

    private void handleApop(String arg) {
        if (state != State.AUTHORIZATION) {
            out.println("-ERR APOP non autorise apres authentification");
            return;
        }
        String[] parts = arg.split(" ");
        if (parts.length != 2) {
            out.println("-ERR Syntaxe de APOP incorrecte");
            return;
        }
        String username = parts[0];
        String password = parts[1]; // Assume the second part is the password

        try {
            // Use verifyCredentials directly
            if (authService.verifyCredentials(username, password)) {
                this.user = username;
                // loadEmails();
                state = State.TRANSACTION;
                out.println("+OK APOP authentification reussie");
            } else {
                out.println("-ERR Authentification echouee");
            }
        } catch (Exception e) {
            out.println("-ERR Erreur de connexion au serveur RMI");
        }
    }

    public static String generateMD5Digest(byte[] input) {
        try {
            // Obtention d'une instance de l'algorithme MD5
            MessageDigest md = MessageDigest.getInstance("MD5");
            // Calcul du hachage
            byte[] digest = md.digest(input);
            // Conversion du tableau d'octets en une chaîne hexadécimale
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Cette exception ne devrait jamais se produire car MD5 est un algorithme valide
            throw new RuntimeException("Erreur lors de la génération du hachage MD5", e);
        }
    }

    private void loadEmails() {
        emails.clear();
        try {
            List<Map<String, Object>> emailRecords = authService.getEmailsForRecipient(user); // Fetch emails from the database
            for (Map<String, Object> emailRecord : emailRecords) {
                Path tempFile = Files.createTempFile("email_", ".txt");
                try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
                    writer.write("From: " + emailRecord.get("sender") + "\n");
                    writer.write("To: " + emailRecord.get("recipient") + "\n");
                    writer.write("Subject: " + emailRecord.get("subject") + "\n");
                    writer.write("\n");
                    writer.write((String) emailRecord.get("content"));
                }
                emails.add(tempFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.println("-ERR Erreur lors du chargement des emails");
        }
    }

    private void handleStat() {
        try {
            Map<String, Integer> stats = authService.getStatisticsForRecipientExcludingDeleted(user);
            int messageCount = stats.getOrDefault("email_count", 0);
            int totalSize = stats.getOrDefault("total_size", 0);

            out.println("+OK " + messageCount + " " + totalSize);
        } catch (Exception e) {
            out.println("-ERR Erreur lors de la récupération des statistiques");
        }
    }

    private void handleList(String arg) {
        try {
            List<Map<String, Object>> messages = authService.getMessageIdsAndLengthsExcludingDeleted(user);
            if (arg.isEmpty()) {
                out.println("+OK Liste des messages");
                for (int i = 0; i < messages.size(); i++) {
                    Map<String, Object> message = messages.get(i);
                    out.println((i + 1) + " " + message.get("length"));
                }
                out.println(".");
            } else {
                int index = Integer.parseInt(arg) - 1;
                if (index < 0 || index >= messages.size()) {
                    out.println("-ERR Aucun message avec ce numéro");
                    return;
                }
                Map<String, Object> message = messages.get(index);
                out.println("+OK " + (index + 1) + " " + message.get("length"));
            }
        } catch (Exception e) {
            out.println("-ERR Erreur lors de la récupération de la liste des messages");
        }
    }

    private void handleRetr(String arg) {
        try {
            int emailId = Integer.parseInt(arg); // Parse the email ID from the argument
            Map<String, Object> email = authService.getEmailContentById(emailId); // Fetch email content by ID

            if (email == null) {
                out.println("-ERR Aucun message avec ce numéro"); 
                return;
            }

            String content = (String) email.get("content");
            out.println("+OK " + content.length() + " octets");
            out.println(content); // Print the email content
            out.println(".");
        } catch (NumberFormatException e) {
            out.println("-ERR Argument invalide"); // Invalid argument format
        } catch (Exception e) {
            out.println("-ERR Erreur lors de la récupération du message"); // General error
        }
    }

    private void handleDele(String arg) {
        try {
            int emailId = Integer.parseInt(arg);
            if (authService.markEmailAsDeleted(emailId)) { // Mark email as deleted in the database
                out.println("+OK Message " + arg + " marqué pour suppression");
            } else {
                out.println("-ERR Aucun message avec ce numéro");
            }
        } catch (NumberFormatException e) {
            out.println("-ERR Argument invalide"); // Invalid argument format
        } catch (Exception e) {
            out.println("-ERR Erreur lors de la suppression du message"); // General error
        }
    }

    private void handleRset() {
        try {
            if (authService.unmarkAllDeletedEmails(user)) { // Unmark all deleted emails for the recipient
                out.println("+OK Toutes les suppressions ont été annulées");
            } else {
                out.println("+OK Aucune suppression à annuler");
            }
        } catch (Exception e) {
            out.println("-ERR Erreur lors de l'annulation des suppressions");
        }
    }

    private void handleUidl(String arg) {
        try {
            List<Map<String, Object>> emailRecords = authService.getEmailsForRecipient(user); // Fetch emails from the database
            if (arg.isEmpty()) {
                out.println("+OK Liste des identifiants uniques");
                for (int i = 0; i < emailRecords.size(); i++) {
                    Map<String, Object> emailRecord = emailRecords.get(i);
                    out.println((i + 1) + " " + emailRecord.get("id"));
                }
                out.println(".");
            } else {
                int index = Integer.parseInt(arg) - 1;
                if (index < 0 || index >= emailRecords.size()) {
                    out.println("-ERR Aucun message avec ce numéro");
                    return;
                }
                Map<String, Object> emailRecord = emailRecords.get(index);
                out.println("+OK " + (index + 1) + " " + emailRecord.get("id"));
            }
        } catch (Exception e) {
            out.println("-ERR Erreur lors de la récupération des identifiants uniques");
        }
    }

    private void handleTop(String arg) {
        String[] parts = arg.split(" ");
        if (parts.length != 2) {
            out.println("-ERR Syntaxe de la commande TOP incorrecte");
            return;
        }
        try {
            int index = Integer.parseInt(parts[0]) - 1;
            int lineCount = Integer.parseInt(parts[1]);
            if (index < 0 || index >= emails.size() || markedForDeletion.contains(index)) {
                out.println("-ERR Aucun message avec ce numéro");
                return;
            }
            out.println("+OK Début du message");
            List<String> lines = Files.readAllLines(emails.get(index));
            boolean inBody = false;
            int linesSent = 0;
            for (String line : lines) {
                if (line.isEmpty() && !inBody) {
                    inBody = true; // Start of the body
                }
                if (inBody) {
                    if (linesSent >= lineCount) {
                        break;
                    }
                    linesSent++;
                }
                out.println(line);
            }
            out.println(".");
        } catch (NumberFormatException | IOException e) {
            out.println("-ERR Erreur lors de la lecture du message");
        }
    }

    private void handleQuit() {
        if (state == State.AUTHORIZATION) {
            out.println("+OK Déconnexion en cours");
        } else {
            try {
                if (authService.deleteMarkedEmails(user)) { // Delete emails marked as deleted for the user
                    System.out.println("Messages marked as deleted have been removed for user: " + user);
                } else {
                    System.out.println("No messages to delete for user: " + user);
                }
            } catch (Exception e) {
                System.err.println("Error while applying deletions for user: " + user);
                e.printStackTrace();
            }            out.println("+OK Déconnexion en cours");
        }
        state = State.UPDATE;
    }

    
    private String generateUniqueId(Path email) {
        try {
            byte[] content = Files.readAllBytes(email);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            return email.getFileName().toString();
        }
    }

    private String generateTimestamp() {
        return "<" + System.currentTimeMillis() + "." + new Random().nextInt(1000) + "@example.com>";
    }

    private void closeConnection() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}