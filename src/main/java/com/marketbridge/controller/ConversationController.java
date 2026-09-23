package com.marketbridge.controller;

import com.marketbridge.model.*;
import com.marketbridge.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/messages")
public class ConversationController {
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User current(HttpServletRequest r){ return (User)r.getAttribute("currentUser"); }
    private ResponseEntity<?> denied(User u){ return ResponseEntity.status(403).body(Map.of("message","Login required")); }

    @GetMapping("/conversations")
    public ResponseEntity<?> conversations(HttpServletRequest request){
        User me=current(request); if(me==null) return denied(me);
        List<Conversation> rows=conversationRepository.findByBuyerIdOrSellerIdOrderByUpdatedAtDesc(me.getId(),me.getId());
        List<Map<String,Object>> out=new ArrayList<>();
        for(Conversation c:rows){
            Long otherId=me.getId().equals(c.getBuyerId())?c.getSellerId():c.getBuyerId();
            User other=userRepository.findById(otherId).orElse(null);
            // A conversation started from a Community post has no product attached (see /start below).
            Product p=c.getProductId()==null?null:productRepository.findById(c.getProductId()).orElse(null);
            List<Message> msgs=messageRepository.findByConversationIdOrderByCreatedAtAsc(c.getId());
            Message last=msgs.isEmpty()?null:msgs.get(msgs.size()-1);
            long unread=msgs.stream().filter(m->m.getReceiverId().equals(me.getId())&&!m.isReadStatus()).count();
            out.add(Map.of("id",c.getId(),"otherId",otherId,"otherName",other==null?"Unknown":other.getName(),"otherVerified",other!=null&&other.isVerified(),"otherProfileImageUrl",other!=null&&other.getProfileImageUrl()!=null?other.getProfileImageUrl():"","productId",c.getProductId()==null?0:c.getProductId(),"productName",p==null?"Community":p.getName(),"lastMessage",last==null?"":last.getContent(),"unread",unread));
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> messages(HttpServletRequest request,@PathVariable Long id){
        User me=current(request); if(me==null) return denied(me);
        Optional<Conversation> opt=conversationRepository.findById(id); if(opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message","Conversation not found"));
        Conversation c=opt.get();
        if(!me.getId().equals(c.getBuyerId())&&!me.getId().equals(c.getSellerId())) return ResponseEntity.status(403).body(Map.of("message","Access denied"));
        List<Message> msgs=messageRepository.findByConversationIdOrderByCreatedAtAsc(id);
        msgs.stream().filter(m->m.getReceiverId().equals(me.getId())&&!m.isReadStatus()).forEach(m->{m.setReadStatus(true);messageRepository.save(m);});
        return ResponseEntity.ok(Map.of("conversation",c,"messages",msgs));
    }

    // sellerId is always required. productId is optional - omit it (or send null)
    // to start a general conversation, e.g. messaging someone about a Community post
    // rather than about a specific listing.
    @PostMapping("/start")
    public ResponseEntity<?> start(HttpServletRequest request,@RequestBody Map<String,Object> body){
        User me=current(request); if(me==null||"ADMIN".equals(me.getRole())) return denied(me);
        if (body.get("sellerId") == null) return ResponseEntity.badRequest().body(Map.of("message","sellerId is required"));
        Long sellerId;
        Long productId = null;
        try {
            sellerId=Long.valueOf(String.valueOf(body.get("sellerId")));
            if (body.get("productId") != null) productId = Long.valueOf(String.valueOf(body.get("productId")));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("message","sellerId and productId must be numbers"));
        }
        if(me.getId().equals(sellerId)) return ResponseEntity.badRequest().body(Map.of("message","You cannot message yourself"));
        if(userRepository.findById(sellerId).isEmpty()) return ResponseEntity.status(404).body(Map.of("message","User not found"));
        if(productId!=null && productRepository.findById(productId).isEmpty()) return ResponseEntity.status(404).body(Map.of("message","Product not found"));

        Long finalProductId = productId;
        Conversation c = (productId == null
                ? conversationRepository.findByBuyerIdAndSellerIdAndProductIdIsNull(me.getId(), sellerId)
                : conversationRepository.findByBuyerIdAndSellerIdAndProductId(me.getId(), sellerId, productId)
        ).orElseGet(() -> {
            Conversation n = new Conversation();
            n.setBuyerId(me.getId()); n.setSellerId(sellerId); n.setProductId(finalProductId);
            n.setUpdatedAt(LocalDateTime.now());
            return conversationRepository.save(n);
        });
        return ResponseEntity.ok(c);
    }

    @PostMapping("/{id}")
    public ResponseEntity<?> send(HttpServletRequest request,@PathVariable Long id,@RequestBody Map<String,String> body){
        User me=current(request); if(me==null||"ADMIN".equals(me.getRole())) return denied(me);
        Conversation c=conversationRepository.findById(id).orElse(null); if(c==null) return ResponseEntity.status(404).body(Map.of("message","Conversation not found"));
        if(!me.getId().equals(c.getBuyerId())&&!me.getId().equals(c.getSellerId())) return ResponseEntity.status(403).body(Map.of("message","Access denied"));
        String content=body.getOrDefault("content","").trim(); if(content.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message","Message cannot be empty"));
        if(content.length()>2000) return ResponseEntity.badRequest().body(Map.of("message","Message is too long"));
        Long receiver=me.getId().equals(c.getBuyerId())?c.getSellerId():c.getBuyerId();
        Message m=new Message();m.setConversationId(id);m.setSenderId(me.getId());m.setReceiverId(receiver);m.setContent(content);m.setReadStatus(false);m.setCreatedAt(LocalDateTime.now());
        Message saved=messageRepository.save(m);
        c.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(c);

        // A notification is helpful, but a notification-storage problem must
        // never make an otherwise successful message appear to have failed.
        try {
            Notification n=new Notification();
            n.setUserId(receiver);
            n.setTitle("New message");
            n.setMessage(me.getName()+" sent you a message.");
            n.setLinkUrl("messages.html?conversation="+id);
            n.setReadStatus(false);
            n.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(n);
        } catch (RuntimeException notificationError) {
            System.err.println("Message notification could not be created: " + notificationError.getMessage());
        }

        return ResponseEntity.ok(saved);
    }
}
