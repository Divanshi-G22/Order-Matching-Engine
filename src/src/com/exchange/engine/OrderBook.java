package com.exchange.engine;

import com.exchange.model.Order;
import com.exchange.model.Side;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class OrderBook {
    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();
    private final Map<Long, Order> index = new HashMap<>();

    TreeMap<Long, Deque<Order>> sideOf(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    public void add(Order o) {
        sideOf(o.side).computeIfAbsent(o.price, p -> new ArrayDeque<>()).addLast(o);
        index.put(o.id, o);
    }

    void forget(Order o) {
        index.remove(o.id);
    }

    public boolean cancel(long orderId) {
        Order o = index.remove(orderId);
        if (o == null) return false;
        TreeMap<Long, Deque<Order>> side = sideOf(o.side);
        Deque<Order> queue = side.get(o.price);
        if (queue != null) {
            queue.remove(o);
            if (queue.isEmpty()) side.remove(o.price);
        }
        o.cancel();
        return true;
    }

    public boolean contains(long orderId) {
        return index.containsKey(orderId);
    }

    public Long bestBid() { return bids.isEmpty() ? null : bids.firstKey(); }
    public Long bestAsk() { return asks.isEmpty() ? null : asks.firstKey(); }

    public int quantityAt(Side side, long price) {
        Deque<Order> q = sideOf(side).get(price);
        if (q == null) return 0;
        int total = 0;
        for (Order o : q) total += o.getRemaining();
        return total;
    }

    public String print() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-10s %-10s %s%n", "SIDE", "PRICE", "QTY (orders in time order)"));
        for (Map.Entry<Long, Deque<Order>> e : asks.descendingMap().entrySet())
            appendLevel(sb, "SELL", e);
        sb.append("---------- spread ----------\n");
        for (Map.Entry<Long, Deque<Order>> e : bids.entrySet())
            appendLevel(sb, "BUY", e);
        return sb.toString();
    }

    private void appendLevel(StringBuilder sb, String label, Map.Entry<Long, Deque<Order>> e) {
        int total = 0;
        StringBuilder ids = new StringBuilder();
        for (Order o : e.getValue()) {
            total += o.getRemaining();
            ids.append(" #").append(o.id).append("(").append(o.getRemaining()).append(")");
        }
        sb.append(String.format("%-10s %-10d %d  [%s ]%n", label, e.getKey(), total, ids));
    }
}