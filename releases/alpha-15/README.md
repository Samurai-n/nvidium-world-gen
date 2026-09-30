# Nvidium World Cache 0.1.0-alpha.15

Experimental release for Fabric on Minecraft **1.21.11, 26.1.2, 26.2 and 26.3**. Download the JAR whose filename matches your Minecraft version. Fabric API, Sodium and Nvidium are required; C2ME is optional.

World Cache preserves terrain the client has seen and restores it for distant rendering. World Gen generates real chunks in local worlds, follows travel and teleportation, and feeds that terrain to the visual cache. Cache and generation controls can be changed while playing. `/nvidium world gen status` now shows a concise progress summary; add `debug` for detailed counters.

**Known limits:** Generating large areas can substantially lower FPS and increase CPU, RAM and disk use. In one user session FPS dropped from about 600 in an empty sky view to about 90–100 while viewing generated terrain; these scenes are not directly comparable, but the reported impact is material. World Gen is limited to local worlds. Cached chunks are visual only and do not keep entities or simulation active. This is an experimental alpha, not a promise of instant generation or stable frame rate.

The interface has English and Brazilian Portuguese files; some command messages remain in Portuguese. Minecraft 1.20.1 and 1.21.1 builds are not released.

Validation: Gradle build and disposable-world client GameTests passed on 26.2, 26.1.2, 26.3 and 1.21.11. Manual gameplay results came from the user's 26.2 installation; other versions have automated validation only. No tests wrote to the user's worlds.