package tz.ppra.ppraAMS.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tz.ppra.ppraAMS.service.JWTService;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JWTService jwtService;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        String email = oauth2User.getAttribute("email");
        String fullName = oauth2User.getAttribute("localFullName");
        Long userId = oauth2User.getAttribute("localUserId");
        String role = oauth2User.getAttribute("localRole");

        String token = jwtService.generateToken(email, userId, fullName, role);

        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);

        String redirectUrl = frontendUrl + "/oauth2/redirect?token=" + encodedToken;

        response.sendRedirect(redirectUrl);
    }
}
