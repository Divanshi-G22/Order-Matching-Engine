package com.exchange.engine;

import com.exchange.model.Trade;

public interface EngineListener {
    void onTrade(Trade trade);
    void onBookChanged(String symbol);
}