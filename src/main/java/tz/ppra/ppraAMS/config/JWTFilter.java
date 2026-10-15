package tz.ppra.ppraAMS.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tz.ppra.ppraAMS.service.JWTService;
import java.io.IOException;
import java.util.List;

@Component
public class JWTFilter extends OncePerRequestFilter {

    private final JWTService jwtService;

    public JWTFilter(JWTService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = null;
        String email = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);

            if (jwtService.validateToken(token) && !jwtService.isTokenExpired(token)) {
                email = jwtService.extractEmail(token);

                // Extract user details from token
                Long userId = jwtService.extractUserId(token);
                String fullName = jwtService.extractFullName(token);
                String role = jwtService.extractRole(token);

                // Create UserDetails from token claims
                UserDetails userDetails = createUserDetailsFromToken(email, userId, fullName, role);

                if (userDetails != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities()
                            );
                    authToken.setDetails(userDetails);
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private UserDetails createUserDetailsFromToken(String email, Long userId, String fullName, String role) {
        if (email == null || userId == null || role == null) {
            return null;
        }

        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + role)
        );

        return new TokenUserDetails(userId, email, fullName, role, authorities);
    }

    // Custom UserDetails implementation to hold token data
    private static class TokenUserDetails implements UserDetails {
        private final Long userId;
        private final String email;
        private final String fullName;
        private final String role;
        private final List<GrantedAuthority> authorities;

        public TokenUserDetails(Long userId, String email, String fullName, String role,
                                List<GrantedAuthority> authorities) {
            this.userId = userId;
            this.email = email;
            this.fullName = fullName;
            this.role = role;
            this.authorities = authorities;
        }

        // Custom getters for user details
        public Long getUserId() {
            return userId;
        }

        public String getFullName() {
            return fullName;
        }

        public String getRole() {
            return role;
        }

        public String getEmail() {
            return email;
        }

        // Spring Security UserDetails methods
        @Override
        public String getUsername() {
            return email;
        }

        @Override
        public String getPassword() {
            return null;
        } // No password in token

        @Override
        public List<GrantedAuthority> getAuthorities() {
            return authorities;
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}
