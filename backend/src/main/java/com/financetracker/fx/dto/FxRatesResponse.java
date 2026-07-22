package com.financetracker.fx.dto;

import java.util.List;

/**
 * The user's rate table plus the anchor it is read against. {@code baseCurrency} is echoed so the
 * client never has to infer the anchor from a separate settings call to render "1 EUR = 4.30 PLN".
 */
public record FxRatesResponse(String baseCurrency, List<FxRateResponse> rates) {}
