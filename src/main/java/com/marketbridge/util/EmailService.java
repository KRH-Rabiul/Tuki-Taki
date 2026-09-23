package com.marketbridge.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// Thin wrapper around Spring's JavaMailSender.
//
// If spring.mail.username / spring.mail.password are left blank in
// application.properties (the default, no-setup state), sending will fail -
// that's expected and fine. AuthController catches that failure and falls
// back to the old "print the link to the console" behaviour, so nothing
// breaks for anyone who hasn't configured a real mailbox yet.
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public boolean isConfigured() {
        return mailSender != null && fromAddress != null && !fromAddress.isBlank();
    }

    // Throws on failure - caller decides what the fallback should be.
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        if (!isConfigured()) {
            throw new IllegalStateException("Email is not configured (spring.mail.username is blank)");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your Tuki-Taki password");
        message.setText(
                "We received a request to reset your Tuki-Taki password.\n\n" +
                "Click the link below to choose a new password (valid for 30 minutes):\n" +
                resetLink + "\n\n" +
                "If you didn't request this, you can safely ignore this email."
        );
        mailSender.send(message);
    }
}
