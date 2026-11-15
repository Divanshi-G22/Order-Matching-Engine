package com.exchange.model;

public enum OrderType {
    LIMIT,   // trades at the given price or better, leftover rests in the book
    MARKET   // trades at any available price, leftover is cancelled
}