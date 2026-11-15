package com.exchange.model;

import java.time.LocalDateTime;

public final class Trade {
    private static long nextId = 1;

    private final long tradeId;
    private final long buyOrderId;
    private final long sellOrderId;
    private final String symbol;
    private final long price;
    private final int quantity;
    private final LocalDateTime timestamp;

    public Trade(Order buy, Order sell, long price, int quantity) {
        this.tradeId = nextId++;
        this.buyOrderId = buy.id;
        this.sellOrderId = sell.id;
        this.symbol = buy.symbol;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = LocalDateTime.now();
    }

    public long getTradeId() { return tradeId; }
    public long getBuyOrderId() { return buyOrderId; }
    public long getSellOrderId() { return sellOrderId; }
    public String getSymbol() { return symbol; }
    public long getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "Trade#" + tradeId + " " + symbol + " " + quantity + " @ " + price
                + " (buy #" + buyOrderId + ", sell #" + sellOrderId + ")";
    }
}