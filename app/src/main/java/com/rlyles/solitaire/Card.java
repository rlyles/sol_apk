package com.rlyles.solitaire;

/** One playing card. Suits follow the cards.dll bitmap order: clubs, diamonds, hearts, spades. */
public final class Card {
    public static final int CLUBS = 0, DIAMONDS = 1, HEARTS = 2, SPADES = 3;

    public final int suit;
    /** 1 = Ace ... 13 = King. */
    public final int rank;
    public boolean faceUp;

    Card(int suit, int rank) {
        this.suit = suit;
        this.rank = rank;
    }

    /** 0..51, also the cards.dll face bitmap id minus one. */
    public int id() {
        return suit * 13 + rank - 1;
    }

    public boolean isRed() {
        return suit == DIAMONDS || suit == HEARTS;
    }

    @Override
    public String toString() {
        return "A23456789TJQK".charAt(rank - 1) + String.valueOf("CDHS".charAt(suit)) + (faceUp ? "" : "*");
    }
}
