package com.exchange.io;

import com.exchange.model.Trade;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Writes every trade to a CSV file. The file is recreated each time the program starts. */
public class TradeLogger {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final Path file;

    public TradeLogger(String fileName) {
        this.file = Paths.get(fileName);
        try {
            Files.write(file,
                    "tradeId,timestamp,symbol,buyOrderId,sellOrderId,price,quantity\n".getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create trade log " + fileName, e);
        }
    }

    public void log(List<Trade> trades) {
        if (trades.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Trade t : trades) {
            sb.append(t.getTradeId()).append(',')
              .append(t.getTimestamp().format(FMT)).append(',')
              .append(t.getSymbol()).append(',')
              .append(t.getBuyOrderId()).append(',')
              .append(t.getSellOrderId()).append(',')
              .append(t.getPrice()).append(',')
              .append(t.getQuantity()).append('\n');
        }
        try {
            Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write to trade log", e);
        }
    }
}