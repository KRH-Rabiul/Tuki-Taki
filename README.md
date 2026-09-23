# 🛍️ Tuki-Taki

### A University-Centered Campus Marketplace

**Tuki-Taki** is a full-stack marketplace platform designed for university communities. It allows students and campus members to **buy and sell products through the same account**, communicate with sellers, manage orders, leave reviews, follow sellers, and participate in a campus community.

> **Buy locally. Sell locally. Pick up safely.**

Tuki-Taki does not use a delivery system. Orders are collected from a **fixed university pickup point**, keeping the marketplace simple and reducing delivery complexity.

---

## 🌐 Live Project

**Live Website:**  
https://tuki-taki.onrender.com

**GitHub Repository:**  
https://github.com/KRH-Rabiul/Tuki-Taki

---

## ✨ Highlights

- 🎓 University-focused marketplace
- 🛒 One account can both buy and sell
- 📦 Pickup-only order system
- 📍 Fixed campus pickup point
- 🖼️ Persistent product and profile image storage with Cloudinary
- 🔎 Search, category, price and condition filtering
- 💬 Buyer-seller messaging
- 🔔 Notifications
- ⭐ Reviews and ratings
- ❤️ Wishlist and seller following
- 🏪 Seller shop profiles
- 👥 Campus community discussion board
- 🛡️ Admin moderation and management
- 🌙 Light and dark themes
- 📱 Responsive frontend
- ✨ Smooth page transitions and UI animations

---

# 🎯 Project Vision

Traditional online marketplaces are designed for large-scale buying and selling, often involving delivery, shipping fees and complicated logistics.

Tuki-Taki focuses on a smaller and more practical environment:

**University → Campus Community → Local Marketplace → Fixed Pickup**

A member can discover a product, contact the seller, place an order and collect it from the university's designated pickup point.

---

# 🧩 Core Features

## 👤 Buyer

Buyers can:

- Browse products
- Search products
- Filter by category
- Filter by price range
- Filter by condition
- Sort listings
- View product details
- Browse product image galleries
- Add products to cart
- Checkout multiple products
- Track orders
- Cancel eligible orders
- Receive pickup codes
- Confirm pickup
- Review purchased products
- Give star ratings
- Add products to wishlist
- Follow sellers
- Send direct messages
- Receive notifications
- View seller shops

---

## 🏪 Seller

A marketplace account can also act as a seller.

Sellers can:

- Create product listings
- Upload up to five product images
- Edit listings
- Delete listings
- Manage stock
- Set product condition
- Set preferred pickup time
- View listing status
- Manage their shop
- Track followers
- View incoming orders
- Manage received orders
- View basic sales information
- Communicate with buyers

### Listing Moderation

New listings can pass through the admin review process before becoming publicly visible.

Possible listing states:

- `PENDING_REVIEW`
- `ACTIVE`
- `REJECTED`
- `RESERVED`
- `SOLD`

---

# 🛡️ Admin Panel

Administrators manage the marketplace through a dedicated admin area.

### Dashboard

- Marketplace overview
- Category statistics
- Recent activity
- Platform information

### Product Moderation

- Review pending listings
- Approve listings
- Reject listings with a reason
- Hide products
- Delete products

### User Management

- Search users
- Filter users
- Verify users
- Suspend users
- Reinstate users
- View user activity

### Orders

- Monitor orders
- Override order status when required
- Handle marketplace-level order issues

### Reports

- View reported listings
- Review reports
- Resolve reports

### Marketplace Settings

- Configure the campus pickup point
- Manage marketplace-level settings

---

# 💬 Campus Community

Tuki-Taki includes a separate community discussion area.

Users can:

- Browse community posts without logging in
- Create questions or requests after logging in
- Reply to posts
- Discuss campus-related topics

The community is intentionally kept separate from product listings.

---

# 🔐 Authentication & Account System

The application provides:

- Account registration
- Login
- Token-based sessions
- Account ownership checks
- Profile management
- Seller shop information
- Profile photo upload
- Forgot password
- Password reset
- Light/dark theme preference

Password reset tokens are:

- One-time use
- Valid for 30 minutes

For production password-reset email delivery, configure a real SMTP provider.

---

# 🖼️ Image Storage

Tuki-Taki uses **Cloudinary** for persistent image storage in production.

This includes:

- Product images
- Seller profile images

The backend uploads images to Cloudinary and stores the resulting secure URL in MySQL.

### Image Rules

- Maximum **5 images per product**
- Maximum **5 MB per image**
- Supported formats:
  - JPG/JPEG
  - PNG
  - WebP

---

# 🏗️ System Architecture

```text
                    ┌─────────────────────┐
                    │      Browser        │
                    │   HTML / CSS / JS   │
                    └──────────┬──────────┘
                               │
                               │ HTTP / REST
                               ▼
                    ┌─────────────────────┐
                    │   Spring Boot App   │
                    │      Java 17        │
                    └───────┬─────┬───────┘
                            │     │
              ┌─────────────┘     └──────────────┐
              ▼                                  ▼
     ┌─────────────────┐                ┌─────────────────┐
     │  Aiven MySQL    │                │   Cloudinary    │
     │ Users / Orders  │                │ Product/Profile │
     │ Products / etc. │                │     Images      │
     └─────────────────┘                └─────────────────┘
