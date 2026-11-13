# Quests

Quest-/Aufgabensystem für **Paper 1.26** (Java 21). Spieler klicken mit Rechtsklick auf einen
festgelegten Block und sehen in einem Kisten-GUI alle Aufgaben mit Fortschritt, Prozent,
Belohnung und Status. Alle Quests und Belohnungen werden komplett in der `config.yml` definiert.

## Installation

1. Projekt bauen: `mvn clean package`
2. `target/Quests-1.0.0.jar` in den Ordner `plugins/` des Servers legen.
3. Server starten (die `config.yml` wird erzeugt).
4. Im Spiel einen Block anschauen und `/questblock set` ausführen.
5. Rechtsklick auf den Block öffnet das Quest-Menü.

Voraussetzungen: Paper 1.26, Java 21. Steht die Paper-API-Version anders im Maven-Repository,
passe `paper.version` in der `pom.xml` an.

## Befehle

| Befehl | Beschreibung | Permission |
|---|---|---|
| `/questblock set` | Angeschauten Block als Quest-Block speichern | `quests.admin` |
| `/questblock remove` | Angeschauten Quest-Block entfernen | `quests.admin` |
| `/questblock list` | Alle Quest-Blöcke auflisten | `quests.admin` |
| `/quests reload` | Config neu laden | `quests.reload` |
| `/quests reset <Spieler> [Quest\|all]` | Fortschritt zurücksetzen | `quests.reset` |
| `/quests progress [Spieler]` | Fortschritt anzeigen (fremde Spieler nur mit `quests.admin`) | `quests.progress` |

## Permissions

| Permission | Standard | Beschreibung |
|---|---|---|
| `quests.admin` | OP | Alles (enthält alle anderen) |
| `quests.reload` | OP | Config neu laden |
| `quests.reset` | OP | Fortschritt zurücksetzen |
| `quests.progress` | alle | Eigenen Fortschritt anzeigen |
| `quests.use` | alle | Quest-Menü am Quest-Block öffnen |

## Neue Quest hinzufügen

Einen neuen Eintrag unter `quests:` anlegen und `/quests reload` ausführen:

```yaml
quests:
  dirt_digger:
    icon: DIAMOND_SHOVEL
    display-name: "&eErdarbeiter"
    description:
      - "&7Baue 5.000 Erdblöcke ab."
    target: 5000
    type: BREAK_SPECIFIC
    tool: SHOVEL
    materials: [DIRT, GRASS_BLOCK, SAND, GRAVEL]
    reward:
      material: DIAMOND_SHOVEL
      enchants:
        EFFICIENCY: 5
```

Quest-Typen: `BREAK_BLOCKS` (alle Blöcke, optional mit Werkzeug und Materialfilter) und
`BREAK_SPECIFIC` (nur Blöcke aus `materials`). Materiallisten unterstützen Wildcards wie `*_LOG`.
Belohnungen unterstützen Material, Anzahl, Name, Lore, Enchantments, ItemFlags, Unbreakable,
CustomModelData und Konsolenbefehle. Alle Details stehen als Kommentare in der `config.yml`.

## Weitere Quest-Typen programmieren

In `QuestType` eine neue Konstante mit eigener `matches(...)`-Methode ergänzen. Sie ist danach
über `type: NAME` in der Config nutzbar. Für Ereignisse außer dem Blockabbau zusätzlich einen
Listener-Eintrag und eine Tracking-Methode im `QuestManager` ergänzen.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `QuestsPlugin` | Start/Stopp, Verdrahtung |
| `ConfigManager` | Config lesen, Quests parsen, Nachrichten |
| `Quest`, `QuestType`, `ToolType`, `QuestReward` | Quest-Definitionen |
| `QuestManager` | Tracking (Material-Index), Status, Abholen, Cache |
| `PlayerQuestData`, `QuestStorage` | Spielerdaten und Speicherung (YAML pro Spieler) |
| `RewardManager` | Belohnungs-Items und Vergabe |
| `GUIManager`, `QuestMenuHolder` | Kisten-Menü |
| `QuestBlockManager` | Quest-Blöcke (persistent in `questblocks.yml`) |
| `EffectManager` | Sounds und Partikel |
| `QuestListener` | Events |
| `QuestCommand`, `QuestBlockCommand` | Befehle |

## Datenspeicherung

* Spielerfortschritt: `plugins/Quests/playerdata/<uuid>.yml` (Autosave, beim Verlassen und beim Stoppen;
  das Abholen einer Belohnung wird sofort gespeichert, damit nichts doppelt vergeben wird)
* Quest-Blöcke: `plugins/Quests/questblocks.yml`

Quest-IDs nach dem ersten Einsatz nicht mehr umbenennen, sonst geht der zugehörige Fortschritt verloren.
