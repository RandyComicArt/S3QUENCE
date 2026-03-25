# S3QUENCE

S3QUENCE is a Java arcade-combat prototype built with Swing. The core loop mixes fast directional input, timed enemy encounters, dungeon-room traversal, shops, and a CRT-styled presentation layer.

## Current State

The game already includes:

- an opening screen and main menu
- a dungeon flow with room traversal
- encounter-based combat driven by directional input sequences
- multiple enemy archetypes with different rules
- a shop with run-based upgrades
- keyboard and controller support
- audio, screen effects, and CRT-style visual treatment

## Controls

### Keyboard

- `WASD` or arrow keys: move in dungeon rooms
- Arrow keys: enter encounter sequences and menu navigation
- `Enter`: confirm / interact
- `Esc`: back / return

### Controller

- D-pad or left stick: movement and menu navigation
- `A`, `X`, or `Start`: confirm
- `B`, `Y`, or `Back`: back

## Project Layout

```text
src/game/
  Main.java              Launches the application
  GamePanel.java         Main game loop, rendering, state flow, input wiring
  audio/                 Audio helpers
  config/                Tunable gameplay values
  input/                 Controller handling
  logic/                 Combat and round logic
  model/                 Enums and gameplay data models
  visual/                Visual effects

src/assets/              Sprites, controller mappings, and other assets
lib/                     External jars and native libraries
out/                     Compiled classes
```

## Run

### IntelliJ IDEA

Open the project, use `src` as the source root, and run [`src/game/Main.java`](/Users/randym/Desktop/Other/Java Games/S3QUENCE/src/game/Main.java).

### Terminal

Compile:

```bash
javac -cp "lib/*" -d out $(find src -name "*.java")
```

Run:

```bash
java -cp "out:lib/*" game.Main
```

Note: the controller path relies on SDL natives from `lib/`, so controller support may depend on your local Java and platform setup.

## Notes / Roadmap

This is the section to keep updating instead of the old `Notes` file.

### Features to Add

- add pausing and resume functionality
- create save files where the player can save progress
- intro and outro animations for the shop

### Things to Rework

- dungeon scene
- game over screen
- shop screen

### Continue Developing

- icons for items, such as a syringe for the poison item

### Item Ideas

Some items should behave like one-time perks and not level up. Others should be levelable and scale their stats.

#### Non-Levelling Items

- Preemptive Strike: if potential damage exceeds enemy health, kill the enemy immediately without completing the sequence
- Auto-Correct: once per encounter, a wrong input is ignored and converted into the correct one

#### Levelled Items

- Chilling Jab: executing a sequence slows or pauses the timer briefly by `X` milliseconds
- Initial Surge: deal `+X%` damage while the enemy timer is above `80%`
- Combo Catalyst: for every correct key, increase damage by `+X%` for the rest of the sequence; a wrong key resets the bonus to `0%`
- Lucky Duck: increase critical hit chance by `+X%`; critical hits deal double damage

### Item System Direction

Do not let the player collect every item in a single run. A better direction is a limited-slot system, closer to the constrained build choices in games like Hades.

Possible structure:

- start each run with one item slot
- let the player hold and level a limited set of items
- unlock more item slots during a run or through between-run home-base upgrades

