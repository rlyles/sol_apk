# Entertainment Pack for Android

A remake of the Windows 95 Best of Entertainment Pack and the classic Windows games, inside a
Windows 95-style desktop, with the Windows 98 screen savers running next to whichever game you're
playing.

Everything is written from scratch in Java: the games, the 3D screen savers, the Windows 95 desktop
and its widgets. The app contains no Microsoft code or artwork, but it can use the pictures and
sounds from your own copy of the original games (see below).

## The desktop

- **Desktop and Start menu.** Tap a desktop icon, or use Start → Programs, to run a game. Games
  open maximized above the taskbar, which has a button for each running game. The title-bar
  buttons minimize or close a game.
- **Split screen.** These were 4:3 games, so by default the game gets a 4:3 play area and the
  screen saver fills the rest of the screen: above the game in portrait, to its left in
  landscape.
  - Drag the bar between them to change the split, or drag it to the edge to hide the screen
    saver.
  - Double-tap the bar to go back to the 4:3 split.
- **Choosing a screen saver.** Long-press the screen saver, or the desktop, to open Display
  Properties. It's also under Start → Settings → Screen Saver. Display Properties has:
  - the screen saver list, with a live preview on the little monitor
  - each saver's own Settings… dialog
  - a full-screen Preview
  - a "full screen after N minutes idle" timer
- **Start → Settings** also turns the split screen and sound effects on and off.

## Games

Each game keeps its original menus and options.

| Game | Notes |
| --- | --- |
| Solitaire | Draw one or three; Standard, Vegas or no scoring; bouncing-cards win |
| FreeCell | Microsoft's numbered deals (game numbers match the original), statistics |
| Golf, TriPeaks, Tut's Tomb, Cruel | Undo/Backup, records, card backs |
| BlackJack | Four-deck shoe, double, split, basic-strategy hints |
| Minesweeper | Beginner/Intermediate/Expert/Custom, marks, best times; long-press to flag |
| Reversi | Four skill levels, Hint, Pass |
| Taipei | 144-tile turtle; every deal is winnable; Hint, Backup, Autoplay |
| TetraVex | 2×2 to 6×6, numbers/letters/Greek/symbols, Hint, Solve |
| Pegged | Cross, Plus, Fireplace, Up Arrow, Pyramid, Diamond, Solitaire |
| TicTactics | 3×3, 3×3×3 and 4×4×4 against the computer |
| Tetris | Drag to move, tap to rotate, flick down to drop |
| JezzBall | Tap to build a wall, swipe to choose its direction |
| Pipe Dream | Dispenser, countdown, crosses, Fast flow |
| Rodent's Revenge | Hold a finger where the mouse should go; 50 levels |
| SkiFree | Steer toward your finger, double-tap to jump; mind the yeti |
| Snake | Eat 1 to 9, then out of the door to the next room |
| Chip's Challenge | Ten built-in practice levels; load your own `CHIPS.DAT` from the Level menu or import it (below) |

## Original pictures and sounds

The app ships with its own drawings and synthesized sounds. If you have the original disks, it
can use their pixel art and sounds instead. Go to **Start → Settings → Original Game Files…**,
tap **Import Files…** and pick a `.zip` of the game folder, or select the `.EXE`, `.DLL`, `.WAV`
and `.MID` files themselves. The 16-bit Windows 3.x files and the later 32-bit versions both work.

| Game | File | What it adds |
| --- | --- | --- |
| Card games | `CARDS.DLL` | Card faces and backs (also under each game's **Deck…** dialog) |
| Chip's Challenge | `CHIPS.EXE`, `CHIPS.DAT`, `*.WAV`, `CHIP01/02.MID` | Tiles, info panel and LED digits; the 149 original levels; sound effects and music |
| FreeCell | `FREECELL.EXE` | The king |
| JezzBall | `JEZZBALL.EXE`, `*.WAV` | Atoms, field and walls; sounds |
| Minesweeper | `WINMINE.EXE` | Squares, counters and smiley (colour and black and white) |
| Pipe Dream | `PIPE.EXE` | Pipes, start pieces and board |
| Rodent's Revenge | `RODENT.EXE` | Mouse, cats, blocks, cheese and sink holes |
| SkiFree | `SKI.EXE` | Skier, yeti, dogs, trees, rocks, ramps |
| Snake | `SNAKE.EXE` | The snake |
| Taipei | `TP.EXE` or `TAIPEI.EXE` | Tile faces |
| Tetris | `TETRIS.EXE` | The changing backdrops |
| TicTactics | `TICTAC.EXE` | Red and blue balls |
| TriPeaks | `TRIPEAKS.EXE` | Its own deck |
| Tut's Tomb | `TUTSTOMB.EXE` | The pyramid |

The app only reads bitmaps and sounds out of the files; it never runs their code. The files are
kept in the app's private storage, and nothing from them is included with the app. Two checkboxes
in the same dialog switch the original pictures and the original sounds on and off. **Remove All**
deletes the imported files.

A few sounds the games ask for aren't in the pack: `CHIMES.WAV`, `STRIKE.WAV` and `HIT3.WAV` for
Chip's Challenge, and `GAMELOST.WAV` for JezzBall. Import them as well if you have them, and they'll
be used; until then those moments get a built-in sound or none.

## Screen savers

The 22 savers from Windows 98 and Plus! 98:

- **3D:** 3D Flower Box, 3D Flying Objects, 3D Maze, 3D Pipes, 3D Text.
- **Flying Windows** and **Starfield**.
- **The Plus! 98 themes:** Baseball, Dangerous Creatures, Inside your Computer, Jungle, Leonardo
  da Vinci, Mystery, Nature, Science, Space, Sports, The 60's USA, The Golden Era, Travel,
  Underwater and Windows 98.

The original theme savers showed pictures from their Plus! themes. Those pictures aren't part of
the `.scr` files, so each theme here is an original animated scene instead.

## Build

Requirements: JDK 17+ and the Android SDK (platform 34).

```
./gradlew testDebugUnitTest   # rules tests, a Chip's Challenge level solver, Robolectric UI tests
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the local debug key so it can be sideloaded right away.

To save screenshots of every game and screen saver during the UI tests, pass
`-DscreenshotDir=/some/dir` to the test task. To also test against real Entertainment Pack files,
pass `-DmediaDir=/folder/of/them`.

## Code

| Package | What's in it |
| --- | --- |
| `win` | The Windows 95 widget kit (`Win95`), dialogs and on-screen keyboard (`RetroView`), the app window base class (`WinApp`), icons, sound effects |
| `media` | The user's original files: 16- and 32-bit resource reader (`ResFile`), bitmaps including RLE (`Dib`), WAVs, MIDI music, the importer (`Media`) and sprite helpers |
| `shell` | `MainActivity`, the split-screen `Shell`, `Desktop`, `Taskbar` with the Start menu and Display Properties, the program list (`Apps`) |
| `cards` | Card art (built-in and `cards.dll`), shared card-game plumbing |
| `games` | One class per game, plus pure-Java rule engines (`Klondike`, `FreeCellModel`, `ChipsEngine`) |
| `savers` | The screen savers and a small 3D helper |
