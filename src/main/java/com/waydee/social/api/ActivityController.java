package com.waydee.social.api;

import com.waydee.social.api.dto.ActivityDtos.ActivityResponse;
import com.waydee.social.application.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Activity", description = "Son hareketler akışı")
@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @Operation(summary = "Son hareketler (satın alma / paylaşım / etkinlik)")
    @GetMapping
    public List<ActivityResponse> recent(@RequestParam(defaultValue = "30") int limit,
                                         @RequestParam(defaultValue = "0") int withinMinutes) {
        return activityService.recent(limit, withinMinutes);
    }
}
