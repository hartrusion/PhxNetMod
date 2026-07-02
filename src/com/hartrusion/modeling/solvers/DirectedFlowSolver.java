/*
 * The MIT License
 *
 * Copyright 2025 Viktor Alexander Hartung.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.hartrusion.modeling.solvers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.GeneralNode;

/**
 * A drop-in replacement for {@link SimpleIterator} that solves a network in
 * flow direction instead of blindly re-scanning every element on every pass.
 * <p>
 * {@link SimpleIterator} keeps calling {@code doCalculation()} on the whole
 * list of elements until a full pass changes nothing. For a network with
 * {@code n} elements that needs {@code p} passes to fully propagate (a long
 * pipe of {@code n} elements needs {@code p ~ n} passes), this costs
 * {@code O(n * p) = O(n^2)} element invocations even though almost every
 * invocation on a later pass does nothing. After the linear solver has fixed
 * all efforts and flows, this propagation of temperature, steam and heat-fluid
 * properties is the dominant cost of a cyclic calculation.
 * <p>
 * This solver computes the same fixed point with an <b>event driven
 * worklist</b>: an element is only (re)evaluated when one of its neighbours
 * actually changed a node they share. The wavefront is steered along the
 * <b>flow direction</b>: when an element produces a result, the elements it
 * feeds downstream (the consumers drawing flow out of the shared node) are
 * woken first, so values are pushed forward along the flow exactly the way the
 * physical properties travel. The number of element invocations drops from
 * {@code O(n^2)} towards {@code O(n + edges)}.
 * <p>
 * <b>Correctness.</b> The result is identical to {@link SimpleIterator}'s fixed
 * point. The seed enqueues every element once (so nothing is skipped) and an
 * element is re-enqueued whenever itself or a neighbour made progress, so the
 * queue can only drain once no element can make any further progress, which is
 * exactly the termination condition of the blind loop. Because the flow signs
 * are read fresh from the nodes on every cycle, a flow that reverses direction
 * simply reverses the wavefront; nothing is cached across cycles that could go
 * stale.
 * <p>
 * <b>Sign convention.</b> A node reports the flow of a connected element as
 * positive when it flows out of the node into the element (the element draws
 * from the node, i.e. it is a downstream consumer at that node) and negative
 * when it flows out of the element into the node (the element feeds the node,
 * i.e. it is an upstream provider at that node). Property propagation therefore
 * runs from the provider (flow &lt; 0) through the node to the consumers (flow
 * &gt; 0).
 * <p>
 * <b>Special elements.</b> Elements that inject their own flow and impose their
 * own direction, such as
 * {@link com.hartrusion.modeling.phasedfluid.PhasedExpandingThermalExchanger}
 * and {@link com.hartrusion.modeling.steam.SteamIsobaricIsochoricEvaporator},
 * need no special handling here: their self-imposed flow shows up in the node
 * flow signs just like any other element, so they naturally act as the start of
 * a wavefront and wake their downstream neighbours once they have produced a
 * result. The order in which elements are evaluated never changes the fixed
 * point, only how quickly it is reached.
 * <p>
 * This is completely AI generated using Github Copilot with Claude Opus 4.8.
 *
 * @author Viktor Alexander Hartung
 */
public class DirectedFlowSolver {

    /**
     * All elements handed to this solver. Kept in the same priority order as
     * {@link SimpleIterator}: origins and enforcers first, then sources, then
     * the rest, so the worklist seed starts from the boundaries and pushes
     * inward.
     */
    private final List<AbstractElement> elements = new ArrayList<>();

    private int numberOfOrigins = 0;

    /**
     * Highest index in {@link #elements} that is not of a resistor type. Used
     * only by {@link #doCalculationOnEnforcerElements()}.
     */
    private int lastNonResistanceIndex = 0;

    /**
     * Maps every managed element to its index in {@link #elements}, so a
     * neighbour found through a shared node can be located in O(1). Built once
     * and rebuilt only if the element count changes.
     */
    private Map<AbstractElement, Integer> indexOf;

    /**
     * Number of elements the {@link #indexOf} map was built for, used to detect
     * that elements were added and the map must be rebuilt.
     */
    private int builtForSize = -1;

    /**
     * Scratch flag per element: true while the element sits in the worklist.
     * Prevents the same element from being enqueued twice. Reused across
     * cycles.
     */
    private boolean[] inQueue = new boolean[0];

    /**
     * The worklist itself, reused across cycles to avoid per-cycle allocation.
     */
    private final IntArrayDeque queue = new IntArrayDeque();

    /**
     * Records, during a full worklist solve, the indices of every element
     * invocation that actually made progress, in execution order. This is the
     * "calculation order" that successfully propagated the whole network. Grows
     * as needed and is reused across cycles.
     */
    private int[] recordBuffer = new int[0];

    /**
     * Number of valid entries in {@link #recordBuffer} for the solve in
     * progress.
     */
    private int recordSize = 0;

    /**
     * The last successfully learned calculation order: the flat sequence of
     * productive element invocations that solved the network on the previous
     * full solve. As long as the flow directions do not change (no valve fully
     * closed, no reversal) replaying this order reaches the fixed point without
     * any of the worklist's neighbour-search bookkeeping. Empty when no order
     * is known yet.
     */
    private int[] cachedOrder = new int[0];

    /**
     * Number of valid entries in {@link #cachedOrder}. Zero means no learned
     * order is available and the full algorithm must run.
     */
    private int cachedOrderSize = 0;

    /**
     * Adds an element to be solved. Origins and sources are prioritised exactly
     * like in {@link SimpleIterator} so the worklist seed and the enforcer-only
     * pass start from the boundaries.
     *
     * @param e Element to add.
     */
    public void addElement(AbstractElement e) {
        switch (e.getElementType()) {
            case ORIGIN:
            case ENFORCER:
                elements.add(0, e);
                lastNonResistanceIndex = elements.size() - 1;
                numberOfOrigins++;
                break;
            case EFFORTSOURCE:
            case FLOWSOURCE:
                elements.add(numberOfOrigins, e);
                lastNonResistanceIndex = elements.size() - 1;
                break;
            default:
                elements.add(e);
        }
    }

    /**
     * Prepares all added elements for the next calculation iteration.
     */
    public void prepareCalculation() {
        for (AbstractElement e : elements) {
            e.prepareCalculation();
        }
    }

    /**
     * Invokes {@code doCalculation()} on origins, effort and flow sources only,
     * until they stop making progress. Kept identical in behaviour to
     * {@link SimpleIterator#doCalculationOnEnforcerElements()} as this is used
     * during the linear setup phase on a small prefix of elements and is not a
     * hot spot.
     */
    public void doCalculationOnEnforcerElements() {
        if (elements.isEmpty()) {
            return;
        }
        boolean didSomething;
        int iterations = 0;
        while (true) {
            didSomething = false;
            for (int idx = 0; idx <= lastNonResistanceIndex; idx++) {
                didSomething = elements.get(idx).doCalculation() || didSomething;
            }
            iterations++;
            if (iterations >= 1000) {
                throw new UnsupportedOperationException("Endless iterations?");
            }
            if (!didSomething) {
                break;
            }
        }
    }

    /**
     * Solves the network by propagating results along the flow direction using
     * an event driven worklist, then verifies the result with full sweeps so
     * the exit condition is identical to
     * {@link SimpleIterator#doCalculation()}: a complete pass over all elements
     * that makes no further progress. The worklist does the heavy lifting in
     * flow order; the verification sweeps normally pass exactly once finding
     * nothing to do, but they guarantee that even an element whose only
     * remaining input was filled in silently by a node auto-completing its last
     * unknown flow is still finished. The reached fixed point is therefore the
     * same as the blind loop's.
     * <p>
     * <b>Learned order fast path.</b> The flow path through the network only
     * changes when a flow actually reverses (typically a valve closing fully),
     * which is rare. So the first thing this method does is try to replay the
     * calculation order learned on the previous full solve: a flat list of the
     * element invocations that made progress, in the exact order they did. If
     * replaying that order leaves the network at its fixed point - confirmed by
     * a single verification sweep that finds nothing left to do, i.e. exactly
     * {@link SimpleIterator}'s termination condition - the result is correct
     * and was reached without any of the worklist's queue and neighbour-search
     * bookkeeping. Only when that verification sweep still finds work (the path
     * changed, or no order is known yet) does the full worklist algorithm run
     * and learn a fresh order.
     */
    public void doCalculation() {
        final int n = elements.size();
        if (n == 0) {
            return;
        }
        ensureGraph();

        // Fast path: replay the previously learned calculation order. A single
        // clean verification sweep guarantees the same fixed point as the full
        // algorithm, so this can never return a wrong result - at worst it falls
        // back below.
        if (cachedOrderSize > 0 && replayCachedOrder(n)) {
            return;
        }

        // Slow path: solve from scratch with the worklist while recording the
        // order of productive invocations so the next cycle can replay it.
        recordSize = 0;

        if (inQueue.length < n) {
            inQueue = new boolean[n];
        } else {
            Arrays.fill(inQueue, 0, n, false);
        }
        queue.clear();

        // Seed every element once, in boundary-first priority order, so nothing
        // is ever skipped and the wavefront naturally starts at the sources.
        for (int i = 0; i < n; i++) {
            queue.addLast(i);
            inQueue[i] = true;
        }

        int rounds = 0;
        boolean sweepProgressed;
        do {
            drainWorklist(n);
            // One SimpleIterator-style pass to confirm the fixed point. If it
            // finds nothing, we are done; anything it does find is fed back into
            // the worklist and drained in flow order on the next round.
            sweepProgressed = fullSweep(n);
            if (++rounds >= 1000) {
                throw new UnsupportedOperationException("Endless iterations?");
            }
        } while (sweepProgressed);

        commitRecordedOrder();
    }

    /**
     * Replays the previously learned calculation order and then verifies the
     * result with a single {@link SimpleIterator}-style sweep.
     * <p>
     * The cached order only dictates which element is evaluated when; every
     * element still computes from the node values read fresh this cycle, so a
     * replay can never invent values for a changed network - it can only fail
     * to finish, which the verification sweep then detects. If the sweep makes
     * no progress the network is at the unique fixed point and the cached order
     * was a hit. If it does make progress the path has changed; the partial
     * state is left in place and the caller completes it with the full
     * worklist.
     *
     * @param n Number of managed elements.
     * @return true if the cached order solved the network (verified), false if
     * it is stale and the full algorithm must run.
     */
    private boolean replayCachedOrder(int n) {
        for (int k = 0; k < cachedOrderSize; k++) {
            elements.get(cachedOrder[k]).doCalculation();
        }
        for (int i = 0; i < n; i++) {
            if (elements.get(i).doCalculation()) {
                // Not at the fixed point: the learned path no longer applies.
                cachedOrderSize = 0;
                return false;
            }
        }
        return true;
    }

    /**
     * Appends an element index to the order being recorded during a full solve,
     * growing the buffer as needed.
     *
     * @param i Index of the element that just made progress.
     */
    private void recordProgress(int i) {
        if (recordSize == recordBuffer.length) {
            final int grown = recordBuffer.length == 0
                    ? 64 : recordBuffer.length * 2;
            recordBuffer = Arrays.copyOf(recordBuffer, grown);
        }
        recordBuffer[recordSize++] = i;
    }

    /**
     * Promotes the order just recorded by a successful full solve to the cached
     * order replayed on the next cycle.
     */
    private void commitRecordedOrder() {
        if (cachedOrder.length < recordSize) {
            cachedOrder = new int[recordSize];
        }
        System.arraycopy(recordBuffer, 0, cachedOrder, 0, recordSize);
        cachedOrderSize = recordSize;
    }

    /**
     * Processes the worklist until it is empty, waking the flow-direction
     * neighbours of every element that makes progress.
     *
     * @param n Number of managed elements (used to bound the work).
     */
    private void drainWorklist(int n) {
        final long maxInvocations = (long) (n + 16) * 1000L;
        long invocations = 0;
        while (!queue.isEmpty()) {
            final int i = queue.pollFirst();
            inQueue[i] = false;
            if (elements.get(i).doCalculation()) {
                recordProgress(i);
                wakeNeighbours(i);
            }
            if (++invocations > maxInvocations) {
                throw new UnsupportedOperationException("Endless iterations?");
            }
        }
    }

    /**
     * Calls {@code doCalculation()} once on every element in priority order,
     * exactly like a single pass of {@link SimpleIterator}, re-seeding the
     * worklist from any element that still makes progress.
     *
     * @param n Number of managed elements.
     * @return true if any element made progress during the sweep.
     */
    private boolean fullSweep(int n) {
        boolean progressed = false;
        for (int i = 0; i < n; i++) {
            if (elements.get(i).doCalculation()) {
                progressed = true;
                recordProgress(i);
                wakeNeighbours(i);
            }
        }
        return progressed;
    }

    /**
     * After element {@code i} produced a result, re-enqueue the elements that
     * may now be able to make progress. Downstream consumers (elements drawing
     * flow out of a node that {@code i} feeds) are pushed to the front so the
     * wavefront follows the flow; everything else sharing a node is appended.
     * The element itself is re-queued as well so a step that unlocks a further
     * step inside the same element is never lost, guaranteeing the same fixed
     * point as the blind loop.
     *
     * @param i Index of the element that just made progress.
     */
    private void wakeNeighbours(int i) {
        final AbstractElement e = elements.get(i);
        final int nodeCount = e.getNumberOfNodes();

        for (int k = 0; k < nodeCount; k++) {
            final GeneralNode node = e.getNode(k);
            if (node == null) {
                continue;
            }
            // e feeds this node (is an upstream provider here) when its flow is
            // negative: flow runs out of the element into the node.
            final boolean eFeedsNode
                    = node.flowUpdated(e) && node.getFlow(e) < 0.0;

            final int connected = node.getNumberOfElements();
            for (int j = 0; j < connected; j++) {
                final AbstractElement other = node.getElement(j);
                if (other == e) {
                    continue;
                }
                final Integer oi = indexOf.get(other);
                if (oi == null) {
                    // Element on a shared boundary node but not part of this
                    // solver: propagation correctly stops here.
                    continue;
                }
                final int oIdx = oi;
                if (inQueue[oIdx]) {
                    continue;
                }
                inQueue[oIdx] = true;
                // Downstream consumer: it draws flow out of the node (flow > 0)
                // that e just fed. Process those first to follow the flow.
                final boolean downstream = eFeedsNode
                        && node.flowUpdated(other) && node.getFlow(other) > 0.0;
                if (downstream) {
                    queue.addFirst(oIdx);
                } else {
                    queue.addLast(oIdx);
                }
            }
        }

        // Retry the element after its woken frontier, so an internal step that
        // only became possible through this element's own progress is not lost.
        if (!inQueue[i]) {
            inQueue[i] = true;
            queue.addLast(i);
        }
    }

    /**
     * (Re)builds the element to index lookup if elements were added since the
     * last build. The network structure is fixed once setup is finished, so
     * this normally runs exactly once.
     */
    private void ensureGraph() {
        final int n = elements.size();
        if (builtForSize == n && indexOf != null) {
            return;
        }
        indexOf = new IdentityHashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            indexOf.put(elements.get(i), i);
        }
        builtForSize = n;
        // The element set changed: any learned order no longer matches.
        cachedOrderSize = 0;
    }

    /**
     * Checks if the calculation is complete for all managed elements.
     *
     * @return true if every element reports a finished calculation.
     */
    public boolean isCalculationFinished() {
        for (AbstractElement e : elements) {
            if (!e.isCalculationFinished()) {
                return false;
            }
        }
        return true;
    }

    public boolean containsElement(AbstractElement element) {
        return elements.contains(element);
    }

    /**
     * Minimal primitive int deque backed by a circular buffer, used as the
     * worklist. Avoids the boxing of {@code ArrayDeque<Integer>} on the hot
     * path and is reused across calculation cycles.
     */
    private static final class IntArrayDeque {

        private int[] buffer = new int[64];
        private int head = 0;
        private int size = 0;

        void clear() {
            head = 0;
            size = 0;
        }

        boolean isEmpty() {
            return size == 0;
        }

        void addLast(int value) {
            if (size == buffer.length) {
                grow();
            }
            final int mask = buffer.length - 1;
            buffer[(head + size) & mask] = value;
            size++;
        }

        void addFirst(int value) {
            if (size == buffer.length) {
                grow();
            }
            final int mask = buffer.length - 1;
            head = (head - 1) & mask;
            buffer[head] = value;
            size++;
        }

        int pollFirst() {
            final int mask = buffer.length - 1;
            final int value = buffer[head];
            head = (head + 1) & mask;
            size--;
            return value;
        }

        private void grow() {
            final int oldLength = buffer.length;
            final int[] enlarged = new int[oldLength * 2];
            final int mask = oldLength - 1;
            for (int i = 0; i < size; i++) {
                enlarged[i] = buffer[(head + i) & mask];
            }
            buffer = enlarged;
            head = 0;
        }
    }
}
