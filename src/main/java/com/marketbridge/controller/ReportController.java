package com.marketbridge.controller;
import com.marketbridge.model.Report;
import com.marketbridge.model.User;
import com.marketbridge.repository.ReportRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController @RequestMapping("/api/reports")
public class ReportController {
    @Autowired private ReportRepository reportRepository;
    @PostMapping public ResponseEntity<?> create(HttpServletRequest request,@RequestBody Map<String,String> body){
        User user=(User)request.getAttribute("currentUser");
        if(user==null || "ADMIN".equals(user.getRole())) return ResponseEntity.status(403).body(Map.of("message","Marketplace account required"));
        Report r=new Report(); r.setReporterId(user.getId()); r.setProductId(Long.valueOf(body.get("productId"))); r.setReason(body.getOrDefault("reason","Other"));
        r.setDetails(body.getOrDefault("details","")); r.setStatus("OPEN"); r.setCreatedAt(LocalDateTime.now()); return ResponseEntity.ok(reportRepository.save(r));
    }
    @GetMapping public ResponseEntity<?> all(HttpServletRequest request){ if(!isAdmin(request)) return ResponseEntity.status(403).body(Map.of("message","Admin access only")); return ResponseEntity.ok(reportRepository.findAll()); }
    @PutMapping("/{id}/resolve") public ResponseEntity<?> resolve(HttpServletRequest request,@PathVariable Long id){
        if(!isAdmin(request)) return ResponseEntity.status(403).body(Map.of("message","Admin access only")); Optional<Report> opt=reportRepository.findById(id);
        if(opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message","Report not found")); Report r=opt.get();r.setStatus("RESOLVED");return ResponseEntity.ok(reportRepository.save(r)); }
    @DeleteMapping("/{id}") public ResponseEntity<?> delete(HttpServletRequest request,@PathVariable Long id){
        if(!isAdmin(request)) return ResponseEntity.status(403).body(Map.of("message","Admin access only"));
        if(reportRepository.findById(id).isEmpty()) return ResponseEntity.status(404).body(Map.of("message","Report not found"));
        reportRepository.deleteById(id); return ResponseEntity.ok(Map.of("message","Report deleted")); }
    private boolean isAdmin(HttpServletRequest request){ User u=(User)request.getAttribute("currentUser");return u!=null&&"ADMIN".equals(u.getRole()); }
}
