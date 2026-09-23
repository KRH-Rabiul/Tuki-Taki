package com.marketbridge.controller;

import com.marketbridge.model.Order;
import com.marketbridge.model.Product;
import com.marketbridge.model.User;
import com.marketbridge.repository.OrderRepository;
import com.marketbridge.repository.ProductRepository;
import com.marketbridge.repository.MarketplaceSettingsRepository;
import com.marketbridge.repository.NotificationRepository;
import com.marketbridge.model.Notification;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired private MarketplaceSettingsRepository settingsRepository;
    @Autowired private NotificationRepository notificationRepository;

    // Every order now goes through /api/orders/checkout below (product-details.html's
    // "Buy now" redirects into checkout.html, which posts there too) - this keeps
    // order placement to a single, well-tested code path instead of two.

    // Validates the whole cart before changing stock, so checkout is all-or-nothing.
    @PostMapping("/checkout")
    @Transactional
    public ResponseEntity<?> checkout(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null || "ADMIN".equals(currentUser.getRole())) return ResponseEntity.status(403).body(Map.of("message", "Marketplace account required"));
        Object itemsValue = body.get("items");
        if (!(itemsValue instanceof List<?> rawItems) || rawItems.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "Your cart is empty"));
        if (rawItems.size() > 20) return ResponseEntity.badRequest().body(Map.of("message", "A checkout can contain at most 20 listings"));

        String paymentMethod = body.get("paymentMethod") == null ? "CASH_ON_PICKUP" : body.get("paymentMethod").toString();
        java.util.Set<String> allowedPayments = java.util.Set.of("CASH_ON_PICKUP", "BKASH", "NAGAD", "ROCKET", "CARD");
        if (!allowedPayments.contains(paymentMethod)) return ResponseEntity.badRequest().body(Map.of("message", "Choose a valid payment method"));
        String notes = body.get("notes") == null ? "" : body.get("notes").toString().trim();
        if (notes.length() > 1000) return ResponseEntity.badRequest().body(Map.of("message", "Notes are too long"));
        String buyerName = body.get("buyerName") == null ? currentUser.getName() : body.get("buyerName").toString().trim();
        String buyerPhone = body.get("buyerPhone") == null ? "" : body.get("buyerPhone").toString().trim();
        String buyerContact = body.get("buyerContact") == null ? "" : body.get("buyerContact").toString().trim();

        List<Product> products = new java.util.ArrayList<>();
        List<Integer> quantities = new java.util.ArrayList<>();
        for (Object raw : rawItems) {
            if (!(raw instanceof Map<?, ?> item)) return ResponseEntity.badRequest().body(Map.of("message", "Invalid cart item"));
            Long productId = Long.valueOf(String.valueOf(item.get("productId")));
            int quantity = Integer.parseInt(String.valueOf(item.get("quantity")));
            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "A product in your cart is no longer available"));
            Product product = productOpt.get();
            if (quantity < 1 || quantity > product.getStock() || (product.getAvailability() != null && !"ACTIVE".equals(product.getAvailability())))
                return ResponseEntity.badRequest().body(Map.of("message", product.getName() + " is no longer available in that quantity"));
            if (currentUser.getId().equals(product.getSellerId())) return ResponseEntity.badRequest().body(Map.of("message", "You cannot order your own listing"));
            products.add(product); quantities.add(quantity);
        }

        String pickupPoint = settingsRepository.findById(1L).map(s -> s.getPickupPoint()).orElse("Bonomaya");
        List<Order> orders = new java.util.ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i); int quantity = quantities.get(i);
            Order order = new Order();
            order.setBuyerId(currentUser.getId()); order.setProductId(product.getId()); order.setSellerId(product.getSellerId());
            order.setQuantity(quantity); order.setTotalPrice(product.getPrice() * quantity); order.setProductName(product.getName());
            order.setPickupPoint(pickupPoint); order.setPaymentMethod(paymentMethod); order.setNotes(notes);
            order.setBuyerName(buyerName); order.setBuyerPhone(buyerPhone); order.setBuyerContact(buyerContact);
            order.setPickupCode(UUID.randomUUID().toString().substring(0, 6).toUpperCase()); order.setStatus("PENDING"); order.setOrderDate(LocalDateTime.now());
            product.setStock(product.getStock() - quantity); productRepository.save(product); orders.add(orderRepository.save(order));
            notify(product.getSellerId(), "New pickup order", product.getName() + " has a new order to prepare.", "seller-dashboard.html");
        }
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/buyer/{buyerId}")
    public ResponseEntity<?> getOrdersByBuyer(HttpServletRequest request, @PathVariable Long buyerId) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null || !currentUser.getId().equals(buyerId)) {
            return ResponseEntity.status(403).body(Map.of("message", "You can only view your own orders"));
        }
        return ResponseEntity.ok(orderRepository.findByBuyerId(buyerId));
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<?> getOrdersBySeller(HttpServletRequest request, @PathVariable Long sellerId) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null || !currentUser.getId().equals(sellerId)) {
            return ResponseEntity.status(403).body(Map.of("message", "You can only view your own orders"));
        }
        return ResponseEntity.ok(orderRepository.findBySellerId(sellerId));
    }

    // Seller progresses an order to the pickup point. The buyer alone confirms collection.
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, String> body) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Order not found"));
        }
        Order order = orderOpt.get();

        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null || !currentUser.getId().equals(order.getSellerId())) {
            return ResponseEntity.status(403).body(Map.of("message", "Only the listing owner can update this order"));
        }

        String nextStatus = body.get("status");
        boolean validTransition = ("PENDING".equals(order.getStatus()) && "CONFIRMED".equals(nextStatus))
                || ("CONFIRMED".equals(order.getStatus()) && "READY_FOR_PICKUP".equals(nextStatus));
        if (!validTransition) {
            return ResponseEntity.badRequest().body(Map.of("message", "That pickup status change is not allowed"));
        }
        order.setStatus(nextStatus);
        Order saved = orderRepository.save(order);
        String message = "CONFIRMED".equals(nextStatus) ? "Your order was accepted." : "Your order is ready at " + order.getPickupPoint() + ".";
        notify(order.getBuyerId(), "Order update", message, "buyer-dashboard.html");
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/collect")
    public ResponseEntity<?> confirmCollection(HttpServletRequest request, @PathVariable Long id,
                                                @RequestBody Map<String, String> body) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "Order not found"));

        Order order = orderOpt.get();
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null || !currentUser.getId().equals(order.getBuyerId())) {
            return ResponseEntity.status(403).body(Map.of("message", "Only the buyer can confirm collection"));
        }
        if (!"READY_FOR_PICKUP".equals(order.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "This order is not ready for collection"));
        }
        if (!order.getPickupCode().equalsIgnoreCase(body.getOrDefault("pickupCode", ""))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Pickup code does not match"));
        }

        order.setStatus("COLLECTED");
        Order saved = orderRepository.save(order);
        notify(order.getSellerId(), "Order collected", order.getProductName() + " was collected by the buyer.", "seller-dashboard.html");
        return ResponseEntity.ok(saved);
    }

    // Buyer or seller cancels an order - only while it's still PENDING (not yet confirmed/shipped).
    // Stock goes back to the product so it can be bought again.
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(HttpServletRequest request, @PathVariable Long id) {
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Order not found"));
        }
        Order order = orderOpt.get();

        User currentUser = (User) request.getAttribute("currentUser");
        boolean isBuyer = currentUser != null && currentUser.getId().equals(order.getBuyerId());
        boolean isSeller = currentUser != null && currentUser.getId().equals(order.getSellerId());
        if (!isBuyer && !isSeller) {
            return ResponseEntity.status(403).body(Map.of("message", "You can only cancel your own orders"));
        }

        if (!"PENDING".equals(order.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only pending orders can be cancelled"));
        }

        order.setStatus("CANCELLED");
        orderRepository.save(order);

        // Give the stock back to the product
        productRepository.findById(order.getProductId()).ifPresent(product -> {
            product.setStock(product.getStock() + order.getQuantity());
            productRepository.save(product);
        });

        return ResponseEntity.ok(order);
    }

    private void notify(Long userId, String title, String message, String linkUrl) {
        Notification n = new Notification(); n.setUserId(userId); n.setTitle(title); n.setMessage(message);
        n.setLinkUrl(linkUrl); n.setReadStatus(false); n.setCreatedAt(LocalDateTime.now()); notificationRepository.save(n);
    }
}
