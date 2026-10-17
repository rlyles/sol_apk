package com.rlyles.wep.shell;

import android.content.Context;

import com.rlyles.wep.games.BlackJack;
import com.rlyles.wep.games.ChipsChallenge;
import com.rlyles.wep.games.Cruel;
import com.rlyles.wep.games.FreeCell;
import com.rlyles.wep.games.Golf;
import com.rlyles.wep.games.JezzBall;
import com.rlyles.wep.games.Minesweeper;
import com.rlyles.wep.games.Pegged;
import com.rlyles.wep.games.PipeDream;
import com.rlyles.wep.games.Reversi;
import com.rlyles.wep.games.RodentsRevenge;
import com.rlyles.wep.games.SkiFree;
import com.rlyles.wep.games.Snake;
import com.rlyles.wep.games.Taipei;
import com.rlyles.wep.games.TetraVex;
import com.rlyles.wep.games.Tetris;
import com.rlyles.wep.games.TicTactics;
import com.rlyles.wep.games.Solitaire;
import com.rlyles.wep.games.TriPeaks;
import com.rlyles.wep.games.TutsTomb;
import com.rlyles.wep.win.WinApp;

import java.util.ArrayList;
import java.util.List;

/** Every program on the desktop and in the Start menu. */
public final class Apps {
    public interface Factory {
        WinApp create(Context context, WinApp.Host host);
    }

    public static final class Info {
        public final String id, title;
        public final Factory factory;

        Info(String id, String title, Factory factory) {
            this.id = id;
            this.title = title;
            this.factory = factory;
        }
    }

    public static final List<Info> ALL = new ArrayList<>();

    static {
        ALL.add(new Info("sol", "Solitaire", Solitaire::new));
        ALL.add(new Info("freecell", "FreeCell", FreeCell::new));
        ALL.add(new Info("golf", "Golf", Golf::new));
        ALL.add(new Info("tripeaks", "TriPeaks", TriPeaks::new));
        ALL.add(new Info("tutstomb", "Tut's Tomb", TutsTomb::new));
        ALL.add(new Info("cruel", "Cruel", Cruel::new));
        ALL.add(new Info("blackjack", "BlackJack", BlackJack::new));
        ALL.add(new Info("winmine", "Minesweeper", Minesweeper::new));
        ALL.add(new Info("reversi", "Reversi", Reversi::new));
        ALL.add(new Info("taipei", "Taipei", Taipei::new));
        ALL.add(new Info("tetravex", "TetraVex", TetraVex::new));
        ALL.add(new Info("pegged", "Pegged", Pegged::new));
        ALL.add(new Info("tictac", "TicTactics", TicTactics::new));
        ALL.add(new Info("tetris", "Tetris", Tetris::new));
        ALL.add(new Info("jezzball", "JezzBall", JezzBall::new));
        ALL.add(new Info("pipe", "Pipe Dream", PipeDream::new));
        ALL.add(new Info("rodent", "Rodent's Revenge", RodentsRevenge::new));
        ALL.add(new Info("ski", "SkiFree", SkiFree::new));
        ALL.add(new Info("snake", "Snake", Snake::new));
        ALL.add(new Info("chips", "Chip's Challenge", ChipsChallenge::new));
    }

    private Apps() {
    }

    public static Info find(String id) {
        for (Info i : ALL) {
            if (i.id.equals(id)) {
                return i;
            }
        }
        return null;
    }
}
