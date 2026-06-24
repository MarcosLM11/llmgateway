package com.marcos.llmgateway.metering.internal;

import java.math.BigDecimal;

record PricingRule(BigDecimal promptPerMillion, BigDecimal completionPerMillion) {}