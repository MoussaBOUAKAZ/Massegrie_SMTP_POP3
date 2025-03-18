package org.example;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmtpServer {
    public static final String DOMAIN = "Eoc.dz";
    private static final int PORT = 25;
    static final String MAIL_DIR = "src/main/resources/mailserver/";
    private static final int MAX_THREADS = 10; // Maximum number of threads
    private static final int QUEUE_CAPACITY = 20; // Maximum number of queued connections
    private static void logRejectedConnection() {
        try {
            Path logDir = Paths.get(MAIL_DIR, "logs");
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            Path logFile = logDir.resolve("rejected_connections.log");
            String logEntry = "Rejected connection at " + timestamp + "\n";
            Files.write(logFile, logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.out.println("Connexion rejetée enregistrée dans le journal : " + logFile);
            logRejectedConnection();
        } catch (IOException e) {
            System.err.println("Erreur lors de l'enregistrement de la connexion rejetée : " + e.getMessage());
        }
    }
    public static void main(String[] args) {
        ExecutorService threadPool = new ThreadPoolExecutor(
                MAX_THREADS, MAX_THREADS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                new ThreadPoolExecutor.AbortPolicy() // Reject new tasks when the queue is full
        );

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur SMTP en écoute sur le port " + PORT);
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(new SmtpHandler(clientSocket));
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

class SmtpHandler implements Runnable {
    private final Socket socket;
    private State state = State.WAITING_FOR_HELO;
    private String sender = null;
    private final Set<String> recipients = new HashSet<>();
    private boolean dataMode = false;
    private final StringBuilder messageData = new StringBuilder();

    public SmtpHandler(Socket socket) {
        this.socket = socket;
    }

    private enum State {
        WAITING_FOR_HELO,
        WAITING_FOR_MAIL_FROM,
        WAITING_FOR_RCPT_TO,
        WAITING_FOR_DATA,
        RECEIVING_MESSAGE
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            send(out, "220 " + SmtpServer.DOMAIN + " ESMTP Service Ready");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Client: " + line);
                String command = line.split(" ")[0].toUpperCase();
                switch (command) {
                    case "HELO":
                        handleHelo(out, line);
                        break;
                    case "EHLO":
                        handleHelo(out, line);
                        break;
                    case "MAIL":
                        handleMailFrom(out, line);
                        break;
                    case "RCPT":
                        handleRcptTo(out, line);
                        break;
                    case "DATA":
                        handleData(out);
                        break;
                    case "RSET":
                        handleRset(out);
                        break;
                    case "VRFY":
                        handleVrfy(out, line);
                        break;
                    case "EXPN":
                        handleExpn(out, line);
                        break;
                    case "NOOP":
                        handleNoop(out);
                        break;
                    case "QUIT":
                        handleQuit(out);
                        return;
                    default:
                        if (dataMode) {
                            handleMessage(out, line);
                        } else {
                            send(out, "502 Command not implemented");
                        }
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleHelo(BufferedWriter out, String line) throws IOException {
        if (state != State.WAITING_FOR_HELO) {
            send(out, "503 Bad sequence of commands: HELO/EHLO expected first");
            return;
        }

        String[] parts = line.split("\\s+", 2);
        System.out.println(parts);
        if (parts.length < 2 || !isValidDomain(parts[1].trim())) {
            send(out, "501 Syntax error in parameters or arguments");
            return;
        }

        String clientDomain = parts[1].trim();
        // Additional validation for clientDomain can be added here

        // Log the HELO interaction for storage
        saveHeloInteraction(clientDomain);
        state = State.WAITING_FOR_MAIL_FROM;
        send(out, "250 OK"   + " Hello " + clientDomain );
       /*
        send(out, "250-SIZE 10485760");
        send(out, "250-8BITMIME");
        send(out, "250-PIPELINING");
        send(out, "250 OK");*/
    }

    private void handleMailFrom(BufferedWriter out, String line) throws IOException {
        if (state != State.WAITING_FOR_MAIL_FROM) {
            send(out, "503 Bad sequence of commands: MAIL FROM expected after HELO/EHLO");
            return;
        }

        String email = extractEmail(line, "MAIL FROM:");
        if (email == null) {
            send(out, "501 Syntax error: Email address must be enclosed in angle brackets");
            return;
        }

        if (!isValidEmail(email)) {
            send(out, "553 Invalid sender address: " + email);
            return;
        }

        sender = email;
        state = State.WAITING_FOR_RCPT_TO;
        send(out, "250 OK");
    }

    private void handleRcptTo(BufferedWriter out, String line) throws IOException {
        if (state != State.WAITING_FOR_RCPT_TO && state != State.WAITING_FOR_DATA) {
            send(out, "503 Bad sequence of commands: RCPT TO expected after MAIL FROM");
            return;
        }

        String email = extractEmail(line, "RCPT TO:");
        if (email == null) {
            send(out, "501 Syntax error in parameters or arguments");
            return;
        }

        if (!isValidEmail(email)) {
            send(out, "553 Invalid recipient address");
            return;
        }

        recipients.add(email);
        state = State.WAITING_FOR_DATA;
        send(out, "250 OK");
    }

    private void handleData(BufferedWriter out) throws IOException {
        if (state != State.WAITING_FOR_DATA) {
            send(out, "503 Bad sequence of commands: RCPT TO required before DATA");
            return;
        }

        dataMode = true;
        state = State.RECEIVING_MESSAGE;
        send(out, "354 Start mail input; end with <CRLF>.<CRLF>");
    }

    private void handleMessage(BufferedWriter out, String line) throws IOException {
        if (messageData.length() > 10 * 1024 * 1024) { // 10 MB limit
            send(out, "552 Message size exceeds fixed limit");
            reset();
            return;
        }
        if (line.equals(".")) {
            dataMode = false;
            saveEmail(); // Save the email after receiving the message
            state = State.WAITING_FOR_MAIL_FROM; // Return to MAIL FROM state
            send(out, "250 Message received");
        } else {
            messageData.append(line).append("\r\n");
        }
    }

    private void handleRset(BufferedWriter out) throws IOException {
        reset();
        send(out, "250 OK - Reset state");
    }

    private void handleVrfy(BufferedWriter out, String line) throws IOException {
        // Implement VRFY command handling as per RFC 5321
        send(out, "252 Cannot VRFY user, but will accept message and attempt delivery");
    }

    private void handleExpn(BufferedWriter out, String line) throws IOException {
        // Implement EXPN command handling as per RFC 5321
        send(out, "502 Command not implemented");
    }

    private void handleNoop(BufferedWriter out) throws IOException {
        send(out, "250 OK");
    }

    private void handleQuit(BufferedWriter out) throws IOException {
        send(out, "221 " + SmtpServer.DOMAIN + " Service closing transmission channel");
        socket.close();
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$";
        return email.matches(emailRegex);
    }


    private boolean isValidDomain(String domain) {
        String domainRegex = "^(?!-)(?!.*--)[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.[A-Za-z]{2,}$";
        return domain.matches(domainRegex);
    }
    private String extractEmail(String line, String prefix) {
        // Regex stricte : doit être sous la forme MAIL FROM:<email@example.com>
        String regex = "^" + prefix + "\\s*<([^>]+)>$";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            return matcher.group(1).trim().toLowerCase(); // Normalisation en minuscules
        }

        // Log pour debugging
        System.out.println("Invalid MAIL FROM command: " + line);
        return null;
    }

    private void reset() {
        state = State.WAITING_FOR_HELO; // Reset to HELO state
        sender = null;
        recipients.clear();
        messageData.setLength(0);
        dataMode = false;
    }

    private void saveEmail() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String formattedDate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH).format(new Date());
        Path mailDir = Paths.get(SmtpServer.MAIL_DIR);
        if (!Files.exists(mailDir)) {
            Files.createDirectories(mailDir);
        }
        for (String recipient : recipients) {
            String sanitizedRecipient = recipient.replaceAll("[^A-Za-z0-9@._-]", "_");
            Path recipientDir = mailDir.resolve(sanitizedRecipient);
            if (!Files.exists(recipientDir)) {
                Files.createDirectories(recipientDir);
            }
            String filename = timestamp + ".txt";
            Path filePath = recipientDir.resolve(filename);

            // Construct the email header
            StringBuilder emailContent = new StringBuilder();
            emailContent.append("From: ").append(sender).append("\r\n");
            emailContent.append("To: ").append(recipient).append("\r\n");
            emailContent.append("Date: ").append(formattedDate).append("\r\n");
            emailContent.append("Subject: ").append("No Subject").append("\r\n");
            emailContent.append("\r\n"); // Separate headers from the body
            emailContent.append(messageData); // Append the email body

            // Save the email to the file
            Files.write(filePath, emailContent.toString().getBytes());
            System.out.println("E-mail sauvegardé : " + filePath.toString());
        }
        reset();
    }

    private void saveHeloInteraction(String clientDomain) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        Path logDir = Paths.get(SmtpServer.MAIL_DIR, "logs");
        if (!Files.exists(logDir)) {
            Files.createDirectories(logDir);
        }
        String filename = "helo_" + timestamp + ".log";
        Path filePath = logDir.resolve(filename);
        String logContent = "HELO/EHLO interaction with client domain: " + clientDomain + "\nTimestamp: " + timestamp;
        Files.write(filePath, logContent.getBytes());
        System.out.println("HELO interaction logged: " + filePath.toString());
    }

    private void send(BufferedWriter out, String response) throws IOException {
        out.write(response + "\r\n");
        out.flush();
        System.out.println("Serveur: " + response);
    }
}