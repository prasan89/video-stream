package com.videocloud.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {
    public record PlanResponse(String code, String name, long monthlyPricePaise,
                               long storageLimitBytes, int videoLimit) {}

    @GetMapping
    public List<PlanResponse> listPlans() {
        // Demonstration catalog only. Replace with DB-backed active plan records before launch.
        return List.of(
            new PlanResponse("FREE", "Free", 0L, 2L * 1024 * 1024 * 1024, 5),
            new PlanResponse("STARTER", "Starter", 49900L, 50L * 1024 * 1024 * 1024, 25),
            new PlanResponse("PRO", "Pro", 149900L, 250L * 1024 * 1024 * 1024, 200),
            new PlanResponse("BUSINESS", "Business", 499900L, 1024L * 1024 * 1024 * 1024, 1000)
        );
    }
}
