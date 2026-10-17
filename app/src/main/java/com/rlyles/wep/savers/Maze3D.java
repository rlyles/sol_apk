package com.rlyles.wep.savers;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.rlyles.wep.win.RetroView;
import com.rlyles.wep.win.Win95;

import java.util.List;

/**
 * 3D Maze: a walk through a brick maze, keeping a hand on the left wall, until it reaches the
 * smiley face at the end and a new maze is built.
 */
public final class Maze3D extends Saver {
    private static final int TEX = 64;
    private int cellsX = 8, cellsY = 8;
    private boolean[] wall;
    private int gw, gh;
    private float px, py, angle;
    private int dir; // 0 east, 1 south, 2 west, 3 north
    private float targetAngle, moveLeft;
    private int goalX, goalY;
    private int speed = 5;

    private int rw, rh;
    private int[] pixels;
    private Bitmap frame;
    private final int[] brick = new int[TEX * TEX], wood = new int[TEX * TEX], ceiling = new int[TEX * TEX];
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect(), dst = new Rect();

    public Maze3D() {
        buildTextures();
    }

    @Override
    public String name() {
        return "3D Maze";
    }

    @Override
    protected void loadSettings() {
        speed = prefs.getInt("speed", 5);
        int size = prefs.getInt("size", 1);
        cellsX = cellsY = 6 + size * 3;
    }

    private void buildTextures() {
        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                int row = y / 16;
                int off = row % 2 == 0 ? 0 : 16;
                boolean mortar = y % 16 < 2 || (x + off) % 32 < 2;
                int noise = (int) ((Math.sin(x * 12.9898 + y * 78.233) * 43758.5453) % 1 * 30);
                brick[y * TEX + x] = mortar ? rgb(180 + noise / 3, 175 + noise / 3, 165) : rgb(160 + noise, 50 + noise / 2, 35);
                int plank = x / 16;
                boolean seam = x % 16 == 0 || (y + plank * 23) % 64 == 0;
                int grain = (int) (Math.sin(y * 0.4 + plank * 3) * 12);
                wood[y * TEX + x] = seam ? rgb(70, 40, 20) : rgb(150 + grain, 100 + grain, 55 + grain / 2);
                boolean tileEdge = x % 32 == 0 || y % 32 == 0;
                ceiling[y * TEX + x] = tileEdge ? rgb(150, 150, 150) : rgb(215 + noise / 4, 215 + noise / 4, 210);
            }
        }
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    @Override
    protected void reset() {
        rh = 150;
        rw = Math.max(80, rh * w / Math.max(1, h));
        pixels = new int[rw * rh];
        if (frame != null) {
            frame.recycle();
        }
        frame = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888);
        newMaze();
    }

    private void newMaze() {
        gw = cellsX * 2 + 1;
        gh = cellsY * 2 + 1;
        wall = new boolean[gw * gh];
        java.util.Arrays.fill(wall, true);
        int[] stack = new int[cellsX * cellsY];
        boolean[] seen = new boolean[cellsX * cellsY];
        int sp = 0;
        stack[sp++] = 0;
        seen[0] = true;
        wall[gw + 1] = false;
        int[][] d = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        while (sp > 0) {
            int cur = stack[sp - 1];
            int cx = cur % cellsX, cy = cur / cellsX;
            int[] order = {0, 1, 2, 3};
            for (int i = 3; i > 0; i--) {
                int j = rnd.nextInt(i + 1), t = order[i];
                order[i] = order[j];
                order[j] = t;
            }
            boolean moved = false;
            for (int k : order) {
                int nx = cx + d[k][0], ny = cy + d[k][1];
                if (nx < 0 || ny < 0 || nx >= cellsX || ny >= cellsY || seen[ny * cellsX + nx]) {
                    continue;
                }
                seen[ny * cellsX + nx] = true;
                wall[(cy * 2 + 1 + d[k][1]) * gw + cx * 2 + 1 + d[k][0]] = false;
                wall[(ny * 2 + 1) * gw + nx * 2 + 1] = false;
                stack[sp++] = ny * cellsX + nx;
                moved = true;
                break;
            }
            if (!moved) {
                sp--;
            }
        }
        px = 1.5f;
        py = 1.5f;
        dir = wall[gw + 2] ? 1 : 0;
        angle = targetAngle = dir * (float) Math.PI / 2;
        moveLeft = 0;
        goalX = gw - 2;
        goalY = gh - 2;
    }

    private boolean open(int x, int y) {
        return x >= 0 && y >= 0 && x < gw && y < gh && !wall[y * gw + x];
    }

    private static final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1};

    /** Left-hand rule, deciding at the centre of each grid square. */
    private void decide() {
        int cx = (int) px, cy = (int) py;
        if (cx == goalX && cy == goalY) {
            newMaze();
            return;
        }
        int left = (dir + 3) % 4, right = (dir + 1) % 4;
        if (open(cx + DX[left], cy + DY[left])) {
            dir = left;
            targetAngle -= (float) Math.PI / 2;
        } else if (open(cx + DX[dir], cy + DY[dir])) {
            // keep going
        } else if (open(cx + DX[right], cy + DY[right])) {
            dir = right;
            targetAngle += (float) Math.PI / 2;
        } else {
            dir = (dir + 2) % 4;
            targetAngle += (float) Math.PI;
            return;
        }
        moveLeft = 1f;
    }

    private void step(float dt) {
        float turnRate = 2.2f + speed * 0.25f, walkRate = 1.0f + speed * 0.25f;
        if (Math.abs(targetAngle - angle) > 0.001f) {
            float d = targetAngle - angle;
            float s = Math.signum(d) * Math.min(Math.abs(d), turnRate * dt);
            angle += s;
            return;
        }
        if (moveLeft > 0) {
            float s = Math.min(moveLeft, walkRate * dt);
            px += DX[dir] * s;
            py += DY[dir] * s;
            moveLeft -= s;
            if (moveLeft <= 0) {
                px = (int) px + 0.5f;
                py = (int) py + 0.5f;
            }
            return;
        }
        decide();
    }

    @Override
    public void draw(Canvas c, float dt) {
        if (frame == null) {
            c.drawColor(0xFF000000);
            return;
        }
        step(dt);
        render();
        frame.setPixels(pixels, 0, rw, 0, 0, rw, rh);
        src.set(0, 0, rw, rh);
        dst.set(0, 0, w, h);
        c.drawBitmap(frame, src, dst, blit);
    }

    private void render() {
        float dirX = (float) Math.cos(angle), dirY = (float) Math.sin(angle);
        float planeX = -dirY * 0.66f, planeY = dirX * 0.66f;
        // Floor and ceiling, cast row by row.
        for (int y = rh / 2 + 1; y < rh; y++) {
            float rowDist = (rh / 2f) / (y - rh / 2f);
            float lx = px + rowDist * (dirX - planeX), ly = py + rowDist * (dirY - planeY);
            float sx = rowDist * 2 * planeX / rw, sy = rowDist * 2 * planeY / rw;
            float fog = Math.max(0.25f, 1 - rowDist / 9f);
            for (int x = 0; x < rw; x++) {
                int tx = (int) (lx * TEX) & (TEX - 1), ty = (int) (ly * TEX) & (TEX - 1);
                pixels[y * rw + x] = dim(wood[ty * TEX + tx], fog);
                pixels[(rh - y) * rw + x] = dim(ceiling[ty * TEX + tx], fog);
                lx += sx;
                ly += sy;
            }
        }
        for (int x = 0; x < rw; x++) {
            float camX = 2f * x / rw - 1;
            float rx = dirX + planeX * camX, ry = dirY + planeY * camX;
            int mx = (int) px, my = (int) py;
            float ddx = rx == 0 ? 1e30f : Math.abs(1 / rx), ddy = ry == 0 ? 1e30f : Math.abs(1 / ry);
            int stepX = rx < 0 ? -1 : 1, stepY = ry < 0 ? -1 : 1;
            float sdx = rx < 0 ? (px - mx) * ddx : (mx + 1 - px) * ddx;
            float sdy = ry < 0 ? (py - my) * ddy : (my + 1 - py) * ddy;
            boolean side = false;
            int guard = 0;
            while (guard++ < 64) {
                if (sdx < sdy) {
                    sdx += ddx;
                    mx += stepX;
                    side = false;
                } else {
                    sdy += ddy;
                    my += stepY;
                    side = true;
                }
                if (!open(mx, my)) {
                    break;
                }
            }
            float dist = side ? sdy - ddy : sdx - ddx;
            dist = Math.max(0.05f, dist);
            int lineH = (int) (rh / dist);
            int top = Math.max(0, rh / 2 - lineH / 2), bottom = Math.min(rh - 1, rh / 2 + lineH / 2);
            float hit = side ? px + dist * rx : py + dist * ry;
            hit -= (float) Math.floor(hit);
            int tx = (int) (hit * TEX) & (TEX - 1);
            boolean goalWall = Math.abs(mx - goalX) + Math.abs(my - goalY) == 1;
            float fog = Math.max(0.2f, 1 - dist / 9f) * (side ? 0.8f : 1f);
            for (int y = top; y <= bottom; y++) {
                int ty = (int) ((y - (rh / 2f - lineH / 2f)) * TEX / lineH) & (TEX - 1);
                int col = brick[ty * TEX + tx];
                if (goalWall) {
                    col = smiley(tx, ty, col);
                }
                pixels[y * rw + x] = dim(col, fog);
            }
        }
    }

    /** The smiley face painted on the walls around the end of the maze. */
    private static int smiley(int x, int y, int base) {
        float dx = x - 32, dy = y - 32;
        float r = (float) Math.hypot(dx, dy);
        if (r > 22) {
            return base;
        }
        boolean eye = Math.hypot(x - 24, y - 26) < 4 || Math.hypot(x - 40, y - 26) < 4;
        boolean mouth = r > 11 && r < 15 && dy > 3;
        return eye || mouth || r > 20 ? 0xFF000000 : 0xFFFFFF00;
    }

    private static int dim(int c, float k) {
        return 0xFF000000 | (int) (((c >> 16) & 0xFF) * k) << 16 | (int) (((c >> 8) & 0xFF) * k) << 8
                | (int) ((c & 0xFF) * k);
    }

    @Override
    public float settings(Win95.Dialog d, float top, List<Runnable> apply, RetroView host) {
        d.add(new Win95.Group("Maze size")).at(10, top, 280, 120);
        int size = prefs.getInt("size", 1);
        d.add(new Win95.Radio("&Small", 0, size == 0)).at(22, top + 26, 250, 28);
        d.add(new Win95.Radio("&Medium", 0, size == 1)).at(22, top + 56, 250, 28);
        d.add(new Win95.Radio("&Large", 0, size == 2)).at(22, top + 86, 250, 28);
        d.add(new Win95.Text("&Speed", false)).at(10, top + 128, 280, 22);
        Win95.Slider sp = d.add(new Win95.Slider(0, 10, speed));
        sp.leftLabel = "Slow";
        sp.rightLabel = "Fast";
        sp.at(10, top + 150, 280, 44);
        apply.add(() -> prefs.edit().putInt("size", Win95.Radio.selected(d, 0)).putInt("speed", sp.value).apply());
        return 200;
    }

    @Override
    public void release() {
        if (frame != null) {
            frame.recycle();
            frame = null;
        }
    }
}
