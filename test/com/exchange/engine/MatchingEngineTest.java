package com.exchange.engine;

import com.exchange.exception.InvalidOrderException;
import com.exchange.exception.OrderNotFoundException;
import com.exchange.model.Order;
import com.exchange.model.OrderStatus;
import com.exchange.model.Side;
import com.exchange.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {
    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
    }

    @Test
    void exactMatch() {
        engine.submit(Order.limit("INFY", Side.SELL, 100, 50));
        Order buy = Order.limit("INFY", Side.BUY, 100, 50);
        List<Trade> trades = engine.submit(buy);

        assertEquals(1, trades.size());
        assertEquals(50, trades.get(0).getQuantity());
        assertEquals(100, trades.get(0).getPrice());
        assertEquals(OrderStatus.FILLED, buy.getStatus());
        assertNull(engine.getBook("INFY").bestAsk());
        assertNull(engine.getBook("INFY").bestBid());
    }

    @Test
    void partialFillLeavesRemainderResting() {
        engine.submit(Order.limit("INFY", Side.SELL, 100, 50));
        Order buy = Order.limit("INFY", Side.BUY, 100, 80);
        List<Trade> trades = engine.submit(buy);

        assertEquals(1, trades.size());
        assertEquals(50, trades.get(0).getQuantity());
        assertEquals(30, buy.getRemaining());
        assertEquals(OrderStatus.PARTIALLY_FILLED, buy.getStatus());
        assertEquals(Long.valueOf(100), engine.getBook("INFY").bestBid());
        assertEquals(30, engine.getBook("INFY").quantityAt(Side.BUY, 100));
    }

    @Test
    void oneOrderSweepsMultiplePriceLevels() {
        engine.submit(Order.limit("INFY", Side.SELL, 101, 50));
        engine.submit(Order.limit("INFY", Side.SELL, 101, 30));
        engine.submit(Order.limit("INFY", Side.SELL, 102, 40));

        List<Trade> trades = engine.submit(Order.limit("INFY", Side.BUY, 102, 100));

        assertEquals(3, trades.size());
        assertEquals(50, trades.get(0).getQuantity());
        assertEquals(101, trades.get(0).getPrice());
        assertEquals(30, trades.get(1).getQuantity());
        assertEquals(101, trades.get(1).getPrice());
        assertEquals(20, trades.get(2).getQuantity());
        assertEquals(102, trades.get(2).getPrice());
        assertEquals(20, engine.getBook("INFY").quantityAt(Side.SELL, 102));
    }

    @Test
    void earlierOrderAtSamePriceFillsFirst() {
        Order first = Order.limit("INFY", Side.SELL, 100, 10);
        Order second = Order.limit("INFY", Side.SELL, 100, 10);
        engine.submit(first);
        engine.submit(second);

        List<Trade> trades = engine.submit(Order.limit("INFY", Side.BUY, 100, 10));

        assertEquals(1, trades.size());
        assertEquals(first.id, trades.get(0).getSellOrderId());
        assertEquals(10, second.getRemaining());
    }

    @Test
    void tradeExecutesAtRestingOrderPrice() {
        engine.submit(Order.limit("INFY", Side.SELL, 100, 10));
        List<Trade> trades = engine.submit(Order.limit("INFY", Side.BUY, 105, 10));

        assertEquals(1, trades.size());
        assertEquals(100, trades.get(0).getPrice());
    }

    @Test
    void nonCrossingOrdersRestWithoutTrading() {
        engine.submit(Order.limit("INFY", Side.SELL, 101, 10));
        List<Trade> trades = engine.submit(Order.limit("INFY", Side.BUY, 99, 10));

        assertTrue(trades.isEmpty());
        assertEquals(Long.valueOf(99), engine.getBook("INFY").bestBid());
        assertEquals(Long.valueOf(101), engine.getBook("INFY").bestAsk());
    }

    @Test
    void marketOrderFillsAtRestingPrice() {
        engine.submit(Order.limit("INFY", Side.SELL, 100, 10));
        Order market = Order.market("INFY", Side.BUY, 10);
        List<Trade> trades = engine.submit(market);

        assertEquals(1, trades.size());
        assertEquals(100, trades.get(0).getPrice());
        assertEquals(OrderStatus.FILLED, market.getStatus());
    }

    @Test
    void marketOrderOnEmptyBookIsCancelled() {
        Order market = Order.market("INFY", Side.BUY, 10);
        List<Trade> trades = engine.submit(market);

        assertTrue(trades.isEmpty());
        assertEquals(OrderStatus.CANCELLED, market.getStatus());
    }

    @Test
    void cancelledOrderNeverTrades() {
        Order sell = Order.limit("INFY", Side.SELL, 100, 10);
        engine.submit(sell);
        engine.cancel(sell.id);

        List<Trade> trades = engine.submit(Order.limit("INFY", Side.BUY, 100, 10));

        assertTrue(trades.isEmpty());
        assertEquals(OrderStatus.CANCELLED, sell.getStatus());
    }

    @Test
    void cancellingUnknownOrderThrows() {
        assertThrows(OrderNotFoundException.class, () -> engine.cancel(9999));
    }

    @Test
    void invalidOrdersAreRejected() {
        assertThrows(InvalidOrderException.class, () -> Order.limit("INFY", Side.BUY, 100, 0));
        assertThrows(InvalidOrderException.class, () -> Order.limit("INFY", Side.BUY, -5, 10));
        assertThrows(InvalidOrderException.class, () -> Order.limit("", Side.BUY, 100, 10));
    }

    @Test
    void differentSymbolsDoNotInterfere() {
        engine.submit(Order.limit("INFY", Side.SELL, 100, 10));
        List<Trade> trades = engine.submit(Order.limit("TCS", Side.BUY, 100, 10));

        assertTrue(trades.isEmpty());
        assertEquals(Long.valueOf(100), engine.getBook("INFY").bestAsk());
        assertEquals(Long.valueOf(100), engine.getBook("TCS").bestBid());
    }
}