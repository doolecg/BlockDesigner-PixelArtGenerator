package io.blockdesigner.pixelart.palette;

import io.blockdesigner.core.model.BlockState;
import io.blockdesigner.pixelart.pipeline.OkLab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the block that looks closest to a colour: nearest in OKLab, with an optional penalty for busy textures so a
 * flat blue picks blue concrete over lapis block when both are about as close. Not thread safe (it caches answers);
 * make one per build.
 */
public final class Matcher {

    /** A candidate and the colour of the face that will show. */
    public record Candidate(BlockState block, float[] colour, float noise) {
    }

    private final List<Candidate> candidates;
    private final double noisePenalty;
    private final Map<Long, Integer> cache = new HashMap<>();

    /**
     * @param blocks       the blocks to choose from (see-through ones are left out)
     * @param source       how blocks look
     * @param topFace      match the top faces (a floor) instead of the sides (a wall)
     * @param noisePenalty 0 … 1: how much a busy texture counts against a block
     */
    public Matcher(Collection<BlockState> blocks, BlockColours.Source source, boolean topFace, double noisePenalty) {
        List<Candidate> c = new ArrayList<>();
        for (BlockState b : blocks) {
            BlockColours.Look look = source.look(b);
            if (look.seeThrough()) continue;
            c.add(new Candidate(b, look.face(topFace), look.noise()));
        }
        if (c.isEmpty()) throw new IllegalArgumentException("No usable blocks to build with");
        this.candidates = List.copyOf(c);
        this.noisePenalty = noisePenalty;
    }

    private Matcher(List<Candidate> candidates, double noisePenalty) {
        this.candidates = candidates;
        this.noisePenalty = noisePenalty;
    }

    public List<Candidate> candidates() {
        return candidates;
    }

    /** The same matcher limited to these blocks (those of them it has). */
    public Matcher only(Collection<BlockState> keep) {
        List<Candidate> c = candidates.stream().filter(x -> keep.contains(x.block())).toList();
        return c.isEmpty() ? this : new Matcher(c, noisePenalty);
    }

    /** The best candidate's index for an OKLab colour. */
    public int nearest(float[] lab) {
        return cache.computeIfAbsent(key(lab), k -> {
            int best = 0;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < candidates.size(); i++) {
                double d = score(lab, candidates.get(i));
                if (d < bd) {
                    bd = d;
                    best = i;
                }
            }
            return best;
        });
    }

    /** The two best candidates' indices (the same one twice when there is only one). */
    public int[] nearestTwo(float[] lab) {
        int b1 = 0, b2 = 0;
        double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            double d = score(lab, candidates.get(i));
            if (d < d1) {
                b2 = b1;
                d2 = d1;
                b1 = i;
                d1 = d;
            } else if (d < d2) {
                b2 = i;
                d2 = d;
            }
        }
        return new int[]{b1, d2 == Double.MAX_VALUE ? b1 : b2};
    }

    public Candidate get(int index) {
        return candidates.get(index);
    }

    private double score(float[] lab, Candidate c) {
        return Math.sqrt(OkLab.dist2(lab, c.colour())) + noisePenalty * c.noise();
    }

    /** Colours closer than about 1/1000 of the OKLab range share an answer. */
    private static long key(float[] lab) {
        long l = Math.round(lab[0] * 1000) & 0xFFFFF, a = Math.round(lab[1] * 1000) & 0xFFFFF, b = Math.round(lab[2] * 1000) & 0xFFFFF;
        return l << 40 | a << 20 | b;
    }
}
