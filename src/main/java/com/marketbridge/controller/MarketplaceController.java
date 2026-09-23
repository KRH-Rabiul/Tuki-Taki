package com.marketbridge.controller;
import com.marketbridge.model.MarketplaceSettings;
import com.marketbridge.model.User;
import com.marketbridge.repository.MarketplaceSettingsRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/marketplace")
public class MarketplaceController {
    @Autowired private MarketplaceSettingsRepository settingsRepository;
    @GetMapping("/settings") public MarketplaceSettings settings(){ return getSettings(); }
    @PutMapping("/settings") public ResponseEntity<?> update(HttpServletRequest request,@RequestBody Map<String,String> body){
        User user=(User)request.getAttribute("currentUser"); if(user==null || !"ADMIN".equals(user.getRole())) return ResponseEntity.status(403).body(Map.of("message","Admin access only"));
        MarketplaceSettings s=getSettings(); String point=body.getOrDefault("pickupPoint","").trim();
        if(point.isEmpty() || point.length()>255) return ResponseEntity.badRequest().body(Map.of("message","Enter a valid pickup point"));
        s.setPickupPoint(point); s.setPickupInstructions(body.getOrDefault("pickupInstructions","").trim()); return ResponseEntity.ok(settingsRepository.save(s));
    }
    private MarketplaceSettings getSettings(){ return settingsRepository.findById(1L).orElseGet(() -> settingsRepository.save(new MarketplaceSettings())); }
}
