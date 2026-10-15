package tz.ppra.ppraAMS.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import tz.ppra.ppraAMS.model.AuthRequest;
import tz.ppra.ppraAMS.model.User;
import tz.ppra.ppraAMS.model.UserRoles;
import tz.ppra.ppraAMS.service.UserService;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;


    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> users() {
        return userService.list();
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public User user(@Argument("id") String id) {
        return userService.findById(Long.parseLong(id)).orElse(null);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public User userByEmail(@Argument("email") String email) {
        return userService.findByEmail(email).orElse(null);
    }


    @MutationMapping
    public User register(@Argument("input") UserInput input) {
        User user = User.builder()
                .fullName(input.fullName())
                .email(input.email())
                .password(input.password())
                .build();
        return userService.register(user);
    }

    @MutationMapping
    public String login(@Argument("email") String email,
                        @Argument("password") String password) {
        AuthRequest req = new AuthRequest();
        req.setEmail(email);
        req.setPassword(password);
        return userService.verify(req);
    }


    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public boolean changePassword(@Argument("authHeader") String authHeader,
                                  @Argument("currentPassword") String currentPassword,
                                  @Argument("newPassword") String newPassword) {
        userService.changePassword(authHeader, currentPassword, newPassword);
        return true;
    }


    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public User adminCreateUser(@Argument("input") AdminUserInput input) {
        UserRoles role = parseRole(input.role());

        User user = User.builder()
                .fullName(input.fullName())
                .email(input.email())
                .password(input.password())
                .role(role)
                .active(input.active() != null ? input.active() : true)
                .build();
        return userService.register(user);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public User adminUpdateUser(@Argument("id") String id,
                                @Argument("input") AdminUserInput input) {
        UserRoles role = parseRole(input.role());

        User patch = User.builder()
                .fullName(input.fullName())
                .email(input.email())
                .password(input.password())
                .role(role)
                .active(input.active() != null ? input.active() : true)
                .build();
        return userService.updateUser(Long.parseLong(id), patch);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public User toggleUserStatus(@Argument("id") String id) {
        return userService.toggleUserStatus(Long.parseLong(id));
    }

    private UserRoles parseRole(String role) {
        if (role == null || role.isBlank()) {
            return UserRoles.USER;
        }
        try {
            return UserRoles.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UserRoles.USER;
        }
    }

    public record UserInput(String fullName, String email, String password) {}

    public record AdminUserInput(
            String fullName,
            String email,
            String password,
            String role,
            Boolean active
    ) {}
}
