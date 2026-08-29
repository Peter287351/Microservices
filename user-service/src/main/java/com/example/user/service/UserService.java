package com.example.user.service;

import com.example.common.api.BusinessException;
import com.example.common.api.ErrorCode;
import com.example.user.dto.UserCreateRequest;
import com.example.user.entity.User;
import com.example.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户业务逻辑层。
 * 业务规则（如"用户名不能重复"）写在这里，Controller 只负责接参和调 Service。
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    /** 构造器注入：Spring 自动把 UserRepository 的实例传进来（推荐写法） */
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public List<User> listAll() {
        return userRepository.findAll();
    }

    @Transactional
    public User create(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_DUPLICATED);
        }
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        return userRepository.save(user);
    }
}
