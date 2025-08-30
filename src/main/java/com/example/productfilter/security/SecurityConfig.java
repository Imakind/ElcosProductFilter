package com.example.productfilter.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.web.SecurityFilterChain;

// security/SecurityConfig.java
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public DaoAuthenticationProvider authProvider(AppUserDetailsService uds, BCryptPasswordEncoder enc) {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider();
        p.setUserDetailsService(uds);
        p.setPasswordEncoder(enc);
        return p;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AppUserDetailsService uds) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // у вас много fetch POST — так проще
                .authorizeHttpRequests(auth -> auth
                        // статика и страница логина — всем
                        .requestMatchers("/css/**","/js/**","/images/**","/webjars/**","/favicon.ico").permitAll()
                        .requestMatchers("/login").permitAll()

                        // админка — только ADMIN
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // ВСЁ ОСТАЛЬНОЕ — только после входа
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")                 // кастомная страница логина
                        .loginProcessingUrl("/login")        // форма постит сюда (по умолчанию так и есть)
                        .defaultSuccessUrl("/", false)       // вернёт туда, куда просился (SavedRequest). false — не форсить /
                        .failureUrl("/login?error")          // при ошибке логина
                        .permitAll()
                )
                .rememberMe(me -> me
                        .key("change-this-remember-me-key")  // поменяй ключ
                        .tokenValiditySeconds(60*60*24*30)   // 30 дней
                        .userDetailsService(uds)
                )
                .logout(log -> log
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .deleteCookies("JSESSIONID","remember-me")
                        .permitAll()
                );

        return http.build();
    }
}
