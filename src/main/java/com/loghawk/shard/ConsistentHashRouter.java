package com.loghawk.shard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.SortedMap;
import java.util.TreeMap;

public class ConsistentHashRouter {
    private final SortedMap<Long, Shard> ring = new TreeMap<>();
    private final int numberOfReplicas;

    public ConsistentHashRouter(Collection<Shard> nodes, int numberOfReplicas) {
        this.numberOfReplicas = numberOfReplicas;
        for (Shard node : nodes) {
            addNode(node);
        }
    }

    public void addNode(Shard node) {
        for (int i = 0; i < numberOfReplicas; i++) {
            ring.put(hash(node.getShardId() + ":" + i), node);
        }
    }

    public void removeNode(Shard node) {
        for (int i = 0; i < numberOfReplicas; i++) {
            ring.remove(hash(node.getShardId() + ":" + i));
        }
    }

    public Shard getShard(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        long hash = hash(key);
        if (!ring.containsKey(hash)) {
            SortedMap<Long, Shard> tailMap = ring.tailMap(hash);
            hash = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();
        }
        return ring.get(hash);
    }

    private long hash(String key) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(key.getBytes(StandardCharsets.UTF_8));
            long hash = 0;
            for (int i = 0; i < 4; i++) {
                hash <<= 8;
                hash |= ((int) digest[i]) & 0xFF;
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not found", e);
        }
    }
}
