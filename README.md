# Nvidium World Cache

**Persistent distant terrain for Sodium and Nvidium.** World Cache saves terrain received by the client and restores it through Minecraft's chunk rendering pipeline. World Gen can generate real chunks around the player in local worlds and add them to that visual cache.

> Experimental alpha for Fabric: Minecraft 1.21.11, 26.1.2, 26.2 and 26.3.

## Features

- **World Cache** preserves previously seen terrain for distant rendering.
- **World Gen** generates real chunks in singleplayer and follows the player after travel or teleportation.
- **Live controls** let you switch generation and cache presets while playing. Detailed controls are available in Mod Menu.

Cached terrain is visual data: unloaded chunks do not keep entities or game logic active. World Gen works only in local worlds. It uses Minecraft's normal generation pipeline, so generating a large area can reduce FPS and increase CPU, RAM, and disk usage. Performance varies by hardware, world and modpack; this alpha does not promise instant loading. C2ME is optional.

## Compatibility

Choose the JAR that exactly matches your Minecraft version. Fabric API, Sodium and Nvidium are required. Nvidium also requires compatible graphics hardware. Minecraft 1.20.1 and 1.21.1 are not part of this release.

The interface includes English and Brazilian Portuguese. Minecraft selects the provided language automatically; some command messages are still in Portuguese in this alpha.

## Project

Nvidium World Cache is an independent community project. It is not affiliated with the Nvidium, Sodium, Modrinth or CurseForge teams.

## License

MIT. See [LICENSE](LICENSE).