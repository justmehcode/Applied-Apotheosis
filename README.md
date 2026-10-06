# Applied Apotheosis

AE2 infusion automation and spawner/gateway simulation for Minecraft 1.21.1, NeoForge 21.1.251+ (21.1.x) and Java 21.

## Installation

Place AppliedApotheosis-1.21.1-1.0.1.jar in the client and server mods folders.

Required minimums: AE2 19.2.17, Apothic Enchanting 1.6.2, Just Dire Things 1.5.7, Placebo 9.9.2 and Apothic Attributes 2.10.0. Use NeoForge builds for Minecraft 1.21.1.

Optional integrations: Experience Lib 1.2.1+, Apothic Spawners 1.4.0+, Gateways to Eternity 5.1.0+, Apotheosis 8.8.0+, ExtendedAE, JEI 19+ and Jade 15+. Install each integration's own dependencies.

Newer dependency releases are allowed without an artificial upper version limit. API-breaking changes may still require an addon update. Verified newer versions include AE2 19.2.18, Apothic Enchanting 1.6.3, Placebo 9.9.3, Apothic Attributes 2.11.0 and Apotheosis 8.9.0.

## Usage

- Infusion Controller: one recipe at a time; Extended Infusion Controller: four. Insert an item and lapis manually or use an AE2 Pattern Provider. Acceleration Cards increase speed.
- Select Personal XP or External XP in the toolbar. External XP uses ME XP fluids first, then XP exposed by adjacent Experience Lib containers. No internal XP tank is required.
- Just Dire Things XP and Industrial Foregoing Essence are accepted from ME storage at 20 mB per XP point. Add other fluid IDs and their mB-per-point rates under `[infusion].xpFluids` in `appliedapotheosis-server.toml`. Missing fluids are ignored. Cancellation returns equivalent XP to adjacent native storage, ME Just Dire Things XP, or a refund receipt if storage cannot accept it.
- ME Simulator: four spawners or Gate Pearls; Extended ME Simulator: eight. Use AE power or FE, with configurable FE costs. Choose ME or adjacent-container output. Selected adjacent containers can receive native Experience Lib XP or XP fluid. Spawners remain installed; gateways consume one pearl per simulation. The sword slot applies supported loot enchantments.

See `appliedapotheosis-server.example.toml` for configuration.

## Building

Run `gradlew.bat build` on Windows or `sh gradlew build` on Linux/macOS with Java 21. The source archive excludes tests and development files.

Development runs accept `-PcompatProfile=baseline` or `latest` (a fixed snapshot, not a dynamic download). Runtime versions can also be overridden with `-Pae2Version=...`, `-PenchantingVersion=...`, `-PplaceboVersion=...`, `-PattributesVersion=...`, and `-PapotheosisVersion=...`. Compilation stays on the minimum supported APIs.

Registry ID: `applied_apotheosis`.

Code: MIT. See `THIRD_PARTY_NOTICES.md` for artwork and dependency attribution.
