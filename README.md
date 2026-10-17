# Entertainment Pack for Android

A remake of the Windows 95 Best of Entertainment Pack and the classic Windows games, inside a
Windows 95-style desktop, with the Windows 98 screen savers running next to whichever game you're
playing.

Everything is written from scratch in Java: the games, the 3D screen savers, the Windows 95 desktop
and its widgets. The app contains no Microsoft code or artwork.

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
| Chip's Challenge | Ten built-in practice levels; load your own `CHIPS.DAT` from the Level menu |

The card games can use the original card artwork: choose **Deck… → Load cards.dll…** and pick a
`cards.dll` from a Windows install (the 32-bit PE version). The app only reads the bitmaps out of
the DLL's resources and never runs its code. The DLL isn't included with the app.

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
`-DscreenshotDir=/some/dir` to the test task.

## Code

| Package | What's in it |
| --- | --- |
| `win` | The Windows 95 widget kit (`Win95`), dialogs and on-screen keyboard (`RetroView`), the app window base class (`WinApp`), icons, sound effects |
| `shell` | `MainActivity`, the split-screen `Shell`, `Desktop`, `Taskbar` with the Start menu and Display Properties, the program list (`Apps`) |
| `cards` | Card art (built-in and `cards.dll`), shared card-game plumbing |
| `games` | One class per game, plus pure-Java rule engines (`Klondike`, `FreeCellModel`, `ChipsEngine`) |
| `savers` | The screen savers and a small 3D helper |
