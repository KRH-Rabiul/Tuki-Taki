-- You do NOT need to run this manually.
-- Spring Boot (Hibernate) creates these tables automatically on first run
-- because of "spring.jpa.hibernate.ddl-auto=update" in application.properties.
--
-- This file is just here so you can see the table structure at a glance,
-- and so you can show it during your project presentation/viva.

CREATE DATABASE IF NOT EXISTS marketbridge_db;
USE marketbridge_db;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    password VARCHAR(255),
    role VARCHAR(20),      -- USER, ADMIN
    status VARCHAR(20),    -- APPROVED, PENDING, REJECTED
    token VARCHAR(255),    -- current login session token (set on login, cleared on logout)
    bio VARCHAR(500),
    phone VARCHAR(30),
    profile_image_url VARCHAR(500),
    shop_name VARCHAR(120),
    shop_description VARCHAR(1000),
    verified BOOLEAN,
    reset_token VARCHAR(255),        -- one-time "forgot password" token, null outside a reset flow
    reset_token_expiry DATETIME      -- reset_token stops being valid after this moment
);

CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seller_id BIGINT,
    name VARCHAR(255),
    description VARCHAR(1000),
    price DOUBLE,
    category VARCHAR(100),
    stock INT,
    image_url VARCHAR(500),
    image_urls VARCHAR(3000),  -- comma-separated list, up to 5 image paths
    condition_ VARCHAR(20),    -- NEW, LIKE_NEW, GOOD, USED
    availability VARCHAR(20),  -- PENDING_REVIEW, ACTIVE, RESERVED, SOLD, HIDDEN, REJECTED
    rejection_reason VARCHAR(500), -- set by admin when availability = REJECTED
    pickup_window VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    buyer_id BIGINT,
    product_id BIGINT,
    seller_id BIGINT,
    quantity INT,
    total_price DOUBLE,            -- price x quantity, locked at order time
    shipping_address VARCHAR(500), -- legacy field; pickup-only orders leave this empty
    pickup_point VARCHAR(255),
    pickup_code VARCHAR(20),
    product_name VARCHAR(255),     -- snapshot of product name at order time
    payment_method VARCHAR(30),
    notes VARCHAR(1000),
    buyer_name VARCHAR(80),        -- buyer's contact snapshot at order time, so the
    buyer_phone VARCHAR(30),       -- seller has a way to reach them about pickup
    buyer_contact VARCHAR(255),    -- (email, optional)
    status VARCHAR(30),    -- PENDING, CONFIRMED, READY_FOR_PICKUP, COLLECTED, CANCELLED
    order_date DATETIME
);

CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT,
    buyer_id BIGINT,
    buyer_name VARCHAR(255),
    rating INT,             -- 1 to 5
    comment VARCHAR(1000),
    created_at DATETIME
);

CREATE TABLE IF NOT EXISTS wishlist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    buyer_id BIGINT,
    product_id BIGINT
);

CREATE TABLE IF NOT EXISTS reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT,
    product_id BIGINT,
    reason VARCHAR(255),
    details VARCHAR(1000),
    status VARCHAR(20),     -- OPEN, RESOLVED
    created_at DATETIME
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    title VARCHAR(255),
    message VARCHAR(1000),
    link_url VARCHAR(255),
    read_status BOOLEAN,
    created_at DATETIME
);

CREATE TABLE IF NOT EXISTS marketplace_settings (
    id BIGINT PRIMARY KEY,
    pickup_point VARCHAR(255),
    pickup_instructions VARCHAR(1000)
);

CREATE TABLE IF NOT EXISTS follows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    follower_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    created_at DATETIME,
    UNIQUE KEY unique_follow (follower_id, seller_id)
);

CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    product_id BIGINT, -- NULL for a general conversation (e.g. started from a Community post)
    updated_at DATETIME,
    UNIQUE KEY unique_conversation (buyer_id, seller_id, product_id)
);

CREATE TABLE IF NOT EXISTS community_posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_id BIGINT,
    author_name VARCHAR(255),
    title VARCHAR(150),
    body VARCHAR(2000),
    view_count INT DEFAULT 0,
    reply_count INT DEFAULT 0,
    created_at DATETIME
);

CREATE TABLE IF NOT EXISTS community_replies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT,
    author_id BIGINT,
    author_name VARCHAR(255),
    body VARCHAR(2000),
    created_at DATETIME
);

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    read_status BOOLEAN,
    created_at DATETIME
);
