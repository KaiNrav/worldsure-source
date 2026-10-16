package io.github.kainrav.worldsure;

/** How many blocks, block entities (chests, machines) and entities (mobs, carts) of one mod a world holds. */
public record ObjectCounts(long blocks, long blockEntities, long entities) {
    public static final ObjectCounts ZERO = new ObjectCounts(0, 0, 0);

    public ObjectCounts plus(ObjectCounts other) {
        return new ObjectCounts(blocks + other.blocks, blockEntities + other.blockEntities, entities + other.entities);
    }

    public boolean isZero() {
        return blocks == 0 && blockEntities == 0 && entities == 0;
    }
}
