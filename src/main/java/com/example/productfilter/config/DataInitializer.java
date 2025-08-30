package com.example.productfilter.config;

import com.example.productfilter.model.*;
import com.example.productfilter.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedUsers(RoleRepository roles, UserAccountRepository users, PasswordEncoder enc) {
        return args -> {
            Role adminRole = roles.findByName("ROLE_ADMIN").orElseGet(() -> roles.save(new Role("ROLE_ADMIN")));
            Role userRole  = roles.findByName("ROLE_USER").orElseGet(() -> roles.save(new Role("ROLE_USER")));

            if (!users.existsByUsername("admin")) {
                UserAccount admin = new UserAccount();
                admin.setUsername("admin");
                admin.setPassword(enc.encode("admin")); // поменяйте в проде
                admin.setRoles(Set.of(adminRole, userRole));
                users.save(admin);
            }
            if (!users.existsByUsername("user")) {
                UserAccount user = new UserAccount();
                user.setUsername("user");
                user.setPassword(enc.encode("user"));
                user.setRoles(Set.of(userRole));
                users.save(user);
            }
        };
    }
}
