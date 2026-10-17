package com.rlyles.wep.cards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Fresh shuffled decks for the simpler card games. */
public final class Deck {
    private Deck() {
    }

    /** All 52 cards, face up, in id order. */
    public static Card[] ordered() {
        Card[] cards = new Card[52];
        for (int s = 0; s < 4; s++) {
            for (int r = 1; r <= 13; r++) {
                Card c = new Card(s, r);
                c.faceUp = true;
                cards[s * 13 + r - 1] = c;
            }
        }
        return cards;
    }

    public static List<Card> shuffled(Random rnd) {
        List<Card> list = new ArrayList<>();
        Collections.addAll(list, ordered());
        Collections.shuffle(list, rnd);
        return list;
    }

    /** Serialises piles of cards as id lists: "1,2,3;4,5;;" */
    public static String save(List<List<Card>> piles) {
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < piles.size(); p++) {
            if (p > 0) {
                sb.append(';');
            }
            List<Card> pile = piles.get(p);
            for (int i = 0; i < pile.size(); i++) {
                Card c = pile.get(i);
                sb.append(i > 0 ? "," : "").append(c.faceUp ? c.id() : c.id() + 100);
            }
        }
        return sb.toString();
    }

    /** Parses {@link #save} output against a deck from {@link #ordered}; null if malformed. */
    public static List<List<Card>> load(String s, Card[] deck, int pileCount) {
        if (s == null) {
            return null;
        }
        String[] parts = s.split(";", -1);
        if (parts.length != pileCount) {
            return null;
        }
        List<List<Card>> out = new ArrayList<>();
        boolean[] seen = new boolean[52];
        try {
            for (String part : parts) {
                List<Card> pile = new ArrayList<>();
                if (!part.isEmpty()) {
                    for (String id : part.split(",")) {
                        int code = Integer.parseInt(id);
                        int cid = code % 100;
                        if (cid < 0 || cid >= 52 || seen[cid] || code >= 200) {
                            return null;
                        }
                        seen[cid] = true;
                        Card c = deck[cid];
                        c.faceUp = code < 100;
                        pile.add(c);
                    }
                }
                out.add(pile);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    public static List<List<Card>> copy(List<List<Card>> piles) {
        List<List<Card>> out = new ArrayList<>();
        for (List<Card> p : piles) {
            out.add(new ArrayList<>(p));
        }
        return out;
    }
}
