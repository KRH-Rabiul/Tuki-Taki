package com.marketbridge.controller;

import com.marketbridge.model.Product;
import com.marketbridge.model.User;
import com.marketbridge.repository.ProductRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    // Fixed, absolute folder (see app.upload-dir in application.properties) -
    // deliberately NOT relative to the project folder, so uploaded photos
    // survive extracting a fresh project zip into a new folder.
    @Value("${app.upload-dir}")
    private String uploadDir;

    // Anyone can browse all products (buyer's home page / search).
    // Supports: keyword search, category filter, price range, sorting, and pagination.
    @GetMapping
    public ResponseEntity<?> getAllProducts(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(required = false) Double minPrice,
                                             @RequestParam(required = false) Double maxPrice,
                                             @RequestParam(required = false) String condition,
                                             @RequestParam(required = false, defaultValue = "newest") String sort,
                                             @RequestParam(required = false, defaultValue = "1") int page,
                                             @RequestParam(required = false, defaultValue = "12") int size) {
        List<Product> products;
        if (keyword != null && !keyword.isBlank()) {
            products = productRepository.findByNameContainingIgnoreCase(keyword);
        } else {
            products = productRepository.findAll();
        }

        // Category filter is applied on top so keyword + category can be combined.
        if (category != null && !category.isBlank()) {
            products = products.stream().filter(p -> category.equals(p.getCategory()))
                    .collect(java.util.stream.Collectors.toList());
        }

        products = products.stream().filter(p -> p.getAvailability() == null || "ACTIVE".equals(p.getAvailability()))
                .collect(java.util.stream.Collectors.toList());

        // Price range filter
        if (minPrice != null) {
            products = products.stream().filter(p -> p.getPrice() >= minPrice).collect(java.util.stream.Collectors.toList());
        }
        if (maxPrice != null) {
            products = products.stream().filter(p -> p.getPrice() <= maxPrice).collect(java.util.stream.Collectors.toList());
        }
        if (condition != null && !condition.isBlank()) {
            products = products.stream().filter(p -> condition.equals(p.getCondition())).collect(java.util.stream.Collectors.toList());
        }

        // Sort
        switch (sort) {
            case "price_asc" -> products.sort((a, b) -> Double.compare(a.getPrice(), b.getPrice()));
            case "price_desc" -> products.sort((a, b) -> Double.compare(b.getPrice(), a.getPrice()));
            case "name_asc" -> products.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            default -> products.sort((a, b) -> Long.compare(b.getId(), a.getId())); // "newest" = highest id first
        }

        int totalItems = products.size();
        int totalPages = (int) Math.ceil((double) totalItems / size);
        int fromIndex = Math.max(0, (page - 1) * size);
        int toIndex = Math.min(fromIndex + size, totalItems);
        List<Product> pageContent = fromIndex >= totalItems ? List.of() : products.subList(fromIndex, toIndex);

        return ResponseEntity.ok(Map.of(
                "products", pageContent,
                "currentPage", page,
                "totalPages", Math.max(totalPages, 1),
                "totalItems", totalItems
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProduct(HttpServletRequest request, @PathVariable Long id) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Product not found"));
        }
        Product product = productOpt.get();

        // A listing still waiting on admin approval, or one admin rejected,
        // is only visible to its own seller and to admins - not the public.
        boolean hidden = "PENDING_REVIEW".equals(product.getAvailability()) || "REJECTED".equals(product.getAvailability());
        if (hidden) {
            User currentUser = (User) request.getAttribute("currentUser");
            boolean isOwner = currentUser != null && currentUser.getId().equals(product.getSellerId());
            boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getRole());
            if (!isOwner && !isAdmin) {
                return ResponseEntity.status(404).body(Map.of("message", "Product not found"));
            }
        }

        return ResponseEntity.ok(product);
    }

    // Public seller shop listing: only ACTIVE products are exposed.
    @GetMapping("/seller/{sellerId}/active")
    public ResponseEntity<?> getActiveProductsBySeller(@PathVariable Long sellerId) {
        return ResponseEntity.ok(productRepository.findBySellerIdAndAvailability(sellerId, "ACTIVE"));
    }

    // All products belonging to one seller (for the seller's own dashboard).
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<?> getProductsBySeller(@PathVariable Long sellerId) {
        return ResponseEntity.ok(productRepository.findBySellerId(sellerId));
    }

    // Seller adds a new product WITH a photo in the same request (multipart/form-data).
    private static final List<String> VALID_CONDITIONS = List.of("NEW", "LIKE_NEW", "GOOD", "USED");

    // Shared by add + edit so both reject oversized/invalid input with a clean
    // message instead of letting a raw "Data too long for column" DB error
    // through. Limits match each field's @Column(length=...) on Product.java.
    private String validateFields(String name, String description, String category, String pickupWindow, String condition) {
        if (name != null && name.length() > 150) return "Product name is too long (150 characters max)";
        if (description != null && description.length() > 1000) return "Description is too long (1000 characters max)";
        if (category != null && category.length() > 50) return "Category is too long (50 characters max)";
        if (pickupWindow != null && pickupWindow.length() > 100) return "Pickup window is too long (100 characters max)";
        if (condition != null && !condition.isBlank() && !VALID_CONDITIONS.contains(condition)) return "Condition must be NEW, LIKE_NEW, GOOD, or USED";
        return null;
    }

    @PostMapping
    public ResponseEntity<?> addProduct(
            HttpServletRequest request,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") double price,
            @RequestParam("category") String category,
            @RequestParam("stock") int stock,
            @RequestParam(value = "condition", required = false, defaultValue = "USED") String condition,
            @RequestParam(value = "pickupWindow", required = false, defaultValue = "") String pickupWindow,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null || "ADMIN".equals(currentUser.getRole())) {
            return ResponseEntity.status(403).body(Map.of("message", "Log in with a marketplace account to add products"));
        }
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Product name is required"));
        }
        if (price < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Price cannot be negative"));
        }
        if (stock < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Stock cannot be negative"));
        }
        String fieldError = validateFields(name, description, category, pickupWindow, condition);
        if (fieldError != null) {
            return ResponseEntity.badRequest().body(Map.of("message", fieldError));
        }

        Product product = new Product();
        // sellerId always comes from the logged-in token, never trusted from the client -
        // otherwise a seller could add products under someone else's name.
        product.setSellerId(currentUser.getId());
        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setCategory(category);
        product.setStock(stock);
        product.setCondition(condition);
        product.setAvailability("PENDING_REVIEW"); // admin must approve before it's publicly visible - see AdminController
        product.setPickupWindow(pickupWindow);

        try {
            List<MultipartFile> allImages = mergeImages(images, image);
            if (!allImages.isEmpty()) {
                List<String> paths = saveImages(allImages);
                product.setImageUrl(paths.get(0));
                product.setImageUrls(String.join(",", paths));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("message", "Image upload failed: " + e.getMessage()));
        }

        Product saved = productRepository.save(product);
        return ResponseEntity.ok(saved);
    }

    // Seller edits an existing product, optionally replacing the photo.
    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("price") double price,
            @RequestParam("category") String category,
            @RequestParam("stock") int stock,
            @RequestParam(value = "condition", required = false, defaultValue = "USED") String condition,
            @RequestParam(value = "pickupWindow", required = false, defaultValue = "") String pickupWindow,
            @RequestParam(value = "availability", required = false, defaultValue = "ACTIVE") String availability,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        Optional<Product> existingOpt = productRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Product not found"));
        }

        Product product = existingOpt.get();

        User currentUser = (User) request.getAttribute("currentUser");
        boolean isOwner = currentUser != null && currentUser.getId().equals(product.getSellerId());
        boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getRole());
        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(403).body(Map.of("message", "You can only edit your own products"));
        }
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Product name is required"));
        }
        if (price < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Price cannot be negative"));
        }
        if (stock < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Stock cannot be negative"));
        }
        String fieldError = validateFields(name, description, category, pickupWindow, condition);
        if (fieldError != null) {
            return ResponseEntity.badRequest().body(Map.of("message", fieldError));
        }

        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setCategory(category);
        product.setStock(stock);
        product.setCondition(condition);
        product.setPickupWindow(pickupWindow);
        // A listing still waiting on admin review stays that way through an edit -
        // only AdminController's approve/reject endpoints can move it out of that
        // state, so editing details can't be used to sneak past review. A rejected
        // listing goes back into the review queue instead, so fixing the problem
        // and resubmitting is possible rather than being stuck rejected forever.
        if ("REJECTED".equals(product.getAvailability())) {
            product.setAvailability("PENDING_REVIEW");
            product.setRejectionReason(null);
        } else if (!"PENDING_REVIEW".equals(product.getAvailability()) && List.of("ACTIVE", "RESERVED", "SOLD").contains(availability)) {
            product.setAvailability(availability);
        }

        try {
            List<MultipartFile> allImages = mergeImages(images, image);
            if (!allImages.isEmpty()) {
                List<String> paths = saveImages(allImages);
                product.setImageUrl(paths.get(0));
                product.setImageUrls(String.join(",", paths));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("message", "Image upload failed: " + e.getMessage()));
        }

        return ResponseEntity.ok(productRepository.save(product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(HttpServletRequest request, @PathVariable Long id) {
        Optional<Product> existingOpt = productRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Product not found"));
        }

        Product product = existingOpt.get();
        User currentUser = (User) request.getAttribute("currentUser");
        boolean isOwner = currentUser != null && currentUser.getId().equals(product.getSellerId());
        boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getRole());
        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(403).body(Map.of("message", "You can only delete your own products"));
        }

        productRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Product deleted"));
    }

    // Saves the uploaded file to the "uploads" folder with a random unique name
    // (so two sellers uploading "photo.jpg" never overwrite each other).
    private List<MultipartFile> mergeImages(List<MultipartFile> images, MultipartFile legacyImage) {
        List<MultipartFile> result = new ArrayList<>();
        if (images != null) result.addAll(images.stream().filter(i -> !i.isEmpty()).toList());
        if (legacyImage != null && !legacyImage.isEmpty()) result.add(legacyImage);
        if (result.size() > 5) throw new IllegalArgumentException("Maximum 5 images per listing");
        return result;
    }

    private List<String> saveImages(List<MultipartFile> images) throws IOException {
        List<String> paths = new ArrayList<>();
        for (MultipartFile image : images) paths.add("/uploads/" + saveImage(image));
        return paths;
    }

    private String saveImage(MultipartFile image) throws IOException {
        if (image.getSize() > 5 * 1024 * 1024) throw new IOException("Each image must be 5 MB or smaller");
        String contentType = image.getContentType();
        if (contentType == null || !List.of("image/jpeg", "image/png", "image/webp").contains(contentType)) {
            throw new IOException("Only JPG, PNG, and WebP images are allowed");
        }
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Extension is derived from the checked content-type, not the raw filename,
        // so it can't contain path separators or unexpected characters.
        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };

        String uniqueFileName = UUID.randomUUID().toString() + extension;
        Path targetPath = Path.of(uploadDir, uniqueFileName);
        Files.copy(image.getInputStream(), targetPath);

        return uniqueFileName;
    }
}
