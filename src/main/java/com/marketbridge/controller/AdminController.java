package com.marketbridge.controller;

import com.marketbridge.model.Notification;
import com.marketbridge.model.Order;
import com.marketbridge.model.Product;
import com.marketbridge.model.Report;
import com.marketbridge.model.User;

import com.marketbridge.repository.NotificationRepository;
import com.marketbridge.repository.OrderRepository;
import com.marketbridge.repository.ProductRepository;
import com.marketbridge.repository.ReportRepository;
import com.marketbridge.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private static final List<String> VALID_ORDER_STATUSES =
            List.of("PENDING", "CONFIRMED", "READY_FOR_PICKUP", "COLLECTED", "CANCELLED");

    // Every method in this controller is admin-only - this one check covers all of them.
    private ResponseEntity<?> requireAdmin(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null || !"ADMIN".equals(currentUser.getRole())) {
            return ResponseEntity.status(403).body(Map.of("message", "Admin access only"));
        }
        return null; // null means "check passed, continue"
    }

    // All buyers and sellers (for the admin's user list).
    // Optional ?query= (matches name or email) and ?role= narrow the list down,
    // so the table stays usable once there are a lot of accounts.
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(HttpServletRequest request,
                                          @RequestParam(required = false) String query,
                                          @RequestParam(required = false) String role) {
        ResponseEntity<?> denied = requireAdmin(request);
        if (denied != null) return denied;

        List<User> users = userRepository.findAll();
        String q = query == null ? null : query.trim().toLowerCase();
        String r = (role == null || role.isBlank()) ? null : role.trim().toUpperCase();

        List<User> filtered = users.stream()
                .filter(u -> q == null || q.isEmpty()
                        || (u.getName() != null && u.getName().toLowerCase().contains(q))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)))
                .filter(u -> r == null || r.equals(u.getRole()))
                .toList();

        filtered.forEach(u -> { u.setPassword(null); u.setToken(null); u.setResetToken(null); });
        return ResponseEntity.ok(filtered);
    }

    // One user's full picture: their account plus what they've listed and ordered,
    // so admin doesn't have to cross-reference the Products/Orders tabs by hand.
    @GetMapping("/users/{id}/details")
    public ResponseEntity<?> getUserDetails(HttpServletRequest request, @PathVariable Long id) {
        ResponseEntity<?> denied = requireAdmin(request);
        if (denied != null) return denied;

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "User not found"));

        User user = userOpt.get();
        user.setPassword(null);
        user.setToken(null);
        user.setResetToken(null);

        List<Product> listings = productRepository.findAll().stream()
                .filter(p -> id.equals(p.getSellerId())).toList();
        List<Order> purchases = orderRepository.findByBuyerId(id);
        List<Order> sales = orderRepository.findBySellerId(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", user);
        result.put("listings", listings);
        result.put("purchases", purchases);
        result.put("sales", sales);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/users/{id}/verify")
    public ResponseEntity<?> verifyUser(HttpServletRequest request, @PathVariable Long id) {
        ResponseEntity<?> denied = requireAdmin(request); if (denied != null) return denied;
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "User not found"));
        User user = userOpt.get(); user.setVerified(!user.isVerified()); userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", user.isVerified() ? "User verified" : "Verification removed"));
    }

    // Suspending blocks login immediately (see AuthController.login) AND kicks the
    // user out of any session they already have open (see AuthInterceptor), so a
    // suspension takes effect right away, not just on their next login attempt.
    @PutMapping("/users/{id}/suspend")
    public ResponseEntity<?> toggleSuspend(HttpServletRequest request, @PathVariable Long id) {
        ResponseEntity<?> denied = requireAdmin(request); if (denied != null) return denied;
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "User not found"));

        User user = userOpt.get();
        if ("ADMIN".equals(user.getRole())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Admin accounts can't be suspended"));
        }

        boolean nowSuspended = !"SUSPENDED".equals(user.getStatus());
        user.setStatus(nowSuspended ? "SUSPENDED" : "APPROVED");
        if (nowSuspended) user.setToken(null); // immediately ends any session they're currently in
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", nowSuspended ? "User suspended" : "User reinstated", "status", user.getStatus()));
    }

    // Overview numbers for the admin dashboard, plus a simple per-category product
    // breakdown (used for the small bar chart) and the most recent signups/orders
    // (used for the activity feed) - all cheap to compute from data we already have.
    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(HttpServletRequest request) {
        ResponseEntity<?> denied = requireAdmin(request);
        if (denied != null) return denied;

        List<User> allUsers = userRepository.findAll();
        List<Product> allProducts = productRepository.findAll();
        List<Order> allOrders = orderRepository.findAll();

        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        long pendingApprovals = 0;
        for (Product p : allProducts) {
            String cat = (p.getCategory() == null || p.getCategory().isBlank()) ? "Other" : p.getCategory();
            categoryCounts.merge(cat, 1L, Long::sum);
            if ("PENDING_REVIEW".equals(p.getAvailability())) pendingApprovals++;
        }

        List<User> recentUsers = allUsers.stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .limit(5)
                .map(u -> { User copy = u; copy.setPassword(null); copy.setToken(null); copy.setResetToken(null); return copy; })
                .toList();

        List<Order> recentOrders = allOrders.stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .limit(5)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", (long) allUsers.size());
        result.put("totalProducts", (long) allProducts.size());
        result.put("totalOrders", (long) allOrders.size());
        result.put("pendingApprovals", pendingApprovals);
        result.put("categoryCounts", categoryCounts);
        result.put("recentUsers", recentUsers);
        result.put("recentOrders", recentOrders);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/products")
    public ResponseEntity<?> getAllProducts(HttpServletRequest request,
                                             @RequestParam(required = false) String query,
                                             @RequestParam(required = false) String category) {
        ResponseEntity<?> denied = requireAdmin(request);
        if (denied != null) return denied;

        List<Product> products = productRepository.findAll();
        String q = query == null ? null : query.trim().toLowerCase();
        String c = (category == null || category.isBlank()) ? null : category;

        List<Product> filtered = products.stream()
                // Products still waiting on approval (or rejected) live in the
                // separate Approvals tab (see /products/pending below), not here.
                .filter(p -> !"PENDING_REVIEW".equals(p.getAvailability()) && !"REJECTED".equals(p.getAvailability()))
                .filter(p -> q == null || q.isEmpty() || (p.getName() != null && p.getName().toLowerCase().contains(q)))
                .filter(p -> c == null || c.equalsIgnoreCase(p.getCategory()))
                .toList();

        return ResponseEntity.ok(filtered);
    }

    // New listings waiting for a first look before they go live - oldest first,
    // so nothing sits unreviewed indefinitely just because newer ones keep coming in.
    @GetMapping("/products/pending")
    public ResponseEntity<?> getPendingProducts(HttpServletRequest request) {
        ResponseEntity<?> denied = requireAdmin(request);
        if (denied != null) return denied;

        List<Product> pending = productRepository.findAll().stream()
                .filter(p -> "PENDING_REVIEW".equals(p.getAvailability()))
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();
        return ResponseEntity.ok(pending);
    }

    @PutMapping("/products/{id}/approve")
    public ResponseEntity<?> approveProduct(HttpServletRequest request, @PathVariable Long id) {
        ResponseEntity<?> denied = requireAdmin(request); if (denied != null) return denied;
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Product not found"));
        Product product = productOpt.get();
        product.setAvailability("ACTIVE");
        product.setRejectionReason(null);
        productRepository.save(product);
        notify(product.getSellerId(), "Listing approved", "\"" + product.getName() + "\" is now live on the marketplace.", "product-details.html?id=" + product.getId());
        return ResponseEntity.ok(product);
    }

    @PutMapping("/products/{id}/reject")
    public ResponseEntity<?> rejectProduct(HttpServletRequest request, @PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        ResponseEntity<?> denied = requireAdmin(request); if (denied != null) return denied;
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Product not found"));
        Product product = productOpt.get();
        String reason = (body == null || body.get("reason") == null || body.get("reason").isBlank())
                ? "Didn't meet listing guidelines." : body.get("reason").trim();
        product.setAvailability("REJECTED");
        product.setRejectionReason(reason);
        productRepository.save(product);
        notify(product.getSellerId(), "Listing rejected", "\"" + product.getName() + "\" was not approved: " + reason, "seller-dashboard.html");
        return ResponseEntity.ok(product);
    }

    @PutMapping("/products/{id}/visibility")
    public ResponseEntity<?> toggleProductVisibility(HttpServletRequest request, @PathVariable Long id) {
        ResponseEntity<?> denied = requireAdmin(request); if (denied != null) return denied;
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Product not found"));
        Product product = productOpt.get();
        product.setAvailability("HIDDEN".equals(product.getAvailability()) ? "ACTIVE" : "HIDDEN");
        productRepository.save(product);
        return ResponseEntity.ok(product);
    }

    // Full removal (not just hiding) - used for listings that broke the rules.
    // Reuses the same delete logic sellers use on their own products (see
    // ProductController#deleteProduct), which already allows ADMIN through.
    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(HttpServletRequest request, @PathVariable Long id) {
        ResponseEntity<?> denied = requireAdmin(request); if (denied != null) return denied;
        if (productRepository.findById(id).isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Product not found"));
        }
        productRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Product deleted"));
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getAllOrders(HttpServletRequest request) {
        ResponseEntity<?> denied = requireAdmin(request);
        if (denied != null) return denied;

        List<Order> orders = orderRepository.findAll();
        return ResponseEntity.ok(orders);
    }

    // Lets admin step in on a stuck/disputed order - unlike the buyer/seller
    // endpoints in OrderController, this isn't limited to the next step in the
    // normal pickup flow. Two transitions are blocked outright rather than
    // guessed at, because neither has a safe, correct stock outcome:
    //   - COLLECTED -> CANCELLED: the item has already physically left the
    //     shelf, so "cancelling" it now must not hand stock back that isn't
    //     really there. If a collected order needs undoing, that's a refund/
    //     return process, not a status flip - handle it outside the system.
    //   - CANCELLED -> any active status: stock was already given back when
    //     it was cancelled, and may have since been sold to someone else, so
    //     there's no reliable amount left to take back out. Ask the buyer to
    //     place a new order instead.
    // Every other move to CANCELLED restores stock exactly once (guarded by
    // the previousStatus check below).
    @PutMapping("/orders/{id}/status")
    public ResponseEntity<?> setOrderStatus(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, String> body) {
        ResponseEntity<?> denied = requireAdmin(request); if (denied != null) return denied;

        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Order not found"));

        String nextStatus = body.get("status");
        if (nextStatus == null || !VALID_ORDER_STATUSES.contains(nextStatus)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Unknown order status"));
        }

        Order order = orderOpt.get();
        String previousStatus = order.getStatus();
        if (nextStatus.equals(previousStatus)) {
            return ResponseEntity.ok(order); // nothing to do
        }

        if ("COLLECTED".equals(previousStatus) && "CANCELLED".equals(nextStatus)) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "Can't cancel an order that's already been collected - the item has already left stock. Handle this as a refund/return instead."));
        }
        if ("CANCELLED".equals(previousStatus) && !"CANCELLED".equals(nextStatus)) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "Can't reactivate a cancelled order - its stock may have already been resold. Ask the buyer to place a new order."));
        }

        if ("CANCELLED".equals(nextStatus)) {
            productRepository.findById(order.getProductId()).ifPresent(product -> {
                product.setStock(product.getStock() + order.getQuantity());
                productRepository.save(product);
            });
        }

        order.setStatus(nextStatus);
        orderRepository.save(order);

        notify(order.getBuyerId(), "Order update", "An admin updated your order to " + nextStatus + ".", "buyer-dashboard.html");
        notify(order.getSellerId(), "Order update", "An admin updated order #" + order.getId() + " to " + nextStatus + ".", "seller-dashboard.html#orders");

        return ResponseEntity.ok(order);
    }

    private void notify(Long userId, String title, String message, String linkUrl) {
        Notification n = new Notification();
        n.setUserId(userId); n.setTitle(title); n.setMessage(message);
        n.setLinkUrl(linkUrl); n.setReadStatus(false); n.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(n);
    }
}
