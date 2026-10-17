package com.rlyles.wep.savers;

import java.util.ArrayList;
import java.util.List;

/** The screen saver list, in the order the Display Properties dialog shows it. */
public final class Savers {
    public interface Factory {
        Saver create();
    }

    public static final class Info {
        public final String id, title;
        final Factory factory;

        Info(String id, String title, Factory factory) {
            this.id = id;
            this.title = title;
            this.factory = factory;
        }
    }

    public static final List<Info> ALL = new ArrayList<>();
    public static final String NONE = "none";

    static {
        ALL.add(new Info(NONE, "(None)", null));
        ALL.add(new Info("flowerbox", "3D Flower Box", FlowerBox::new));
        ALL.add(new Info("flyingobjects", "3D Flying Objects", FlyingObjects::new));
        ALL.add(new Info("maze3d", "3D Maze", Maze3D::new));
        ALL.add(new Info("pipes3d", "3D Pipes", Pipes3D::new));
        ALL.add(new Info("text3d", "3D Text", Text3D::new));
        theme("baseball", "Baseball");
        theme("creatures", "Dangerous Creatures");
        ALL.add(new Info("flyingwindows", "Flying Windows", FlyingWindows::new));
        theme("computer", "Inside your Computer");
        theme("jungle", "Jungle");
        theme("leonardo", "Leonardo da Vinci");
        theme("mystery", "Mystery");
        theme("nature", "Nature");
        theme("science", "Science");
        theme("space", "Space");
        theme("sports", "Sports");
        ALL.add(new Info("starfield", "Starfield", Starfield::new));
        theme("sixties", "The 60's USA");
        theme("golden", "The Golden Era");
        theme("travel", "Travel");
        theme("underwater", "Underwater");
        theme("windows98", "Windows 98");
    }

    private Savers() {
    }

    private static void theme(String id, String title) {
        ALL.add(new Info(id, title, () -> ThemeSaver.create(id)));
    }

    public static Saver create(String id) {
        for (Info i : ALL) {
            if (i.id.equals(id)) {
                return i.factory == null ? null : i.factory.create();
            }
        }
        return null;
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).id.equals(id)) {
                return i;
            }
        }
        return 0;
    }
}
