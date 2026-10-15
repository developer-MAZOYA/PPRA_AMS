package tz.ppra.ppraAMS.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import tz.ppra.ppraAMS.model.User;
import tz.ppra.ppraAMS.model.UserRoles;
import tz.ppra.ppraAMS.repo.UserRepo;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);

        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Email not returned by OAuth2 provider");
        }

        User user = userRepo.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .fullName(name != null && !name.isBlank() ? name : email)
                    .email(email)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .active(true)
                    .role(UserRoles.USER)
                    .build();
            return userRepo.save(newUser);
        });

        if (!user.isActive()) {
            throw new OAuth2AuthenticationException("User account is disabled");
        }

        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());
        attributes.put("localUserId", user.getId());
        attributes.put("localRole", user.getRole());
        attributes.put("localFullName", user.getFullName());

        return new DefaultOAuth2User(
                oauth2User.getAuthorities(),
                attributes,
                "email"
        );
    }
}
