package org.example.restful.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.restful.dtos.EmailDTO;
import org.example.restful.services.EmailService;

import java.util.List;

@RestController
@RequestMapping("/emails")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendEmail(@RequestBody EmailDTO emailDTO) {
        boolean success = emailService.sendEmail(emailDTO);
        if (success) {
            return ResponseEntity.ok("Email sent successfully");
        } else {
            return ResponseEntity.status(500).body("Failed to send email");
        }
    }

    @GetMapping("/inbox/{userId}")
    public ResponseEntity<List<EmailDTO>> getInbox(@PathVariable String userId) {
        List<EmailDTO> emails = emailService.getInbox(userId);
        return ResponseEntity.ok(emails);
    }

    @DeleteMapping("/{emailId}")
    public ResponseEntity<String> deleteEmail(@PathVariable String emailId) {
        boolean success = emailService.deleteEmail(emailId);
        if (success) {
            return ResponseEntity.ok("Email deleted successfully");
        } else {
            return ResponseEntity.status(404).body("Email not found");
        }
    }
}
