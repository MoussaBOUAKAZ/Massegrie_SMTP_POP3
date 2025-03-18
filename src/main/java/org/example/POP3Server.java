package org.example;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

public class POP3Server {
    private static final int PORT = 110;
    private static final int MAX_THREADS = 10; // Maximum number of threads
    private static final int QUEUE_CAPACITY = 20; // Maximum number of queued connections

    public static void main(String[] args) {
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
                    threadPool.execute(new POP3Handler(clientSocket));
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

    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private State state;
    private String user = null;
    private List<Path> emails = new ArrayList<>();
    private Set<Integer> markedForDeletion = new HashSet<>();
    private String serverGreeting;

    public POP3Handler(Socket socket) {
        this.clientSocket = socket;
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
        Path userDir = Paths.get("src/main/resources/mailserver", username+ "@eoc.dz");
        if (Files.exists(userDir)) {
            this.user = username;
            out.println("+OK Utilisateur accepte");
        } else {
            out.println("-ERR Utilisateur inconnu");
        }
    }

    private void handlePass(String password) {
        if (user != null) {
            if (verifyPassword(user, password)) {
                loadEmails();
                state = State.TRANSACTION;
                out.println("+OK Authentification reussie");
            } else {
                out.println("-ERR Mot de passe incorrect");
            }
        } else {
            out.println("-ERR Utilisateur non defini");
        }
    }

    private boolean verifyPassword(String username, String password) {
        Path credentialsFile = Paths.get("src/main/resources/mailserver/credentials.txt");
        try (BufferedReader reader = Files.newBufferedReader(credentialsFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2 && parts[0].equals(username) && parts[1].equals(password)) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
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
        String clientDigest = parts[1];

        Path userDir = Paths.get("src/main/resources/mailserver", username);
        if (Files.exists(userDir)) {
            // Récupérez le mot de passe réel de l'utilisateur
            String password = "password"; // Remplacez par la méthode appropriée pour obtenir le mot de passe
            String serverDigest = generateMD5Digest((serverGreeting + password).getBytes());

            if (serverDigest.equals(clientDigest)) {
                this.user = username;
                loadEmails();
                state = State.TRANSACTION;
                out.println("+OK APOP authentification reussie");
            } else {
                out.println("-ERR Authentification echouee");
            }
        } else {
            out.println("-ERR Utilisateur inconnu");
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
        Path userDir = Paths.get("src/main/resources/mailserver", user);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(userDir, "*.txt")) {
            for (Path file : stream) {
                emails.add(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleStat() {
        int messageCount = 0;
        long totalSize = 0;

        for (int i = 0; i < emails.size(); i++) {
            if (!markedForDeletion.contains(i)) {
                messageCount++;
                try {
                    totalSize += Files.size(emails.get(i));
                } catch (IOException e) {
                    out.println("-ERR Impossible de lire le fichier");
                    return;
                }
            }
        }
        out.println("+OK " + messageCount + " " + totalSize);
    }

    private void handleList(String arg) {
        if (arg.isEmpty()) {
            out.println("+OK Liste des messages");
            for (int i = 0; i < emails.size(); i++) {
                if (!markedForDeletion.contains(i)) {
                    try {
                        long size = Files.size(emails.get(i));
                        out.println((i + 1) + " " + size);
                    } catch (IOException e) {
                        out.println("-ERR Impossible de lire le fichier");
                    }
                }
            }
            out.println(".");
        } else {
            try {
                int index = Integer.parseInt(arg) - 1;
                if (index < 0 || index >= emails.size() || markedForDeletion.contains(index)) {
                    out.println("-ERR Aucun message avec ce numéro");
                    return;
                }
                long size = Files.size(emails.get(index));
                out.println("+OK " + (index + 1) + " " + size);
            } catch (NumberFormatException | IOException e) {
                out.println("-ERR Numéro de message invalide");
            }
        }
    }

    private void handleRetr(String arg) {
        try {
            int index = Integer.parseInt(arg) - 1;
            if (index < 0 || index >= emails.size() || markedForDeletion.contains(index)) {
                out.println("-ERR Aucun message avec ce numéro");
                return;
            }

            Path emailPath = emails.get(index);
            long size = Files.size(emailPath);
            out.println("+OK " + size + " octets");

            try (BufferedReader reader = Files.newBufferedReader(emailPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.println(line);
                }
            }
            out.println(".");
        } catch (NumberFormatException e) {
            out.println("-ERR Numéro de message invalide");
        } catch (IOException e) {
            out.println("-ERR Erreur lors de la lecture du message");
        }
    }

    private void handleDele(String arg) {
        try {
            int index = Integer.parseInt(arg) - 1;
            if (index < 0 || index >= emails.size() || markedForDeletion.contains(index)) {
                out.println("-ERR Aucun message avec ce numéro");
                return;
            }
            markedForDeletion.add(index);
            out.println("+OK Message " + arg + " marqué pour suppression");
        } catch (NumberFormatException e) {
            out.println("-ERR Numéro de message invalide");
        }
    }

    private void handleRset() {
        markedForDeletion.clear();
        out.println("+OK Toutes les suppressions ont été annulées");
    }

    private void handleUidl(String arg) {
        if (arg.isEmpty()) {
            out.println("+OK Liste des identifiants uniques");
            for (int i = 0; i < emails.size(); i++) {
                if (!markedForDeletion.contains(i)) {
                    out.println((i + 1) + " " + generateUniqueId(emails.get(i)));
                }
            }
            out.println(".");
        } else {
            try {
                int index = Integer.parseInt(arg) - 1;
                if (index < 0 || index >= emails.size() || markedForDeletion.contains(index)) {
                    out.println("-ERR Aucun message avec ce numéro");
                    return;
                }
                out.println("+OK " + (index + 1) + " " + generateUniqueId(emails.get(index)));
            } catch (NumberFormatException e) {
                out.println("-ERR Numéro de message invalide");
            }
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
            applyDeletions();
            out.println("+OK Déconnexion en cours");
        }
        state = State.UPDATE;
    }

    private void applyDeletions() {
        for (int index : markedForDeletion) {
            try {
                Files.delete(emails.get(index));
            } catch (IOException e) {
                System.err.println("Erreur lors de la suppression de " + emails.get(index));
            }
        }
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
