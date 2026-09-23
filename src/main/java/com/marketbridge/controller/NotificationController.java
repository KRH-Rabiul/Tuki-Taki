package com.marketbridge.controller;
import com.marketbridge.model.Notification;
import com.marketbridge.model.User;
import com.marketbridge.repository.NotificationRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

@RestController @RequestMapping("/api/notifications")
public class NotificationController {
    @Autowired private NotificationRepository notificationRepository;
    @GetMapping public ResponseEntity<?> all(HttpServletRequest request) {
        User user=(User)request.getAttribute("currentUser");
        return ResponseEntity.ok(notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
    }
    @GetMapping("/unread-count") public ResponseEntity<?> unreadCount(HttpServletRequest request) {
        User user=(User)request.getAttribute("currentUser");
        return ResponseEntity.ok(Map.of("count", notificationRepository.countByUserIdAndReadStatusFalse(user.getId())));
    }
    @PutMapping("/{id}/read") public ResponseEntity<?> read(HttpServletRequest request,@PathVariable Long id){
        User user=(User)request.getAttribute("currentUser"); Optional<Notification> opt=notificationRepository.findById(id);
        if(opt.isEmpty() || !opt.get().getUserId().equals(user.getId())) return ResponseEntity.status(404).body(Map.of("message","Notification not found"));
        Notification n=opt.get(); n.setReadStatus(true); return ResponseEntity.ok(notificationRepository.save(n));
    }
}
