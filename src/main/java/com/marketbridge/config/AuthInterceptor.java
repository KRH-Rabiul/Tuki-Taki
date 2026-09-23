package com.marketbridge.config;

import com.marketbridge.model.User;
import com.marketbridge.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

// Runs before every /api/** request.
// Reads the "Authorization: Bearer <token>" header, finds the matching logged-in
// user, and attaches it to the request as "currentUser" so controllers know
// WHO is calling (needed for ownership checks like "only this seller can edit
// this product").
//
// Public routes (browsing products, register, login) don't need a token at all.
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // ---- Public routes: no login required ----
        // NOTE: "public" only means a missing/invalid token is not rejected.
        // Several of these routes (follow status, seller-profile, reviews)
        // return a *different* result when the caller happens to be logged in
        // (e.g. "am I already following this seller?"), so we still try to
        // read the token below and attach currentUser when it's valid -
        // we just don't reject the request when it's absent.
        boolean isPublic =
                path.startsWith("/api/auth/")
                || (path.startsWith("/api/products") && "GET".equals(method)) // browsing is public
                || (path.equals("/api/marketplace/settings") && "GET".equals(method))
                || (path.matches("/api/users/\\d+/seller-profile") && "GET".equals(method))
                || (path.matches("/api/follows/seller/\\d+") && "GET".equals(method))
                || (path.matches("/api/reviews/product/\\d+") && "GET".equals(method)) // reviews are public browsing info too
                || (path.equals("/api/community/posts") && "GET".equals(method))
                || (path.matches("/api/community/posts/\\d+") && "GET".equals(method));

        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;

        if (token != null) {
            Optional<User> userOpt = userRepository.findByToken(token);
            if (userOpt.isPresent()) {
                request.setAttribute("currentUser", userOpt.get());
            } else if (!isPublic) {
                return reject(response, "Session expired, please log in again");
            }
            // For public routes, an invalid/expired token is simply ignored
            // (treated the same as not being logged in) instead of blocking access.
        } else if (!isPublic) {
            return reject(response, "Login required");
        }

        return true;
    }

    private boolean reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
        return false;
    }
}
