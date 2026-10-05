# Runtime references

AppliedApotheosis's original code is licensed under MIT (see LICENSE).

This addon uses the following installed dependency implementations and resource references at runtime. User-supplied simulator artwork is listed separately below:

- Applied Energistics 2 19.2.17, Team Applied Energistics: crafting/network APIs; AEBaseMenu, AEBaseScreen, BackgroundGenerator, VerticalButtonBar, UpgradesPanel, AppEngSlot, ProgressBar and IconButton implementations; common screen styles, icons, and Cell Workbench side textures. Upstream: https://github.com/AppliedEnergistics/Applied-Energistics-2 (LGPL-3.0 code, with upstream's separately identified API/artwork terms).
- Apothic Enchanting 1.6.2, Shadows-of-Fire: infusion recipe APIs, XP cost calculation, stat-bar dimensions, enchanting GUI and Raven table textures. Upstream: https://github.com/Shadows-of-Fire/Apothic-Enchanting (MIT code; artwork is covered by the upstream LICENSE_ASSETS and is not redistributed here).
- Minecraft: XP bar sprites and XP-orb texture, supplied by the installed game.
- Just Dire Things: registered XP fluid, supplied by the installed mod.
- JEI: recipe-category API, supplied by the optional installed mod.
- Jade 15.10.6+neoforge: block inspection API, supplied by the optional installed mod. Upstream: https://github.com/Snownee/Jade . API reference: https://jademc.readthedocs.io/en/1.x/plugins20/getting-started/ .

The extended controller's original corner geometry and teal title accent are visually inspired by ExtendedAE machines (https://github.com/GlodBlock/ExtendedAE/tree/1.21-2.2.38-neoforge). The Entro shade is sampled from the upstream Entro Crystal palette. No ExtendedAE implementation code is bundled. The controller geometry tints Minecraft's installed white-concrete texture. ExtendedAE is an optional runtime dependency required for the extended controller's crafting ingredients (Entro Crystals and Concurrent Processors).

The UI draws from installed texture resource locations and invokes the dependencies' public display helpers. It does not embed upstream implementation source files.

The native AE2 interface classes are linked from the installed AE2 dependency. The addon provides only its own layout and infusion-specific integration; its unique layout resource uses the AE2 namespace because AE2 StyleManager resolves screen documents there.

ME Simulator uses AE2's native generated-screen/slot widgets at runtime. Gateways to Eternity 5.1.0 (https://github.com/Shadows-of-Fire/GatewaysToEternity) supplies its gateway definitions, entity factories, modifiers and reward APIs. Apothic Spawners 1.4.0 (https://github.com/Shadows-of-Fire/Apothic-Spawners) supplies native loot hooks. Neither mod's implementation nor assets are bundled.

Apotheosis 8.8.0 (https://github.com/Shadows-of-Fire/Apotheosis/tree/1.21) supplies tier context, native tiered gateway definitions, affix/gem reward implementations, invader factories and elite initialization. These implementations are invoked from the installed mod and not embedded. The empty spawner/pearl silhouettes are small original code-drawn UI placeholders.

Build 002 embeds four 16x16 PNG textures supplied by the user: front.png, back.png, top.png and ex_drive_side.png, renamed to me_simulator_front/back/top/side.png. They are copied byte-for-byte and replace the previous Drive texture overlays. Their upstream authorship and licensing were not supplied; they are not claimed as original MIT artwork. The addon code's MIT license does not grant rights to third-party artwork.

Build 004 retains those supplied PNGs for the requested side/rear/top faces and adds original generated simulator_bays_basic.png and simulator_bays_extended.png. The generated bay artwork was subsequently edited by the user and supplied as faces-64x32.png; its pixels are preserved in the packaged textures. The slot frames are drawn from the installed AE2 guis/drive.png, and the native AUTO_EXPORT toggle is used. The relative-side selector uses AE2 AESubScreen/IconButton/TabButton widgets; its arrangement follows ExtendedAE's Circuit Slicer. When ExtendedAE is installed, the side-settings toolbar icon references its installed textures/guis/nicons.png sprite (no texture copy is bundled); otherwise AE2's COG icon is used. ExtendedAE source was inspected for behavior, and the addon implements its own selector without embedding ExtendedAE implementation classes.

Experience Lib 1.2.1 (https://github.com/alec016/experiencelib, LGPL-3.0) is an optional runtime dependency. Its XP capability API is called from the installed library; its code and assets are not bundled.
