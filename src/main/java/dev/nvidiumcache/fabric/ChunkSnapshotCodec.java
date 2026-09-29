package dev.nvidiumcache.fabric;

import com.mojang.serialization.Codec;
import dev.nvidiumcache.core.ChunkKey;
import dev.nvidiumcache.core.PayloadCodec;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;

import java.io.*;

/** Named palettes avoid persisting runtime block IDs or texture atlas addresses.
 * Rendering requirements were checked against Bobby's LGPL-3.0 ChunkSerializer.
 * This small format deliberately excludes simulation and block entities.
 */
public final class ChunkSnapshotCodec {
    private static Codec<PalettedContainer<BlockState>> blocks() {
        return PalettedContainer.codecRW(BlockState.CODEC, Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), Blocks.AIR.defaultBlockState());
    }
    private static Codec<PalettedContainer<Holder<Biome>>> biomes(Level level) {
        var registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        return PalettedContainer.codecRW(registry.holderByNameCodec(), Strategy.createForBiomes(registry.asHolderIdMap()), registry.get(Biomes.PLAINS.identifier()).orElseThrow());
    }
    public record Captured(ChunkKey key, int minY, int dataVersion, LevelChunkSection[] sections,
                           ListTag light, Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec,
                           Codec<PalettedContainer<BlockState>> blockCodec) {
        public long retainedBytes() { return 65536L + sections.length * 40960L; }
        public byte[] encode() throws IOException {
            CompoundTag root = new CompoundTag();
            root.putInt("format", 1); root.putInt("dataVersion", dataVersion);
            root.putInt("x", key.x()); root.putInt("z", key.z());
            root.putInt("minY", minY); root.putInt("count", sections.length);
            ListTag encoded = new ListTag();
            for (var section : sections) {
                CompoundTag tag = new CompoundTag();
                tag.put("blocks", blockCodec.encodeStart(NbtOps.INSTANCE, section.getStates()).getOrThrow());
                tag.put("biomes", biomeCodec.encodeStart(NbtOps.INSTANCE, section.getBiomes()).getOrThrow());
                encoded.add(tag);
            }
            root.put("sections", encoded); root.put("light", light);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.write(root, new DataOutputStream(out));
            if (out.size() > PayloadCodec.MAX_BYTES) throw new IOException("Chunk snapshot too large");
            return out.toByteArray();
        }
    }
    /** Main-thread copy; no live chunks or mutable palette/light arrays are passed to the encoder. */
    public static Captured freeze(LevelChunk chunk) {
        Level level = chunk.getLevel();
        LevelChunkSection[] sections = new LevelChunkSection[chunk.getSections().length];
        var biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        var biomeCodec = PalettedContainer.codecRO(biomeRegistry.holderByNameCodec(), Strategy.createForBiomes(biomeRegistry.asHolderIdMap()), biomeRegistry.get(Biomes.PLAINS.identifier()).orElseThrow());
        for (int i = 0; i < chunk.getSections().length; i++) {
            sections[i] = chunk.getSections()[i].copy();
        }
        ListTag light = new ListTag();
        for (int i = 0; i < sections.length + 2; i++) {
            int y = level.getMinSectionY() - 1 + i;
            CompoundTag tag = new CompoundTag();
            SectionPos pos = SectionPos.of(chunk.getPos(), y);
            var engine = level.getLightEngine();
            DataLayer block = engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(pos);
            DataLayer sky = engine.getLayerListener(LightLayer.SKY).getDataLayerData(pos);
            if (block != null) tag.putByteArray("block", block.getData().clone());
            if (sky != null) tag.putByteArray("sky", sky.getData().clone());
            light.add(tag);
        }
        return new Captured(new ChunkKey(chunk.getPos().x(), chunk.getPos().z()), level.getMinSectionY(),
                SharedConstants.getCurrentVersion().dataVersion().version(), sections, light, biomeCodec, blocks());
    }

    /** Created on the client thread; registry-backed codecs remain read-only on the I/O worker. */
    public record DecodeContext(int minY, int sectionCount, int dataVersion, boolean skyLight,
            Codec<PalettedContainer<BlockState>> blockCodec, Codec<PalettedContainer<Holder<Biome>>> biomeCodec) {}
    public static DecodeContext decodeContext(Level level) {
        return new DecodeContext(level.getMinSectionY(), level.getSectionsCount(),
            SharedConstants.getCurrentVersion().dataVersion().version(), level.dimensionType().hasSkyLight(), blocks(), biomes(level));
    }
    /** Ownership transfers once to the client. Does not hold a Level or renderer reference. */
    public record Prepared(ChunkKey key, LevelChunkSection[] sections, DataLayer[] block, DataLayer[] sky, int rawBytes) {
        public long estimatedBytes() { return 65536L + Math.max(rawBytes * 4L, sections.length * 4096L); }
        public VisualChunk installable(Level level) {
            return new VisualChunk(level, new ChunkPos(key.x(), key.z()), sections, block, sky, rawBytes);
        }
    }
    public static Prepared prepare(byte[] raw, ChunkKey expected, DecodeContext context) throws IOException {
        if (raw.length > PayloadCodec.MAX_BYTES) throw new IOException("Oversized snapshot");
        CompoundTag root = NbtIo.read(new DataInputStream(new ByteArrayInputStream(raw)), NbtAccounter.create(32L << 20));
        if (root.getIntOr("format", -1) != 1 || root.getIntOr("dataVersion", -1) != context.dataVersion()
                || root.getIntOr("x", Integer.MIN_VALUE) != expected.x() || root.getIntOr("z", Integer.MIN_VALUE) != expected.z()
                || root.getIntOr("minY", Integer.MIN_VALUE) != context.minY() || root.getIntOr("count", -1) != context.sectionCount())
            throw new IOException("Snapshot identity or version mismatch");
        ListTag encoded = root.getListOrEmpty("sections"), light = root.getListOrEmpty("light");
        int count = context.sectionCount();
        if (encoded.size() != count || light.size() != count + 2) throw new IOException("Wrong section count");
        LevelChunkSection[] sections = new LevelChunkSection[count];
        var biomeCodec = context.biomeCodec();
        for (int i = 0; i < count; i++) {
            CompoundTag tag = encoded.getCompound(i).orElseThrow(() -> new IOException("Invalid section"));
            var blockData = context.blockCodec().parse(NbtOps.INSTANCE, tag.getCompoundOrEmpty("blocks")).getOrThrow();
            var biomeData = biomeCodec.parse(NbtOps.INSTANCE, tag.getCompoundOrEmpty("biomes")).getOrThrow();
            sections[i] = new LevelChunkSection(blockData, biomeData);
            sections[i].recalcBlockCounts();
        }
        DataLayer[] block = new DataLayer[count + 2], sky = new DataLayer[count + 2];
        for (int i = count + 1; i >= 0; i--) {
            CompoundTag tag = light.getCompound(i).orElseThrow(() -> new IOException("Invalid light section"));
            block[i] = readLight(tag, "block");
            sky[i] = readLight(tag, "sky");
            if (block[i] == null) block[i] = new DataLayer();
            if (sky[i] == null) {
                // Missing sky sections inherit the bottom slice of the section above, as vanilla light does.
                sky[i] = new DataLayer();
                if (context.skyLight()) {
                    byte[] bytes = new byte[2048];
                    if (i == count + 1) java.util.Arrays.fill(bytes, (byte) 0xff);
                    else for (int slice = 0; slice < 16; slice++) System.arraycopy(sky[i + 1].getData(), 0, bytes, slice * 128, 128);
                    sky[i] = new DataLayer(bytes);
                }
            }
        }
        return new Prepared(expected, sections, block, sky, raw.length);
    }
    private static DataLayer readLight(CompoundTag tag, String name) throws IOException {
        var bytes = tag.getByteArray(name);
        if (bytes.isEmpty()) return null;
        if (bytes.get().length != 2048) throw new IOException("Invalid light array");
        return new DataLayer(bytes.get());
    }
}
