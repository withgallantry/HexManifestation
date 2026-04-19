package com.bluup.manifestation.common.menu;

import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * A single Hex Casting pattern, stored as angle signature + start direction.
 *
 * <p>This is the same representation Hex Casting itself uses to serialize
 * patterns. Living in the common package because both sides need it: the
 * server side constructs them when reading the Hex stack, and the client
 * side re-casts them through the packet loop when a button is clicked.
 */
public final class StoredPattern {
    private final String angleSignature;
    private final HexDir startDir;

    public StoredPattern(String angleSignature, HexDir startDir) {
        this.angleSignature = Objects.requireNonNull(angleSignature, "angleSignature");
        this.startDir = Objects.requireNonNull(startDir, "startDir");
    }

    public String angleSignature() {
        return angleSignature;
    }

    public HexDir startDir() {
        return startDir;
    }

    public HexPattern toHexPattern() {
        return HexPattern.fromAnglesUnchecked(angleSignature, startDir);
    }

    public static StoredPattern fromHexPattern(HexPattern pattern) {
        return new StoredPattern(pattern.anglesSignature(), pattern.getStartDir());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(angleSignature);
        buf.writeEnum(startDir);
    }

    public static StoredPattern read(FriendlyByteBuf buf) {
        String sig = buf.readUtf();
        HexDir dir = buf.readEnum(HexDir.class);
        return new StoredPattern(sig, dir);
    }

    @Override
    public String toString() {
        return "StoredPattern[" + startDir + ", " + angleSignature + "]";
    }
}
