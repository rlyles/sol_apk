package com.rlyles.wep.win;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Original 32x32-style icons for every app and shell item, drawn with shapes on a 32-unit grid. */
public final class Icons {
    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint S = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        S.setStyle(Paint.Style.STROKE);
        S.setStrokeCap(Paint.Cap.ROUND);
        S.setStrokeJoin(Paint.Join.ROUND);
    }

    private Icons() {
    }

    public static void draw(Canvas c, String id, float x, float y, float size) {
        c.save();
        c.translate(x, y);
        c.scale(size / 32f, size / 32f);
        S.setStrokeWidth(1.2f);
        switch (id) {
            case "sol": sol(c); break;
            case "freecell": freecell(c); break;
            case "golf": golf(c); break;
            case "tripeaks": tripeaks(c); break;
            case "tutstomb": tutstomb(c); break;
            case "cruel": cruel(c); break;
            case "blackjack": blackjack(c); break;
            case "winmine": winmine(c); break;
            case "reversi": reversi(c); break;
            case "taipei": taipei(c); break;
            case "tetravex": tetravex(c); break;
            case "pegged": pegged(c); break;
            case "tictac": tictac(c); break;
            case "tetris": tetris(c); break;
            case "jezzball": jezzball(c); break;
            case "pipe": pipe(c); break;
            case "rodent": rodent(c); break;
            case "ski": ski(c); break;
            case "snake": snake(c); break;
            case "chips": chips(c); break;
            case "display": display(c); break;
            case "mycomputer": computer(c); break;
            case "recycle": recycle(c); break;
            case "help": help(c); break;
            case "shutdown": shutdown(c); break;
            case "settings": settings(c); break;
            case "folder": folder(c, 0xFFFFE070); break;
            case "games": folder(c, 0xFFFFE070); card(c, 14, 12, 10, 13, true, 0xFFFF0000); break;
            default: folder(c, 0xFFC0C0C0); break;
        }
        c.restore();
    }

    // ---------------------------------------------------------------- helpers

    private static void rect(Canvas c, float l, float t, float r, float b, int color) {
        P.setColor(color);
        c.drawRect(l, t, r, b, P);
    }

    private static void outline(Canvas c, float l, float t, float r, float b, int color) {
        S.setColor(color);
        c.drawRect(l, t, r, b, S);
    }

    private static void circle(Canvas c, float cx, float cy, float r, int color) {
        P.setColor(color);
        c.drawCircle(cx, cy, r, P);
    }

    private static void line(Canvas c, float x0, float y0, float x1, float y1, int color, float w) {
        S.setColor(color);
        S.setStrokeWidth(w);
        c.drawLine(x0, y0, x1, y1, S);
        S.setStrokeWidth(1.2f);
    }

    /** A small playing card: a blue back, or a white face with a pip of {@code pip} colour. */
    private static void card(Canvas c, float x, float y, float w, float h, boolean face, int pip) {
        rect(c, x, y, x + w, y + h, face ? 0xFFFFFFFF : 0xFF0000B0);
        if (!face) {
            S.setColor(0xFF7080FF);
            S.setStrokeWidth(0.6f);
            c.save();
            c.clipRect(x, y, x + w, y + h);
            for (float d = -h; d < w; d += 2.5f) {
                c.drawLine(x + d, y, x + d + h, y + h, S);
                c.drawLine(x + d, y + h, x + d + h, y, S);
            }
            c.restore();
            S.setStrokeWidth(1.2f);
        } else if (pip != 0) {
            diamond(c, x + w / 2, y + h / 2, w * 0.28f, pip);
        }
        outline(c, x, y, x + w, y + h, 0xFF000000);
    }

    private static void diamond(Canvas c, float cx, float cy, float r, int color) {
        Path p = new Path();
        p.moveTo(cx, cy - r * 1.3f);
        p.lineTo(cx + r, cy);
        p.lineTo(cx, cy + r * 1.3f);
        p.lineTo(cx - r, cy);
        p.close();
        P.setColor(color);
        c.drawPath(p, P);
    }

    private static void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        P.setColor(color);
        P.setTextSize(size);
        P.setFakeBoldText(bold);
        P.setTextAlign(Paint.Align.CENTER);
        c.drawText(s, x, y, P);
        P.setFakeBoldText(false);
    }

    // ---------------------------------------------------------------- games

    private static void sol(Canvas c) {
        card(c, 3, 4, 15, 21, false, 0);
        card(c, 13, 8, 15, 21, true, 0xFFFF0000);
    }

    private static void freecell(Canvas c) {
        outline(c, 2, 2, 12, 15, 0xFF004000);
        outline(c, 20, 2, 30, 15, 0xFF004000);
        card(c, 9, 9, 15, 20, true, 0);
        circle(c, 16.5f, 15, 3, 0xFFFFD0A0);
        Path crown = new Path();
        crown.moveTo(13, 12);
        crown.lineTo(14, 8);
        crown.lineTo(16.5f, 10.5f);
        crown.lineTo(19, 8);
        crown.lineTo(20, 12);
        crown.close();
        P.setColor(0xFFFFD000);
        c.drawPath(crown, P);
        rect(c, 12, 18, 21, 27, 0xFFD02020);
        rect(c, 15.5f, 18, 17.5f, 27, 0xFFFFD000);
    }

    private static void golf(Canvas c) {
        rect(c, 1, 22, 31, 31, 0xFF00A000);
        card(c, 3, 6, 12, 17, true, 0xFF000000);
        line(c, 22, 6, 22, 26, 0xFF000000, 1.2f);
        Path flag = new Path();
        flag.moveTo(22, 6);
        flag.lineTo(30, 9);
        flag.lineTo(22, 12);
        flag.close();
        P.setColor(0xFFFF0000);
        c.drawPath(flag, P);
        circle(c, 25, 26, 1.6f, 0xFFFFFFFF);
    }

    private static void tripeaks(Canvas c) {
        float[][] pos = {{4, 4}, {13, 4}, {22, 4}, {1.5f, 11}, {8.5f, 11}, {17.5f, 11}, {24.5f, 11},
                {0, 18}, {7, 18}, {14, 18}, {21, 18}};
        for (int i = 0; i < pos.length; i++) {
            card(c, pos[i][0] + 1, pos[i][1], 7, 10, i >= 7, i >= 7 ? 0xFFFF0000 : 0);
        }
    }

    private static void tutstomb(Canvas c) {
        Path p = new Path();
        p.moveTo(16, 2);
        p.lineTo(31, 29);
        p.lineTo(1, 29);
        p.close();
        P.setColor(0xFFE0B040);
        c.drawPath(p, P);
        S.setColor(0xFF805000);
        c.drawPath(p, S);
        for (int row = 1; row < 4; row++) {
            float y = 2 + row * 6.75f;
            line(c, 16 - row * 3.75f, y, 16 + row * 3.75f, y, 0xFF805000, 0.8f);
        }
        rect(c, 14, 22, 18, 29, 0xFF402000);
    }

    private static void cruel(Canvas c) {
        for (int i = 0; i < 4; i++) {
            card(c, 2 + (i % 2) * 15, 2 + (i / 2) * 15, 13, 13, true, i % 2 == 0 ? 0xFFFF0000 : 0xFF000000);
        }
    }

    private static void blackjack(Canvas c) {
        card(c, 3, 3, 15, 21, true, 0);
        text(c, "A", 10.5f, 17, 11, 0xFF000000, true);
        card(c, 12, 7, 15, 21, true, 0);
        text(c, "J", 19.5f, 21, 11, 0xFFFF0000, true);
        circle(c, 24, 26, 5, 0xFF0000C0);
        circle(c, 24, 26, 3, 0xFFFFFFFF);
        circle(c, 24, 26, 2, 0xFF0000C0);
    }

    private static void winmine(Canvas c) {
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * i / 4;
            line(c, 16, 16, 16 + (float) Math.cos(a) * 13, 16 + (float) Math.sin(a) * 13, 0xFF000000, 2.2f);
        }
        circle(c, 16, 16, 9, 0xFF000000);
        rect(c, 11, 11, 15, 15, 0xFFFFFFFF);
    }

    private static void reversi(Canvas c) {
        rect(c, 1, 1, 31, 31, 0xFF008000);
        for (int i = 1; i < 4; i++) {
            line(c, 1 + i * 7.5f, 1, 1 + i * 7.5f, 31, 0xFF004000, 0.8f);
            line(c, 1, 1 + i * 7.5f, 31, 1 + i * 7.5f, 0xFF004000, 0.8f);
        }
        circle(c, 12.25f, 12.25f, 3.2f, 0xFF000000);
        circle(c, 19.75f, 19.75f, 3.2f, 0xFF000000);
        circle(c, 19.75f, 12.25f, 3.2f, 0xFFFFFFFF);
        circle(c, 12.25f, 19.75f, 3.2f, 0xFFFFFFFF);
        outline(c, 1, 1, 31, 31, 0xFF000000);
    }

    private static void taipei(Canvas c) {
        rect(c, 9, 4, 29, 30, 0xFF208020);
        rect(c, 6, 2, 26, 28, 0xFFFFF8E0);
        outline(c, 6, 2, 26, 28, 0xFF000000);
        text(c, "中", 16, 21, 15, 0xFFE00000, true);
    }

    private static void tetravex(Canvas c) {
        int[][] colors = {{0xFFFF0000, 0xFF00C000, 0xFF0000FF, 0xFFFFFF00}, {0xFF00C000, 0xFFFF8000, 0xFFFF00FF, 0xFF0000FF},
                {0xFF0000FF, 0xFFFFFF00, 0xFF00FFFF, 0xFFFF0000}, {0xFFFF00FF, 0xFF0000FF, 0xFFFF8000, 0xFF00FFFF}};
        for (int t = 0; t < 4; t++) {
            float x = 2 + (t % 2) * 14, y = 2 + (t / 2) * 14;
            float cx = x + 7, cy = y + 7;
            float[][] tri = {{x, y, x + 14, y}, {x + 14, y, x + 14, y + 14}, {x + 14, y + 14, x, y + 14}, {x, y + 14, x, y}};
            for (int s = 0; s < 4; s++) {
                Path p = new Path();
                p.moveTo(cx, cy);
                p.lineTo(tri[s][0], tri[s][1]);
                p.lineTo(tri[s][2], tri[s][3]);
                p.close();
                P.setColor(colors[t][s]);
                c.drawPath(p, P);
            }
            outline(c, x, y, x + 14, y + 14, 0xFF000000);
        }
    }

    private static void pegged(Canvas c) {
        circle(c, 16, 16, 15, 0xFFB07030);
        for (int r = 0; r < 5; r++) {
            for (int q = 0; q < 5; q++) {
                if ((r < 1 || r > 3) && (q < 1 || q > 3)) {
                    continue;
                }
                boolean empty = r == 2 && q == 2;
                circle(c, 6 + q * 5, 6 + r * 5, 1.8f, empty ? 0xFF603010 : 0xFFFFE080);
            }
        }
    }

    private static void tictac(Canvas c) {
        rect(c, 1, 1, 31, 31, 0xFFFFFFFF);
        for (int i = 1; i < 3; i++) {
            line(c, 1 + i * 10, 2, 1 + i * 10, 30, 0xFF000000, 1.5f);
            line(c, 2, 1 + i * 10, 30, 1 + i * 10, 0xFF000000, 1.5f);
        }
        line(c, 4, 4, 9, 9, 0xFFFF0000, 2);
        line(c, 9, 4, 4, 9, 0xFFFF0000, 2);
        line(c, 14, 14, 19, 19, 0xFFFF0000, 2);
        line(c, 19, 14, 14, 19, 0xFFFF0000, 2);
        S.setColor(0xFF0000FF);
        S.setStrokeWidth(2);
        c.drawCircle(26.5f, 6.5f, 3, S);
        c.drawCircle(6.5f, 26.5f, 3, S);
        S.setStrokeWidth(1.2f);
    }

    private static void tetris(Canvas c) {
        int[][] cells = {{1, 0, 0xFFFF0000}, {2, 0, 0xFFFF0000}, {3, 0, 0xFFFF0000}, {2, 1, 0xFFFF0000},
                {0, 2, 0xFF0000FF}, {0, 3, 0xFF0000FF}, {1, 3, 0xFF0000FF}, {2, 3, 0xFF0000FF},
                {3, 2, 0xFF00C000}, {3, 3, 0xFF00C000}, {2, 2, 0xFFFFFF00}};
        for (int[] cell : cells) {
            float x = 2 + cell[0] * 7, y = 3 + cell[1] * 7;
            rect(c, x, y, x + 7, y + 7, cell[2]);
            outline(c, x, y, x + 7, y + 7, 0xFF000000);
            line(c, x + 1, y + 1, x + 5.5f, y + 1, 0x99FFFFFF, 0.8f);
        }
    }

    private static void jezzball(Canvas c) {
        rect(c, 1, 1, 31, 31, 0xFF000000);
        rect(c, 1, 15, 31, 18, 0xFF0000FF);
        circle(c, 10, 8, 5, 0xFFFFFFFF);
        P.setColor(0xFFFF0000);
        c.drawArc(new RectF(5, 3, 15, 13), 180, 180, true, P);
        circle(c, 22, 25, 5, 0xFFFFFFFF);
        c.drawArc(new RectF(17, 20, 27, 30), 0, 180, true, P);
    }

    private static void pipe(Canvas c) {
        rect(c, 1, 1, 31, 31, 0xFFC0C0C0);
        S.setStrokeCap(Paint.Cap.BUTT);
        line(c, 1, 10, 22, 10, 0xFF404040, 7);
        line(c, 22, 6.5f, 22, 31, 0xFF404040, 7);
        line(c, 1, 10, 22, 10, 0xFF00C000, 3.5f);
        line(c, 22, 8.25f, 22, 20, 0xFF00C000, 3.5f);
        S.setStrokeCap(Paint.Cap.ROUND);
    }

    private static void rodent(Canvas c) {
        rect(c, 18, 18, 30, 29, 0xFFFFE040);
        circle(c, 22, 22, 1.5f, 0xFFD0B000);
        circle(c, 27, 26, 1.2f, 0xFFD0B000);
        circle(c, 12, 15, 8, 0xFF909090);
        circle(c, 6, 8, 3.5f, 0xFF909090);
        circle(c, 17, 8, 3.5f, 0xFF909090);
        circle(c, 6, 8, 2, 0xFFFFA0A0);
        circle(c, 17, 8, 2, 0xFFFFA0A0);
        circle(c, 9, 14, 1.2f, 0xFF000000);
        circle(c, 15, 14, 1.2f, 0xFF000000);
        circle(c, 12, 18, 1.4f, 0xFFFF6080);
    }

    private static void ski(Canvas c) {
        rect(c, 1, 1, 31, 31, 0xFFFFFFFF);
        Path tree = new Path();
        tree.moveTo(24, 3);
        tree.lineTo(30, 16);
        tree.lineTo(18, 16);
        tree.close();
        P.setColor(0xFF008000);
        c.drawPath(tree, P);
        rect(c, 23, 16, 25, 19, 0xFF804000);
        circle(c, 10, 9, 2.5f, 0xFF000000);
        line(c, 10, 11, 11, 19, 0xFFFF0000, 3);
        line(c, 11, 19, 8, 24, 0xFF0000FF, 2);
        line(c, 11, 19, 13, 24, 0xFF0000FF, 2);
        line(c, 3, 23, 16, 27, 0xFF000000, 1.4f);
        line(c, 6, 21, 19, 25, 0xFF000000, 1.4f);
    }

    private static void snake(Canvas c) {
        rect(c, 1, 1, 31, 31, 0xFF000080);
        Path p = new Path();
        p.moveTo(4, 26);
        p.cubicTo(4, 12, 14, 26, 14, 14);
        p.cubicTo(14, 4, 26, 6, 26, 12);
        S.setColor(0xFF00E000);
        S.setStrokeWidth(4);
        c.drawPath(p, S);
        S.setStrokeWidth(1.2f);
        circle(c, 26, 12, 3, 0xFF00E000);
        circle(c, 27, 11, 0.9f, 0xFF000000);
        circle(c, 8, 7, 2.2f, 0xFFFF0000);
    }

    private static void chips(Canvas c) {
        for (int i = 0; i < 5; i++) {
            rect(c, 7 + i * 4.2f, 2, 8.5f + i * 4.2f, 6, 0xFFA0A0A0);
            rect(c, 7 + i * 4.2f, 26, 8.5f + i * 4.2f, 30, 0xFFA0A0A0);
        }
        rect(c, 4, 6, 28, 26, 0xFF202020);
        text(c, "IC", 16, 20, 10, 0xFFFFD000, true);
    }

    // ---------------------------------------------------------------- shell

    private static void monitor(Canvas c, int screen) {
        rect(c, 3, 2, 29, 22, 0xFFC0C0C0);
        outline(c, 3, 2, 29, 22, 0xFF000000);
        rect(c, 6, 5, 26, 19, screen);
        rect(c, 12, 22, 20, 25, 0xFF808080);
        rect(c, 7, 25, 25, 28, 0xFFC0C0C0);
        outline(c, 7, 25, 25, 28, 0xFF000000);
    }

    private static void display(Canvas c) {
        monitor(c, 0xFF008080);
        rect(c, 9, 8, 18, 15, 0xFFFFFFFF);
        rect(c, 9, 8, 18, 10, 0xFF000080);
    }

    private static void computer(Canvas c) {
        monitor(c, 0xFF008080);
        rect(c, 2, 28, 30, 31, 0xFFC0C0C0);
        outline(c, 2, 28, 30, 31, 0xFF000000);
    }

    private static void recycle(Canvas c) {
        Path bin = new Path();
        bin.moveTo(7, 8);
        bin.lineTo(25, 8);
        bin.lineTo(23, 30);
        bin.lineTo(9, 30);
        bin.close();
        P.setColor(0xFFE0E0E0);
        c.drawPath(bin, P);
        S.setColor(0xFF000000);
        c.drawPath(bin, S);
        for (int i = 0; i < 3; i++) {
            line(c, 11 + i * 5, 11, 11.5f + i * 4.5f, 27, 0xFF808080, 1);
        }
        rect(c, 5, 5, 27, 8, 0xFFC0C0C0);
        outline(c, 5, 5, 27, 8, 0xFF000000);
    }

    private static void help(Canvas c) {
        rect(c, 5, 3, 27, 29, 0xFF800000);
        rect(c, 7, 3, 27, 27, 0xFFFFFFFF);
        outline(c, 5, 3, 27, 29, 0xFF000000);
        text(c, "?", 17, 23, 20, 0xFF0000C0, true);
    }

    private static void shutdown(Canvas c) {
        monitor(c, 0xFF000000);
        S.setColor(0xFFFFD000);
        S.setStrokeWidth(1.8f);
        c.drawArc(new RectF(11, 7, 21, 17), 300, 300, false, S);
        c.drawLine(16, 6, 16, 12, S);
        S.setStrokeWidth(1.2f);
    }

    private static void settings(Canvas c) {
        folder(c, 0xFFC0C0C0);
        P.setColor(0xFF404040);
        for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45, 19, 19);
            c.drawRect(17.5f, 10, 20.5f, 13, P);
            c.restore();
        }
        circle(c, 19, 19, 6.5f, 0xFF404040);
        circle(c, 19, 19, 2.5f, 0xFFC0C0C0);
    }

    private static void folder(Canvas c, int color) {
        Path p = new Path();
        p.moveTo(2, 8);
        p.lineTo(3, 5);
        p.lineTo(12, 5);
        p.lineTo(14, 8);
        p.lineTo(30, 8);
        p.lineTo(30, 27);
        p.lineTo(2, 27);
        p.close();
        P.setColor(color);
        c.drawPath(p, P);
        S.setColor(0xFF000000);
        c.drawPath(p, S);
        line(c, 2, 11, 30, 11, 0xFF806000, 0.8f);
    }
}
