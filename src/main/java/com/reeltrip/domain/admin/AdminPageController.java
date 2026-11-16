package com.reeltrip.domain.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import io.swagger.v3.oas.annotations.Hidden;

@Controller
@Hidden
public class AdminPageController {
    @GetMapping({"/admin", "/admin/"})
    public String page() { return "redirect:/admin/index.html"; }
}
