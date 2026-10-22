package io.github.kainrav.worldsure;

/**
 * How many blocks, block entities (chests, machines), entities (mobs, carts) and items of one mod a
 * world holds. Items are counted in containers, on the ground and carried by mobs in the saved chunks.
 */
public record ObjectCounts(long blocks, long blockEntities, long entities, long items) {
    public static final ObjectCounts ZERO = new ObjectCounts(0, 0, 0, 0);

    public ObjectCounts(long blocks, long blockEntities, long entities) {
        this(blocks, blockEntities, entities, 0);
    }

    public ObjectCounts plus(ObjectCounts other) {
        return new ObjectCounts(blocks + other.blocks, blockEntities + other.blockEntities,
                entities + other.entities, items + other.items);
    }

    public boolean isZero() {
        return blocks == 0 && blockEntities == 0 && entities == 0 && items == 0;
    }
}
