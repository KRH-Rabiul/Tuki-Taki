package com.marketbridge.controller;

import com.marketbridge.model.User;
import com.marketbridge.repository.UserRepository;
import com.marketbridge.util.EmailService;
import com.marketbridge.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // Local/dev convenience only - see the note on forgotPassword() below.
    // MUST be set to false before this goes live on a real domain.
    @Value("${app.expose-dev-reset-link:true}")
    private boolean exposeDevResetLink;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    // How long a "forgot password" link/token stays valid.
    private static final long RESET_TOKEN_VALID_MINUTES = 30;

    // Every normal account can both buy and sell. Admin remains the only separate role.
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User newUser) {
        String email = newUser.getEmail() == null ? "" : newUser.getEmail().trim().toLowerCase();
        String name = newUser.getName() == null ? "" : newUser.getName().trim();
        String password = newUser.getPassword();

        if (name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Name is required"));
        }
        if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Enter a valid email address"));
        }
        if (password == null || password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 6 characters"));
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email already registered"));
        }

        newUser.setEmail(email);
        newUser.setName(name);
        newUser.setPassword(PasswordUtil.hash(password));
        newUser.setRole("USER");
        newUser.setStatus("APPROVED");

        User saved = userRepository.save(newUser);
        saved.setPassword(null); // never send the password hash back
        saved.setResetToken(null);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
        String email = loginData.get("email");
        String password = loginData.get("password");

        Optional<User> userOpt = email == null ? Optional.empty() : userRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty() || password == null || !PasswordUtil.matches(password, userOpt.get().getPassword())) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid email or password"));
        }

        User user = userOpt.get();

        // A suspended account can't start a new session (see AdminController#toggleSuspend).
        if ("SUSPENDED".equals(user.getStatus())) {
            return ResponseEntity.status(403).body(Map.of("message", "Your account has been suspended. Contact support if you think this is a mistake."));
        }

        // Fresh login token every time - this is what proves "who is calling"
        // on every protected request from now on (see AuthInterceptor).
        user.setToken(UUID.randomUUID().toString());
        userRepository.save(user);

        user.setPassword(null);
        user.setResetToken(null);
        return ResponseEntity.ok(user);
    }

    // Called when the user clicks "Logout" - invalidates their token server-side
    // so it can't be reused even if someone still has it saved somewhere.
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            userRepository.findByToken(token).ifPresent(u -> {
                u.setToken(null);
                userRepository.save(u);
            });
        }
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    // ---------------- Forgot / reset password ----------------

    // Step 1: user submits their email. We never reveal whether that email
    // actually exists (avoids leaking which addresses are registered).
    //
    // If a real mailbox is configured (see spring.mail.* in
    // application.properties), the reset link is emailed to the user.
    // If it isn't configured yet, or sending fails for any reason (wrong
    // password, no internet, etc.), we fall back to printing the link to the
    // console AND returning it in the API response, so the flow is still
    // fully testable locally without a mail server.
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String genericMessage = "If that email is registered, a password reset link has been generated.";

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        }

        Optional<User> userOpt = userRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            // Same response as the success case on purpose - don't leak which emails exist.
            return ResponseEntity.ok(Map.of("message", genericMessage));
        }

        User user = userOpt.get();
        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_VALID_MINUTES));
        userRepository.save(user);

        String resetPath = "/reset-password.html?token=" + token;
        String resetLink = baseUrl + resetPath;

        try {
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
            System.out.println("[Tuki-Taki] Password reset email sent to " + user.getEmail());
            return ResponseEntity.ok(Map.of("message", genericMessage));
        } catch (Exception e) {
            // Email not configured yet, or sending failed - don't fail the
            // request over it. Only echo the reset link back in the response
            // when app.expose-dev-reset-link=true (the local-dev default) -
            // on a real deployment this must be off, or anyone who knows a
            // registered email could grab their password-reset link straight
            // from this response instead of needing their actual inbox.
            System.out.println("[Tuki-Taki] Could not email reset link (" + e.getMessage()
                    + "). Link for " + user.getEmail() + " -> " + resetPath
                    + " (valid " + RESET_TOKEN_VALID_MINUTES + " minutes)");
            if (exposeDevResetLink) {
                return ResponseEntity.ok(Map.of("message", genericMessage, "devResetLink", resetPath));
            }
            return ResponseEntity.ok(Map.of("message", genericMessage));
        }
    }

    // Step 2: user opens the link (token in the URL) and submits a new password.
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("newPassword");

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Reset link is invalid"));
        }
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 6 characters"));
        }

        Optional<User> userOpt = userRepository.findByResetToken(token);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "This reset link is invalid or has already been used"));
        }

        User user = userOpt.get();
        if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            user.setResetToken(null);
            user.setResetTokenExpiry(null);
            userRepository.save(user);
            return ResponseEntity.badRequest().body(Map.of("message", "This reset link has expired. Please request a new one."));
        }

        user.setPassword(PasswordUtil.hash(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        user.setToken(null); // log out any existing session for safety
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password reset successful. Please log in with your new password."));
    }
}
