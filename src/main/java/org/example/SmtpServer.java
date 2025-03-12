/*package org.example;
import java.io.*;
import java.net.*;

public class SmtpServer {
    public static void main(String[] args) {
        int port = 25; // Port SMTP personnalisé pour éviter les conflits (25 nécessite des privilèges root)

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serveur SMTP en écoute sur le port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new SmtpHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class SmtpHandler implements Runnable {
    private final Socket socket;

    public SmtpHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
        ) {
            // Envoi du message de bienvenue
            out.write("220 SMTP Server Ready\r\n");
            out.flush();

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Client: " + line);

                if (line.startsWith("HELO")) {
                    //out.write("250-Hello " + line.split(" ")[1] + "\r\n");
                    //out.write("250-SIZE 10485760\r\n");
                    out.write("250 OK\r\n");
                } else if (line.startsWith("MAIL FROM:")) {
                    out.write("250 OK\r\n");
                } else if (line.startsWith("RCPT TO:")) {
                    out.write("250 OK\r\n");
                } else if (line.equals("DATA")) {
                    out.write("354 End data with <CRLF>.<CRLF>\r\n");
                } else if (line.equals(".")) { // Fin du message
                    out.write("250 Message received\r\n");
                } else if (line.equals("QUIT")) {
                    out.write("221 Bye\r\n");
                    break;
                } else {
                    out.write("502 Command not implemented\r\n");
                }
                out.flush();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
*/
package org.example;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class SmtpServer {
    public static void main(String[] args) {
        int port = 25;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serveur SMTP en écoute sur le port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new SmtpHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
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
    private static final String MAIL_DIR = "src/main/resources/mailserver/alice@example.com";

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
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
        ) {
            send(out, "220 smtp.example.com ESMTP Service Ready");
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Client: " + line);
                if (line.toUpperCase().startsWith("HELO") || line.toUpperCase().startsWith("EHLO")) {
                    handleHelo(out, line);
                } else if (line.toUpperCase().startsWith("MAIL FROM:")) {
                    handleMailFrom(out, line);
                } else if (line.toUpperCase().startsWith("RCPT TO:")) {
                    handleRcptTo(out, line);
                } else if (line.toUpperCase().equals("DATA")) {
                    handleData(out);
                } else if (dataMode) {
                    handleMessage(out, line);
                } else if (line.toUpperCase().equals("QUIT")) {
                    send(out, "221 smtp.example.com Service closing transmission channel");
                    break;
                } else {
                    send(out, "502 Command not implemented");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleHelo(BufferedWriter out, String line) throws IOException {
        if (state != State.WAITING_FOR_HELO) {
            send(out, "503 Bad sequence of commands: HELO/EHLO expected first");
            return;
        }
        state = State.WAITING_FOR_MAIL_FROM;
       /*
        send(out, "250-smtp.example.com Hello [" + socket.getInetAddress().getHostAddress() + "]");
        send(out, "250-SIZE 10485760");
        */
        send(out, "250 OK");

    }

    private void handleMailFrom(BufferedWriter out, String line) throws IOException {
        if (state != State.WAITING_FOR_MAIL_FROM) {
            send(out, "503 Bad sequence of commands: MAIL FROM expected after HELO");
            return;
        }
        sender = line.substring(10).trim();
        state = State.WAITING_FOR_RCPT_TO;
        send(out, "250 OK");
    }

    private void handleRcptTo(BufferedWriter out, String line) throws IOException {
        if (state != State.WAITING_FOR_RCPT_TO) {
            send(out, "503 Bad sequence of commands: RCPT TO expected after MAIL FROM");
            return;
        }
        recipients.add(line.substring(8).trim());
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
        if (line.equals(".")) {
            dataMode = false;
            saveEmail();
            state = State.WAITING_FOR_HELO;
            send(out, "250 Message received");
        } else {
            messageData.append(line).append("\r\n");
        }
    }

    private void saveEmail() {
        try {
            for (String recipient : recipients) {
                String sanitizedRecipient = recipient.replaceAll("[<>]", "");
                String userDir = MAIL_DIR  + sanitizedRecipient;
                Files.createDirectories(Paths.get(userDir));
                String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                Path emailPath = Paths.get(userDir, timestamp + ".txt");
                Files.write(emailPath, messageData.toString().getBytes());
            }
            messageData.setLength(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void send(BufferedWriter out, String response) throws IOException {
        out.write(response + "\r\n");
        out.flush();
        System.out.println("Serveur: " + response);
    }
}