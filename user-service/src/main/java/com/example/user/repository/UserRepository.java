package com.example.user.repository;

import com.example.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据访问层。
 * 继承 JpaRepository 后自动拥有 save/findAll/findById 等方法，
 * 方法名按规则命名（如 findByUsername）Spring Data 会自动生成实现，不用写 SQL。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
