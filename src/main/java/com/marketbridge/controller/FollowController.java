package com.marketbridge.controller;

import com.marketbridge.model.Follow;
import com.marketbridge.model.User;
import com.marketbridge.repository.FollowRepository;
import com.marketbridge.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/follows")
public class FollowController {
    @Autowired private FollowRepository followRepository;
    @Autowired private UserRepository userRepository;

    private User current(HttpServletRequest r){ return (User) r.getAttribute("currentUser"); }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<?> sellerStatus(HttpServletRequest request, @PathVariable Long sellerId){
        long followers = followRepository.countBySellerId(sellerId);
        User me = current(request);
        boolean following = me != null && followRepository.findByFollowerIdAndSellerId(me.getId(), sellerId).isPresent();
        return ResponseEntity.ok(Map.of("sellerId", sellerId, "followers", followers, "following", following));
    }

    @PostMapping("/seller/{sellerId}")
    public ResponseEntity<?> follow(HttpServletRequest request, @PathVariable Long sellerId){
        User me = current(request);
        if(me == null || "ADMIN".equals(me.getRole())) return ResponseEntity.status(403).body(Map.of("message","Marketplace account required"));
        if(me.getId().equals(sellerId)) return ResponseEntity.badRequest().body(Map.of("message","You cannot follow yourself"));
        if(userRepository.findById(sellerId).isEmpty()) return ResponseEntity.status(404).body(Map.of("message","Seller not found"));
        if(followRepository.findByFollowerIdAndSellerId(me.getId(), sellerId).isEmpty()){
            Follow f = new Follow(); f.setFollowerId(me.getId()); f.setSellerId(sellerId); f.setCreatedAt(LocalDateTime.now()); followRepository.save(f);
        }
        return sellerStatus(request, sellerId);
    }

    @DeleteMapping("/seller/{sellerId}")
    public ResponseEntity<?> unfollow(HttpServletRequest request, @PathVariable Long sellerId){
        User me = current(request);
        if(me == null) return ResponseEntity.status(403).body(Map.of("message","Login required"));
        followRepository.findByFollowerIdAndSellerId(me.getId(), sellerId).ifPresent(followRepository::delete);
        return sellerStatus(request, sellerId);
    }

    @GetMapping("/following")
    public ResponseEntity<?> following(HttpServletRequest request){
        User me = current(request);
        if(me == null) return ResponseEntity.status(403).body(Map.of("message","Login required"));
        List<Long> sellerIds = followRepository.findByFollowerId(me.getId()).stream().map(Follow::getSellerId).toList();
        return ResponseEntity.ok(userRepository.findAllById(sellerIds).stream().map(u -> Map.of("id",u.getId(),"name",u.getName(),"verified",u.isVerified())).toList());
    }
}
