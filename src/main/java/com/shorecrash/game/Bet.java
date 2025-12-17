package com.shorecrash.game;

import java.util.UUID;

public class Bet {
    public enum Status { ACTIVE, CASHED_OUT, LOST }

    private final UUID playerId;
    private final String playerName;
    private double amount;
    private Status status;
    private double cashoutMultiplier;
    private double payout;

    public Bet(UUID playerId, String playerName, double amount) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.amount = amount;
        this.status = Status.ACTIVE;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public Status getStatus() {
        return status;
    }

    public double getCashoutMultiplier() {
        return cashoutMultiplier;
    }

    public double getPayout() {
        return payout;
    }

    public void markCashed(double multiplier, double payout) {
        this.status = Status.CASHED_OUT;
        this.cashoutMultiplier = multiplier;
        this.payout = payout;
    }

    public void markLost() {
        this.status = Status.LOST;
    }
}
