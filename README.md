# Solitaire for Android

A remake of the classic Windows Solitaire (Klondike) for Android phones and tablets. It's
built from scratch to look and play like the original, down to the Windows 95 window, menus and
dialogs and the bouncing cards when you win.

## Features

- **Game menu:** Deal, Undo, Deck…, Options…, Exit. **Help menu:** Contents, About.
- **Options dialog:** Draw One / Draw Three; Standard, Vegas or no scoring; timed game; status
  bar; outline dragging; cumulative Vegas score; and *Tap to move*, which is new.
- **Original scoring rules:**
  - Standard: +10 for a card to a foundation, +5 deck to column, +5 for turning a card over,
    −15 for a card off a foundation.
  - Recycling the deck costs −100 when drawing one, or −20 after the third pass when drawing
    three.
  - A timed game loses 2 points every 10 seconds, and a win earns a 700,000 ÷ seconds bonus.
  - Vegas: $52 a game and $5 back for every card played up. You get one pass through the deck
    when drawing one, three when drawing three.
- **Bouncing-cards win animation**, followed by "Deal Again?".
- **Touch controls:** drag cards and stacks, tap a card to send it to the best spot, double-tap
  a card to send it to a foundation, or double-tap the green table to send up everything that
  can go (the old right-click). Tap the deck to deal.
- Your game and settings are saved automatically. Portrait and landscape both work.

## Card art

The app draws its own cards: classic-style faces and 12 original card backs. It doesn't
contain any Microsoft artwork.

To play with the original cards, copy your own `cards.dll` from a Windows install to your
phone. Then choose **Game → Deck… → Load cards.dll…** and pick the file. The app reads the card
bitmaps out of the DLL's resources and never runs any of its code. **Built-in cards** switches
back.

## Install

Download the APK (from the latest build on the Actions tab, or build it yourself) and open it on
your phone. You may need to allow installing apps from that source.

## Build

Requirements: JDK 17+ and the Android SDK (platform 34).

```
./gradlew testDebugUnitTest   # rules, scoring and UI tests (Robolectric)
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the local debug key so it can be sideloaded right away.

To save screenshots of the UI tests, or to test the DLL loader, pass
`-DscreenshotDir=/some/dir` and `-DcardsDll=/path/to/cards.dll` to the test task.

## Code

| File | What it does |
| --- | --- |
| `Game.java` | Klondike rules, scoring, undo, save/load (plain Java, unit tested) |
| `SolitaireView.java` | Layout, drawing, touch, animations, menus, dialogs |
| `Win95.java` | Windows 95 look-alike widgets drawn on a Canvas |
| `CardPainter.java` | The built-in vector card faces and backs |
| `CardsDll.java` | Reads card bitmaps from a user-supplied cards.dll |
| `CardArt.java` | Picks built-in or DLL art and caches it at screen size |
