# ItemLimiter 🛡️

A lightweight, secure, and optimized Minecraft Paper plugin (1.21+) that allows server admins to limit the amount of specific items a player can carry.

## ✨ Features

* **GUI Management:** Easily manage limits via an in-game GUI (`/limit`).
* **Smart Pickup System:**
    * If a player has space for 3 items but the stack on the ground has 5, the plugin will **split the stack**.
    * The player picks up 3, and 2 remain on the ground.
    * No more "Inventory Full" spam when you could actually carry some of it!
* **Secure Prevention:**
    * Blocks picking up items beyond the limit.
    * Blocks crafting if the result exceeds the limit.
    * Blocks inventory clicks/swapping/dragging that would bypass the limit.
* **Visual & Audio Feedback:**
    * Particles (Smoke) and Sounds (Villager No) when a limit prevents an action.
    * Actionbar messages instead of chat spam.
* **Optimized:** Uses HashMaps for O(1) lookups. No laggy schedulers.

## 📥 Installation

1.  Download the latest release JAR.
2.  Place it into your server's `plugins/` folder.
3.  Restart the server.
4.  **Note:** This plugin requires a server running **Paper 1.21** (or forks like Purpur/Pufferfish).

## 🎮 Usage

### Commands

* `/limit` - Opens the limit management GUI.

### Permissions

* `itemlimiter.admin` - Allows access to the `/limit` command and GUI.

### How to use the GUI

1.  Run `/limit`.
2.  **Add an Item:** Hold an item in your main hand and click the **Lime Dye** icon at the top.
3.  **Edit Limits:**
    * **Left-Click** an item to increase the limit (+1).
    * **Right-Click** an item to decrease the limit (-1).
    * **Shift-Click** an item to remove the limit completely.

## ⚙️ Configuration

The `config.yml` is simple and allows you to manually edit limits if needed.

```yaml
# ItemLimiter Config
# Format: MATERIAL: AMOUNT
limits:
  TOTEM_OF_UNDYING: 1
  ENCHANTED_GOLDEN_APPLE: 6

# List of worlds where limits are active.
# Leave empty [] to activate everywhere.
enabled-worlds: []
