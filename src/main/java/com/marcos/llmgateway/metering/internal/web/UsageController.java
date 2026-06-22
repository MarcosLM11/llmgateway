package com.marcos.llmgateway.metering.internal.web;

import com.marcos.llmgateway.metering.internal.UsageQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/admin/usage")
class UsageController {

    private final UsageQueryService queryService;

    UsageController(UsageQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    UsageSummaryDTO getUsage(
            @RequestParam String tenantId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        var effectiveTo = to != null ? to : Instant.now();
        var effectiveFrom = from != null ? from : effectiveTo.minus(30, ChronoUnit.DAYS);
        return queryService.summarize(tenantId, effectiveFrom, effectiveTo);
    }
}
