package com.example.productfilter.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {
    @GetMapping("/login")
    public String login() {
        return "login";  // вернёт templates/login.html
    }
}
