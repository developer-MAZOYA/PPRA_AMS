package tz.ppra.ppraAMS.service;

import tz.ppra.ppraAMS.model.AuthRequest;
import tz.ppra.ppraAMS.model.User;

import java.util.List;
import java.util.Optional;

public interface UserService {

    User register(User user);

    String verify(AuthRequest request);

    Optional<User> findByEmail(String email);

    Optional<User> findById(long id);

    List<User> list();

    User updateUser(long id, User user);

    User toggleUserStatus(long id);

    void changePassword(String authHeader, String currentPassword, String newPassword);
}
