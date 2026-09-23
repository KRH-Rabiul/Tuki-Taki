package com.marketbridge.controller;

import com.marketbridge.model.CommunityPost;
import com.marketbridge.model.CommunityReply;
import com.marketbridge.model.User;
import com.marketbridge.repository.CommunityPostRepository;
import com.marketbridge.repository.CommunityReplyRepository;
import com.marketbridge.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// The campus "Community" board: students can post a question or a request
// ("looking for a neck pillow", "does anyone have last term's notes?") and
// other students reply. Anyone can browse it; posting/replying needs login,
// same pattern as reviews - see AuthInterceptor for the public GET routes.
@RestController
@RequestMapping("/api/community")
public class CommunityController {

    @Autowired
    private CommunityPostRepository postRepository;

    @Autowired
    private CommunityReplyRepository replyRepository;

    @Autowired
    private UserRepository userRepository;

    // Community posts/replies only store an author snapshot (id + name), not
    // a live "verified" flag or profile photo, since those can change after
    // the post was made. This looks the current values up for display, in
    // one query per author rather than per-row, using the ids actually
    // present on the page.
    private Map<Long, User> authorLookup(java.util.Set<Long> authorIds) {
        Map<Long, User> result = new HashMap<>();
        for (Long id : authorIds) {
            userRepository.findById(id).ifPresent(u -> result.put(id, u));
        }
        return result;
    }

    @GetMapping("/posts")
    public ResponseEntity<?> getAllPosts() {
        List<CommunityPost> posts = postRepository.findAll();
        posts.sort((a, b) -> Long.compare(b.getId(), a.getId())); // newest first

        Map<Long, User> authors = authorLookup(posts.stream().map(CommunityPost::getAuthorId).collect(java.util.stream.Collectors.toSet()));
        List<Map<String, Object>> out = posts.stream().map(p -> postToMap(p, authors.get(p.getAuthorId()))).toList();
        return ResponseEntity.ok(out);
    }

    // Fetching a post also bumps its view count - simple "+1 every visit" counter,
    // not deduplicated per-visitor, which keeps this feature simple.
    @GetMapping("/posts/{id}")
    public ResponseEntity<?> getPost(@PathVariable Long id) {
        Optional<CommunityPost> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Post not found"));

        CommunityPost post = postOpt.get();
        post.setViewCount(post.getViewCount() + 1);
        postRepository.save(post);

        List<CommunityReply> replies = replyRepository.findByPostIdOrderByCreatedAtAsc(id);

        java.util.Set<Long> ids = new java.util.HashSet<>(replies.stream().map(CommunityReply::getAuthorId).toList());
        ids.add(post.getAuthorId());
        Map<Long, User> authors = authorLookup(ids);

        Map<String, Object> postMap = postToMap(post, authors.get(post.getAuthorId()));
        List<Map<String, Object>> replyMaps = replies.stream().map(r -> replyToMap(r, authors.get(r.getAuthorId()))).toList();

        return ResponseEntity.ok(Map.of("post", postMap, "replies", replyMaps));
    }

    private Map<String, Object> postToMap(CommunityPost p, User author) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId()); m.put("authorId", p.getAuthorId()); m.put("authorName", p.getAuthorName());
        m.put("authorVerified", author != null && author.isVerified());
        m.put("authorProfileImageUrl", author != null && author.getProfileImageUrl() != null ? author.getProfileImageUrl() : "");
        m.put("title", p.getTitle()); m.put("body", p.getBody());
        m.put("viewCount", p.getViewCount()); m.put("replyCount", p.getReplyCount()); m.put("createdAt", p.getCreatedAt());
        return m;
    }

    private Map<String, Object> replyToMap(CommunityReply r, User author) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId()); m.put("postId", r.getPostId()); m.put("authorId", r.getAuthorId());
        m.put("authorName", r.getAuthorName()); m.put("authorVerified", author != null && author.isVerified());
        m.put("authorProfileImageUrl", author != null && author.getProfileImageUrl() != null ? author.getProfileImageUrl() : "");
        m.put("body", r.getBody()); m.put("createdAt", r.getCreatedAt());
        return m;
    }

    @PostMapping("/posts")
    public ResponseEntity<?> createPost(HttpServletRequest request, @RequestBody Map<String, String> body) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) return ResponseEntity.status(403).body(Map.of("message", "Log in to post on the community"));

        String title = body.get("title");
        String text = body.get("body");
        if (title == null || title.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "Title is required"));
        if (title.length() > 150) return ResponseEntity.badRequest().body(Map.of("message", "Title is too long (150 characters max)"));
        if (text != null && text.length() > 2000) return ResponseEntity.badRequest().body(Map.of("message", "Details are too long (2000 characters max)"));

        CommunityPost post = new CommunityPost();
        post.setAuthorId(currentUser.getId());
        post.setAuthorName(currentUser.getName());
        post.setTitle(title.trim());
        post.setBody(text == null ? "" : text.trim());
        post.setViewCount(0);
        post.setReplyCount(0);
        post.setCreatedAt(LocalDateTime.now());

        return ResponseEntity.ok(postRepository.save(post));
    }

    @PostMapping("/posts/{id}/replies")
    public ResponseEntity<?> addReply(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, String> body) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) return ResponseEntity.status(403).body(Map.of("message", "Log in to reply"));

        Optional<CommunityPost> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Post not found"));

        String text = body.get("body");
        if (text == null || text.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "Reply can't be empty"));
        if (text.length() > 2000) return ResponseEntity.badRequest().body(Map.of("message", "Reply is too long (2000 characters max)"));

        CommunityReply reply = new CommunityReply();
        reply.setPostId(id);
        reply.setAuthorId(currentUser.getId());
        reply.setAuthorName(currentUser.getName());
        reply.setBody(text.trim());
        reply.setCreatedAt(LocalDateTime.now());
        replyRepository.save(reply);

        CommunityPost post = postOpt.get();
        post.setReplyCount(post.getReplyCount() + 1);
        postRepository.save(post);

        return ResponseEntity.ok(reply);
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<?> deletePost(HttpServletRequest request, @PathVariable Long id) {
        Optional<CommunityPost> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Post not found"));

        User currentUser = (User) request.getAttribute("currentUser");
        CommunityPost post = postOpt.get();
        boolean isOwner = currentUser != null && currentUser.getId().equals(post.getAuthorId());
        boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getRole());
        if (!isOwner && !isAdmin) return ResponseEntity.status(403).body(Map.of("message", "You can only delete your own posts"));

        replyRepository.findByPostIdOrderByCreatedAtAsc(id).forEach(r -> replyRepository.deleteById(r.getId()));
        postRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Post deleted"));
    }

    @DeleteMapping("/replies/{id}")
    public ResponseEntity<?> deleteReply(HttpServletRequest request, @PathVariable Long id) {
        Optional<CommunityReply> replyOpt = replyRepository.findById(id);
        if (replyOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Reply not found"));

        User currentUser = (User) request.getAttribute("currentUser");
        CommunityReply reply = replyOpt.get();
        boolean isOwner = currentUser != null && currentUser.getId().equals(reply.getAuthorId());
        boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getRole());
        if (!isOwner && !isAdmin) return ResponseEntity.status(403).body(Map.of("message", "You can only delete your own replies"));

        replyRepository.deleteById(id);
        postRepository.findById(reply.getPostId()).ifPresent(post -> {
            post.setReplyCount(Math.max(0, post.getReplyCount() - 1));
            postRepository.save(post);
        });
        return ResponseEntity.ok(Map.of("message", "Reply deleted"));
    }
}
