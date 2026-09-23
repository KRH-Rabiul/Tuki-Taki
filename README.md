# Tuki-Taki

A local university-centered campus marketplace (formerly MarketBridge). A normal account can both **buy and sell**; administrators manage the platform.

- Frontend: plain HTML + CSS + JavaScript (`src/main/resources/static`)
- Backend: Java + Spring Boot (`src/main/java/com/marketbridge`)
- Database: MySQL

No complex frameworks. Every Java class has comments explaining what it does, so it's easy to modify and easy to explain.

---

## Current feature list

This section is kept up to date - if you only read one part of this file, read this one.

**Buyer:** browse/search/filter (category, price, condition) with pagination,
product detail pages with an image gallery, cart + multi-product checkout,
order tracking with a pickup code, order cancel (restores stock), reviews &
star ratings, wishlist, follow a seller, direct messaging, notifications.

**Seller:** listing CRUD with multi-image upload, new listings go through
admin approval before they're publicly visible, a seller shop page with
follower count and a "verified" blue-checkmark badge, orders-received queue,
basic sales stats.

**Admin:** overview dashboard with a category chart and recent-activity
feed, pending-listing approvals (approve/reject with a reason), user
management (search/filter, suspend/reinstate, verify, view a user's full
activity), product moderation (hide/delete), order status override for
disputes, reports queue, marketplace pickup-point settings.

**Community:** a campus discussion board (separate from product listings)
where anyone can post a question/request and reply - login required to post,
open to browse without an account.

**Account/auth:** token-based sessions, ownership checks on every endpoint,
forgot/reset password (emails a real reset link if SMTP is configured,
otherwise falls back to a local-dev shortcut - see `app.expose-dev-reset-link`
in `application.properties`), dark/light theme.

---

## 1. Install requirements (Ubuntu)

```bash
sudo apt update
sudo apt install openjdk-17-jdk maven mysql-server -y
```

Check versions:
```bash
java -version
mvn -version
```

## 2. Set up MySQL

Start MySQL and create a password for the root user (or use an existing user):

```bash
sudo systemctl start mysql
sudo mysql
```

Inside the MySQL prompt:
```sql
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'yourpassword';
FLUSH PRIVILEGES;
EXIT;
```

You do **not** need to manually create tables — Spring Boot creates the database and tables automatically the first time it runs. (`database/schema.sql` is included just for reference, in case you want to show it during your project presentation.)

## 3. Configure the project

Open `src/main/resources/application.properties` and set your MySQL username/password:

```properties
spring.datasource.username=root
spring.datasource.password=yourpassword
```

## 4. Run the project

From the project's root folder:

```bash
mvn spring-boot:run
```

Then open your browser at:

```
http://localhost:8080
```

## 5. Logging in

- **Marketplace members**: register from the "Sign up" page. One account can buy and sell.
- **Admin**: a starter admin account is created automatically on first run:
  - Email: `admin@marketbridge.com`
  - Password: `admin123`
  - Change these starter credentials in `DataInitializer.java` before any public deployment. They are deliberately no longer shown on the login page.

## Forgot password

The login page has a "Forgot password?" link -> `forgot-password.html`. The flow:

1. User enters their email on `forgot-password.html`, which calls `POST /api/auth/forgot-password`.
2. The backend generates a one-time token, valid for **30 minutes**, and saves it on that user's row.
3. **This project has no email server configured** (no `spring-boot-starter-mail`, no SMTP settings), so instead
   of emailing the link, `AuthController.forgotPassword()` prints it to the server console and also returns it
   in the API response under `devResetLink`. The `forgot-password.html` page shows that link directly on screen
   so you can test the whole flow locally without setting up email.
4. Opening that link goes to `reset-password.html?token=...`, where the user sets a new password via
   `POST /api/auth/reset-password`. The token is single-use and expires after 30 minutes.

**Before using this for real users**, wire step 3 to an actual email send (e.g. add
`spring-boot-starter-mail` + SMTP settings in `application.properties` and email `resetLink` instead of
returning `devResetLink` in the JSON response).

## Project structure

```
src/main/java/com/marketbridge/
  MarketBridgeApplication.java   -> starts the app
  config/WebConfig.java          -> serves uploaded photos, allows API calls
  config/DataInitializer.java    -> creates the default admin account
  model/                         -> User, Product, Order (database tables)
  repository/                    -> auto-generated database queries
  controller/                    -> the REST API (what the frontend calls)
  util/PasswordUtil.java         -> password hashing

src/main/resources/
  application.properties         -> database connection settings
  static/                        -> all frontend pages (HTML/CSS/JS)

uploads/                         -> product photos are saved here at runtime
```

## How product photo upload works

When a seller adds a product, the form sends the text fields *and* the image
file together in one request (`multipart/form-data`) to
`POST /api/products`. The backend (`ProductController.saveImage`) saves the
photo into the `uploads/` folder with a random unique filename, and stores
the path (e.g. `/uploads/xxxx.jpg`) in the product's `imageUrl` field.
`WebConfig` then makes that folder visible to the browser at `/uploads/...`,
so `<img src="/uploads/xxxx.jpg">` works anywhere on the site.

## Pickup-only flow

Tuki-Taki does not offer delivery. Each order is collected from **University Central Pickup Point** at no delivery charge. Sellers accept an order, mark it ready for pickup, and the buyer confirms collection using their six-character pickup code.

## Hero banner + pickup point rebrand (latest update)

- **Hero is now a full-width photo banner** (the DIU campus shot, day/dusk
  swapping with the theme) with a dark gradient overlay and a floating
  glass "trust" card on the right (Buy from verified sellers / Pick up at
  Bonomaya / Safe & trusted) - matching the reference design instead of the
  previous side-by-side layout.
- **Pickup point renamed to "Bonomaya"** everywhere - the default in
  `MarketplaceSettings.java`, the fallback text in `buyer.js` / `seller.js`
  / `cart.js`, and the product page's "Shipping & Pickup" tab. Admins can
  still rename it any time from the Pickup settings tab.
- **Order confirmation now shows a real photo of Bonomaya** (compressed to
  `static/images/bonomaya-pickup-point.jpg`) in a small card instead of a
  plain text banner - shown after both "Buy now" and cart checkout succeed.

## Bug-fix pass (latest update)

A round of testing turned up several real bugs across the messaging, wishlist,
cart, and checkout features added on top of the design work. All of these
are now fixed:

- **Cart was shared between different accounts on the same browser.** The
  cart used one fixed `localStorage` key for everyone, so logging in as a
  second user on the same computer showed the first user's cart. Cart data
  is now stored per-user (`mb_cart_<userId>`, or `mb_cart_guest` before
  login) via shared `getCart()`/`saveCart()` helpers in `js/app.js`. If you
  add items before logging in, they're folded into your account's cart
  right after login instead of being lost.
- **Wishlist "Remove" didn't remove anything.** The repository's delete
  method is now an explicit `@Modifying @Transactional` query instead of
  relying on Spring Data's derived `deleteBy...` convention.
- **Messages: clicking a different conversation kept snapping back to the
  first one you opened.** A `wanted` conversation-id variable was never
  cleared, so every reload re-opened it. Now it's only used once.
- **"Follow seller" always showed as not-following**, even if you already
  followed them, because the status check used a plain `fetch()` instead of
  an authenticated one. Fixed on both the product page and the shop page.
- **Uploading more than 5 photos on a listing** threw an uncaught exception
  and showed a generic "Could not save product" instead of a real error
  message. Also added `server.error.include-message=always` so any future
  unexpected server error shows an actual message instead of a blank one.
- **Order confirmation page was static placeholder content** (it didn't
  even show your pickup code) and had a hardcoded test name ("somrat")
  baked into the navbar that any user or guest could see. It now reads the
  order you just placed and shows the real items, totals, and pickup codes.
- **The checkout page's "Your information" step (name/phone/email) was
  never actually saved anywhere** - sellers had no way to see a buyer's
  contact details. `Order` now stores `buyerName`/`buyerPhone`/
  `buyerContact`, and the seller's order table shows them.
- Removed the old single-item `POST /api/orders` endpoint - every order now
  goes through `/api/orders/checkout`, so there's one order-placing code
  path instead of two (the old one was no longer called by anything and
  was a maintenance trap).
- Fixed a broken CSS custom property (`var(--mint)`, never actually
  defined) used in 6 places across the newer account/shop/messages pages -
  it silently fell back to no color at all. Also removed a leftover
  hardcoded, non-theme-aware duplicate of the order-status pill colors
  that was overriding the correct dark-mode-aware version.
- De-duplicated a small `escapeHtml()` helper that had been copy-pasted
  into three different files - it now lives once in `js/app.js`.

**Known remaining technical debt (not bugs, but worth knowing about):** the
CSS file has some genuinely duplicated rule blocks from when the
messaging/shop/profile pages were built in two passes (search for "Phase 1"
and "PHASE 3-5" in `style.css`). They don't currently break anything - later
rules simply take precedence - but the file would benefit from a proper
cleanup pass merging them into one block per feature.

## Design system (light + dark theme)

The whole frontend was reskinned around a mint-green "campus marketplace" look,
defined entirely as CSS variables in `css/style.css` (`:root` = light,
`html[data-theme="dark"]` = dark). Every page automatically gets a theme
toggle in its navbar via `setupThemeControl()` in `js/app.js` - nothing
page-specific to wire up.

New/changed pages:
- `categories.html` (+ `js/categories.js`) - a new browse page with a sidebar
  (category, price range, condition filters), sort dropdown, and pagination.
  The home page (`index.html`) is now a lighter landing page (hero, category
  icon tiles, featured products) that links into it.
- `product-details.html` - redesigned with an image gallery, tabs
  (Description / Specifications / Reviews / Shipping & Pickup), and a seller
  info card, matching the reference screenshots.
- `seller-dashboard.html` - "My Products" is now a proper table ("My
  Listings") with a status pill per row instead of a card grid.
- Shared navbar across all pages: logo, wishlist/cart icon buttons with a
  live cart-count badge, and an avatar chip for the logged-in user.

Backend: `GET /api/products` gained one new optional filter, `condition`
(NEW/LIKE_NEW/GOOD/USED), used by the new categories page. Everything else
in this pass was frontend-only - no other endpoints changed.

**Update:** messaging, seller shop profiles with "Follow", admin analytics,
and the admin reports/moderation queue mentioned as not-done above have since
been built - see the "Current feature list" section near the top of this
file for what's actually in the project today.

## Navigation rework + animation pass (this update)

- **Removed `dashboard.html`.** It was an unnecessary extra click - its three
  links (Orders / Listings / Wishlist) now live directly in every page's
  navbar: **Home · Categories · Cart · Sell · Wishlist**. "Sell" opens
  `seller-dashboard.html`; a buyer's own orders moved under the avatar
  dropdown as **My Orders** (`buyer-dashboard.html`, which also gained a
  **Profile & Notifications** tab - the profile form and notification list
  that used to live on the deleted dashboard page).
- **Theme toggle** is now a small sliding ☀/☾ switch button instead of a
  light/dark/system dropdown - one click flips it.
- **Page-to-page transitions**: `@view-transition { navigation: auto; }` in
  `css/style.css` turns on the browser's native cross-document View
  Transitions, so navigating between pages crossfades smoothly in
  Chromium-based browsers (Chrome/Brave/Edge) instead of a hard cut. It's a
  progressive enhancement - browsers without support just navigate normally.
- **More animation throughout**: a subtle fade-up on page load, staggered
  entrance on the hero text, product image zoom on card hover, a heart "pop"
  when adding to wishlist, and scale/slide transitions on modals and the
  theme switch. Kept deliberately restrained - nothing spins or bounces
  without a reason. `prefers-reduced-motion` is respected everywhere.
- **Hero banner** now shows Daffodil International University's own campus
  photo instead of a generic stock image - the day shot in light mode, the
  dusk shot in dark mode (`static/images/campus-day.jpg` /
  `campus-dusk.jpg`, resized/compressed from the originals so the page still
  loads fast).

## Included MVP features

- One account can buy and sell; profile and local-verification support
- Search, filters, wishlist, cart, atomic multi-item pickup checkout, reviews
- Product condition, availability, preferred pickup time, and up to five images
- Seller rating summary, report listing flow, and order notifications
- Admin visibility moderation, user verification, report resolution, and editable pickup-point settings
- Forgot / reset password (see "Forgot password" section above)
- Light, Dark, and System display themes

## Notes

- This project keeps things intentionally simple: no Spring Security, no
  payment gateway, no complex service layers — just controllers talking
  directly to the database through Spring Data JPA. That keeps every file
  short enough to read top to bottom and explain in a viva.
- This was built and reviewed for correctness, but could not be
  compiled/tested inside this sandbox (no internet access to Maven's
  package repository here). Run `mvn spring-boot:run` on your own machine —
  if anything doesn't compile, paste the error back and it can be fixed
  quickly.
- `spring.jpa.hibernate.ddl-auto=update` will add the new `users` columns
  (`reset_token`, `reset_token_expiry`) automatically the next time you run
  the app against your existing database — no manual migration needed.
- The old "Pending Sellers" admin screen was removed: registration now
  always creates an auto-approved `USER` account, so a seller-approval
  queue never had anything in it.

## Before deploying this for real (not just local testing)

A few things in `application.properties` default to values that are fine for
local development but should be changed before this goes live on a public
domain:

- `spring.datasource.username` / `spring.datasource.password` - move these
  to environment variables instead of leaving real credentials in the file.
- `app.expose-dev-reset-link` - set to `false` once `spring.mail.*` is
  configured with a working mailbox. Left `true`, a failed email send
  exposes the password-reset link in the API response itself.
- `app.allowed-origins` - set to your real deployed domain instead of `*`.
- `app.base-url` - set to your real deployed domain (used to build links
  inside emails).
- The default admin account (`admin@marketbridge.com` / `admin123`, created
  once by `DataInitializer` if that email doesn't already exist) - log in
  and change this password immediately on a fresh deployment.
