# Realism Plugin

The **Realism** plugin adds a layer of survival mechanics to Old School RuneScape, bringing hunger, thirst and equipment durability into the game.  These meters are represented by movable **orbs** overlaid on your game view and are fully configurable.

## Features

* **Hunger and Thirst Meters** – meters drain over time and are replenished by eating food or drinking potions.  Alerts and optional desktop notifications trigger at configurable thresholds.
* **Durability Tracking** – all equipped items wear down over time; broken items are highlighted and you can optionally prevent equipping them until repaired.
* **Movable Status Orbs** – hunger, thirst and durability are shown as pie‐chart orbs with RuneLite item icons (meat, fish or cabbage for hunger; water vial for thirst; helmet for durability).  Orbs can be dragged while holding Alt.
* **Customisable Settings** – adjust drain rates, colour schemes, warning thresholds, messages, notification behaviour and the hunger icon type.

## Installation

1. Clone or download this repository and open it in an IDE configured for RuneLite development (see the [RuneLite developer guide](https://github.com/runelite/runelite#developer-guide)).
2. Ensure you are using **JDK 11** as recommended by RuneLite’s plugin‑hub guidelines.
3. Build the plugin using Gradle:

   ```bash
   ./gradlew build
   ```

4. Copy the generated JAR from `build/libs` into your RuneLite external plugins folder.
5. Run RuneLite in developer mode and enable the **Realism** plugin in the settings panel.

## Usage

Once enabled, you will see three status orbs (hunger, thirst and durability) appear near the top‑left of your game window.  Hold **Alt** and drag to reposition them.  Open the plugin’s configuration panel to customise drain rates, colours, threshold messages and whether to restrict equipping broken gear.  As you play:

* Eating food will restore hunger according to its heal value (scaled by the `Food Heal Weight` setting).
* Drinking potions or beverages will restore thirst by a fixed amount (configurable).
* Equipped items lose durability over time; once a piece reaches zero, it is flagged as broken.  You can optionally prevent equipping broken items.

The plugin never generates input or automates actions; it merely displays information and blocks equip attempts on broken items, ensuring compliance with RuneLite’s rules and Jagex’s Third‑Party Client guidelines.

## Contributing

Contributions are welcome!  Feel free to open issues or pull requests for bug fixes, feature requests or improvements.  Please ensure your code adheres to the RuneLite code style and includes appropriate documentation.

## License

This project is distributed under the BSD 2‑Clause license.  See the [LICENSE](LICENSE) file for more information.