package org.example;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoadTest {
    private static final String SMTP_SERVER = "smtp.eoc.dz";
    private static final int SMTP_PORT = 25;
    private static final String POP3_SERVER = "pop3.eoc.dz";
    private static final int POP3_PORT = 110;
    private static final int NUM_CLIENTS = 50; // Nombre de connexions simultanées

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(20); // Simule la saturation du pool de threads

        for (int i = 0; i < NUM_CLIENTS; i++) {
            executor.execute(() -> sendEmail("user" + new String(String.valueOf(Thread.currentThread().getId())) + "@eoc.dz"));
           // executor.execute(() -> checkEmail("user" + Thread.currentThread().getId() + "@eoc.dz", "123"));
        }

        executor.shutdown();
    }

    private static void sendEmail(String user) {
        try (Socket socket = new Socket(SMTP_SERVER, SMTP_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println(reader.readLine());
            writer.println("HELO client.dz");
            System.out.println(reader.readLine());
            writer.println("MAIL FROM:<" + user + ">");
            System.out.println(reader.readLine());
            writer.println("RCPT TO:<receiver2@example.com>");
            System.out.println(reader.readLine());
            writer.println("DATA");
            System.out.println(reader.readLine());
            writer.println("Subject: Test Load\n\nThis is a test email.\n.");
            System.out.println(reader.readLine());
            writer.println("QUIT");
            System.out.println(reader.readLine());
        } catch (IOException e) {
            System.err.println("SMTP Error: " + e.getMessage());
        }
    }

    private static void checkEmail(String user, String password) {
        try (Socket socket = new Socket(POP3_SERVER, POP3_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println(reader.readLine());
            writer.println("USER " + user);
            System.out.println(reader.readLine());
            writer.println("PASS " + password);
            System.out.println(reader.readLine());
            writer.println("STAT");
            System.out.println(reader.readLine());
            writer.println("LIST");
            System.out.println(reader.readLine());
            writer.println("QUIT");
            System.out.println(reader.readLine());
        } catch (IOException e) {
            System.err.println("POP3 Error: " + e.getMessage());
        }
    }
}
