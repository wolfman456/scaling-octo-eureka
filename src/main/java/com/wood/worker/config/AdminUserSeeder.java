package com.wood.worker.config;

import com.wood.worker.service.AdminUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminUserSeeder implements ApplicationRunner {

    private final AdminUserService users;
    private final String username;
    private final String password;

    public AdminUserSeeder(AdminUserService users,
                           @Value("${app.admin.user}") String username,
                           @Value("${app.admin.password}") String password) {
        this.users = users;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        users.seedIfEmpty(username, password);
    }
}