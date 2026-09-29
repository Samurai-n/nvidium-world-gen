# Nvidium World Cache

**Persistent terrain for Sodium and Nvidium.** Nvidium World Cache saves terrain received by the client and restores it through Minecraft's regular chunk rendering pipeline. Its optional World Gen mode generates real chunks in local worlds and prepares them for rendering.

> Experimental alpha · Fabric · Minecraft 26.2

## Features

- **World Cache** — keeps a visual record of terrain the client has received, so it can be restored later.
- **World Gen** — generates chunks in singleplayer worlds and feeds them into the normal client rendering pipeline.
- **Render-friendly processing** — restoration and generation are budgeted to help keep gameplay responsive.

Cached terrain is visual data. It does not keep entities active or simulate unloaded chunks. World Gen currently works in local worlds; remote-server generation is not supported. Alpha builds may change and can contain bugs.

## Compatibility

This project targets Minecraft **26.2** on Fabric and works with Sodium and Nvidium. Nvidium requires compatible hardware. Compatibility with other Minecraft versions is not implied.

## Project

Nvidium World Cache is an independent community project and is not affiliated with the Nvidium, Sodium, Modrinth, or CurseForge teams.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
