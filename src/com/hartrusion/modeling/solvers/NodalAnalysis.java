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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.general.Origin;

/**
 * Solves a linear network using the node potential method (nodal analysis,
 * "Knotenpotentialverfahren"). This is an alternative to the
 * {@link SuperPosition} solver which avoids creating one layer network per
 * source and is therefore much cheaper for networks with many sources.
 * <p>
 * The solver expects the very same kind of network that {@link SuperPosition}
 * gets handed by {@link TransferSubnet}: a network built from
 * {@link LinearDissipator} elements (DISSIPATOR, OPEN or BRIDGED), real
 * {@link EffortSource} voltage sources, {@link FlowSource} current sources and
 * exactly one {@link ClosedOrigin} that fixes the reference potential.
 * <p>
 * The work is done in three derived networks so that every step stays
 * transparent and debuggable:
 * <ol>
 * <li><b>base network</b> &ndash; the registered nodes and elements (this class
 * itself, as it extends {@link LinearNetwork}). The solution is finally
 * transferred back onto these objects.</li>
 * <li><b>merged network</b> ({@code mergedNetwork}) &ndash; all nodes that are
 * connected by a BRIDGED element (usually domain converters) are collapsed into
 * a single node. BRIDGED elements are assumed to stay BRIDGED for the lifetime
 * of this solver; if that assumption is violated a {@link ModelErrorException}
 * is thrown so the caller can fall back to {@link SuperPosition}.</li>
 * <li><b>converted network</b> ({@code convertedNetwork}) &ndash; every real
 * voltage source (an {@link EffortSource} in series with one resistor) is
 * transformed into a real current source by means of Norton's theorem. After
 * this step the network only contains conductances and current sources, which
 * is exactly what the node potential method needs.</li>
 * </ol>
 * <p>
 * From the converted network the conductance matrix {@code G} and the source
 * current vector {@code rhs} are assembled and the linear system
 * {@code G * v = rhs} is solved for the node potentials {@code v}. To keep the
 * cyclic calls {@link #prepareCalculation()} and {@link #doCalculation()} free
 * of object allocation (they run in the 100&nbsp;ms cycle of the simulator),
 * the topology, the matrix buffers and the per-matrix-entry contributor lists
 * are all built once in {@link #nodalAnalysisSetup()}.
 * {@code prepareCalculation} only reads the current values out of the network
 * down to the matrix entries, {@code doCalculation} only solves the prepared
 * matrix and writes the results back.
 * <p>
 * This code was generated with heavy use of Claude Opus 4.8 AI.
 *
 * @author Viktor Alexander Hartung
 */
public class NodalAnalysis extends LinearNetwork {

    private static final Logger LOGGER = Logger.getLogger(
            NodalAnalysis.class.getName());

    /**
     * Pivot elements with an absolute value smaller than this are treated as
     * zero during the Gaussian elimination. A zero pivot indicates a node that
     * has no conductive path to the reference (a floating node).
     */
    private static final double PIVOT_EPSILON = 1e-12;

    /**
     * Network where all nodes connected by BRIDGED elements are collapsed into
     * a single node.
     */
    private final DerivedNetwork mergedNetwork = new DerivedNetwork();

    /**
     * For each base node, the index of the merged node it belongs to. Usage:
     * {@code mergedNodeOfBaseNode[baseNodeIndex] = mergedNodeIndex}.
     */
    private int[] mergedNodeOfBaseNode;

    /**
     * Union-find parent array used while collapsing BRIDGED-connected base
     * nodes. Only used during setup.
     */
    private int[] unionParent;

    /**
     * True for each base element that was a BRIDGED element when the solver was
     * set up. Such elements are absorbed into a merged node and are expected to
     * stay BRIDGED. Usage: {@code baseWasBridge[baseElementIndex]}.
     */
    private boolean[] baseWasBridge;

    /**
     * For each merged element, the base element it was derived from, so values
     * can be copied from the base network to the merged network. Indices match
     * {@code mergedNetwork.elements}.
     */
    private final List<AbstractElement> baseOfMergedElement = new ArrayList<>();

    /**
     * For each merged element, true if it is one of the two halves a shared
     * series resistor was split into. Such a half only receives half of the
     * base resistance so the two halves in series add up to the original value.
     * Indices match {@code mergedNetwork.elements} and
     * {@code baseOfMergedElement}.
     */
    private boolean[] mergedElementIsHalf;

    /**
     * Network where every real voltage source has been converted into a real
     * current source (Norton). Contains only conductances and current sources.
     */
    private final DerivedNetwork convertedNetwork = new DerivedNetwork();

    /**
     * For each merged node, the index of the corresponding converted node, or
     * -1 if the merged node was eliminated as the internal node of a real
     * voltage source. Usage:
     * {@code convertedNodeOfMergedNode[mergedNodeIndex] = convertedNodeIndex}.
     */
    private int[] convertedNodeOfMergedNode;

    /**
     * Describes how each converted element gets its value copied from the
     * merged network. Indices match {@code convertedNetwork.elements}.
     */
    private final List<ConvertedSource> convertedSources = new ArrayList<>();

    /**
     * Reconstruction data for the internal nodes that were eliminated during
     * the Norton conversion. Indexed by merged node. The arrays only hold
     * meaningful values for merged nodes that are eliminated internal nodes
     * (where {@code convertedNodeOfMergedNode} is -1).
     */
    private int[] internalTerminalConvertedNode;
    private EffortSource[] internalMergedSource;
    private boolean[] internalSourceIsNode1;

    /**
     * Index of the converted node that serves as the potential reference (the
     * merged node of the single ClosedOrigin).
     */
    private int referenceConvertedNode;

    /**
     * The origin element that fixes the reference potential of the network.
     */
    private Origin originElement;

    /**
     * The conductance matrix. {@code g[i][j]} relates the current at matrix
     * node i to the potential of matrix node j.
     */
    private double[][] g;

    /**
     * The right hand side source current vector. {@code rhs[i]} is the sum of
     * all source currents injected into matrix node i.
     */
    private double[] rhs;

    /**
     * The solution vector, holding the potentials of the matrix nodes relative
     * to the reference node after {@link #doCalculation()}.
     */
    private double[] potential;

    /**
     * Number of unknown node potentials, equal to the number of converted nodes
     * minus the reference node.
     */
    private int matrixSize;

    /**
     * For each converted node, its row/column index in the matrix, or -1 for
     * the reference node. Usage:
     * {@code matrixIndexOfConvertedNode[convertedNodeIndex] = matrixIndex}.
     */
    private int[] matrixIndexOfConvertedNode;

    /**
     * For each matrix row i, the list of converted resistors whose conductance
     * is summed into the diagonal entry {@code g[i][i]}.
     */
    private List<LinearDissipator>[] diagonalContributors;

    /**
     * For each matrix row i, the list of current sources contributing to the
     * source current vector entry {@code rhs[i]}.
     */
    private List<CurrentContribution>[] rhsContributors;

    /**
     * The off-diagonal entries of the matrix that actually have a connection.
     * Each entry knows its two matrix indices and the resistors connecting
     * them, so {@code g[i][j]} and {@code g[j][i]} can be summed up.
     */
    private final List<OffDiagonalEntry> offDiagonalEntries = new ArrayList<>();

    /**
     * Computed absolute effort value for each base node, written during
     * {@link #doCalculation()} and then transferred to the base nodes.
     */
    private double[] baseNodeEffort;

    /**
     * Avoids repeated logging when a floating node is encountered.
     */
    private boolean floatingNodeWarned;

    /**
     * Diagonal factor {@code D} of the {@code L D L^T} factorization used by
     * {@link #solveLinearSystemCholesky()}. Preallocated so the cyclic solve
     * does not allocate. Length equals {@link #matrixSize}.
     */
    private double[] choleskyDiagonal;

    /**
     * Scratch buffer holding {@code D[k] * L[j][k]} for the column currently
     * being processed in {@link #solveLinearSystemCholesky()}. Preallocated so
     * the cyclic solve does not allocate. Length equals {@link #matrixSize}.
     */
    private double[] choleskyColumn;

    // -- sparse Cholesky (L D L^T) work arrays ----------------------------
    // The conductance matrix is not only symmetric positive definite but also
    // very sparse (every node touches only a handful of resistors), so a sparse
    // factorization only stores and works on the non-zero entries. The sparsity
    // pattern is fixed by the network topology, so the expensive symbolic
    // analysis (elimination tree and fill-in of the factor) is done once in
    // {@link #buildSparseSymbolic()} and reused every cycle; only the cheap
    // numeric refactorization runs in the cyclic solve. The implementation
    // follows the compressed-column "LDL" scheme of T. Davis.
    /**
     * Column pointers of the compressed-column upper triangle of the matrix
     * {@code A}. Column {@code c} occupies {@code [sparseAColPtr[c],
     * sparseAColPtr[c + 1])}. Length {@code matrixSize + 1}. Built once.
     */
    private int[] sparseAColPtr;

    /**
     * Row indices of the compressed-column upper triangle of {@code A}, sorted
     * with the diagonal last in each column. Built once.
     */
    private int[] sparseARowIndex;

    /**
     * Numeric values of {@code A}, refilled from the dense matrix every cycle.
     */
    private double[] sparseAValue;

    /**
     * Column pointers of the lower triangular factor {@code L}. Built once.
     */
    private int[] sparseLColPtr;

    /**
     * Row indices of the factor {@code L}, filled by the numeric factorization.
     */
    private int[] sparseLRowIndex;

    /**
     * Numeric values of the factor {@code L}, filled every cycle.
     */
    private double[] sparseLValue;

    /**
     * Diagonal factor {@code D} of the sparse {@code L D L^T} factorization.
     */
    private double[] sparseDiagonal;

    /**
     * Elimination tree parent of each column, defining the fill-in pattern.
     * Built once.
     */
    private int[] eliminationParent;

    /**
     * Per column non-zero counter of {@code L}; reused as a write cursor in the
     * numeric factorization.
     */
    private int[] sparseLNz;

    /**
     * Visit marker used by the symbolic and numeric factorization.
     */
    private int[] sparseFlag;

    /**
     * Topological pattern stack of the row currently being eliminated.
     */
    private int[] sparsePattern;

    /**
     * Dense scratch vector used to gather a sparse column during factorization.
     */
    private double[] sparseY;

    // -- forced (fixed potential) nodes -----------------------------------
    /**
     * Base nodes whose potential is forced (fixed) by a boundary source, for
     * example a node a capacitance or a replaced origin sits on. Such a node is
     * not an unknown of the conductance matrix; its potential is read from the
     * provider every cycle and moved to the right hand side. Filled by
     * {@link #forceNodeEffort(GeneralNode, EffortSource)} before setup.
     */
    private final List<GeneralNode> forcedNodeList = new ArrayList<>();

    /**
     * For each entry in {@link #forcedNodeList}, the grounded effort source
     * that supplies the forced potential (its effort is the potential relative
     * to the reference). Indices match {@code forcedNodeList}.
     */
    private final List<EffortSource> forcedProviderList = new ArrayList<>();

    /**
     * True for each base element that is a forced node value provider. Such an
     * element is not turned into a matrix element (it would be an ideal
     * source); it only supplies the potential of its forced node.
     */
    private boolean[] baseIsForcedProvider;

    /**
     * True for each converted node whose potential is fixed, that is the
     * reference node and every forced node. Fixed nodes are not unknowns of the
     * matrix.
     */
    private boolean[] convertedNodeIsFixed;

    /**
     * True for each converted node that is a forced node (a fixed node that is
     * not the reference node).
     */
    private boolean[] convertedNodeIsForced;

    /**
     * For each converted node, its potential relative to the reference node.
     * Zero for the reference, the provider effort for a forced node, updated
     * every cycle in {@link #prepareCalculation()}.
     */
    private double[] fixedRelativePotential;

    /**
     * For each converted node, the boundary source that supplies its forced
     * potential, or null if the node is not forced.
     */
    private EffortSource[] forcedProviderOfConvertedNode;

    /**
     * For each converted node, +1 if the forced node is node 1 of its provider
     * (the usual grounded case), or -1 if it is node 0.
     */
    private double[] forcedSignOfConvertedNode;

    /**
     * For each matrix row i, the resistors that connect this free node to a
     * forced node. Each contributes its conductance times the forced potential
     * to the right hand side entry {@code rhs[i]}.
     */
    private List<ForcedContribution>[] forcedRhsContributors;

    /**
     * Registers a node whose potential is forced (fixed) by a boundary source.
     * Electrically the provider is a grounded ideal voltage source: one of its
     * nodes is the reference (origin) node and the other is the forced node.
     * Instead of converting such a source with a series resistor (Norton), the
     * solver treats the forced node as a node with a known potential and moves
     * its contribution to the right hand side of the matrix. The provider
     * effort is read every cycle, so a changing boundary value (for example a
     * capacitance state) is followed automatically.
     * <p>
     * Has to be called after the nodes and elements have been registered and
     * before {@link #nodalAnalysisSetup()}.
     *
     * @param node The base node whose potential is forced. Must be one of the
     * two nodes of {@code provider}.
     * @param provider The grounded effort source supplying the forced
     * potential.
     */
    public void forceNodeEffort(GeneralNode node, EffortSource provider) {
        if (node == null || provider == null) {
            throw new ModelErrorException("forceNodeEffort requires a node and "
                    + "a provider.");
        }
        forcedNodeList.add(node);
        forcedProviderList.add(provider);
    }

    /**
     * <b>Static solvability test</b>
     * <p>
     * Checks whether a network described by the given nodes and elements can be
     * solved with this nodal analysis solver. The caller (for example
     * {@link TransferSubnet} or {@link DomainAnalogySolver}) can use this to
     * decide whether to use this solver or fall back to {@link SuperPosition}.
     * <p>
     * To be solvable, the network must
     * <ul>
     * <li>contain exactly one origin (ClosedOrigin) as reference,</li>
     * <li>only contain resistor type elements, effort sources and flow
     * sources,</li>
     * <li>and every effort source must be a <i>real</i> voltage source, meaning
     * one of its two nodes is an internal node that only connects the source
     * and exactly one (non-bridged) resistor. That resistor is the series
     * resistance needed for the Norton conversion. Ideal voltage sources are
     * not allowed.</li>
     * </ul>
     *
     * @param nodes The nodes of the network to check.
     * @param elements The elements of the network to check.
     * @return true if the network can be solved by this solver.
     */
    public static boolean isSolvableByNodalAnalysis(
            List<GeneralNode> nodes, List<AbstractElement> elements) {
        return isSolvableByNodalAnalysis(nodes, elements, null);
    }

    /**
     * Same as {@link #isSolvableByNodalAnalysis(List, List)} but additionally
     * accepts a set of effort sources that will be registered as forced node
     * providers (see {@link #forceNodeEffort(GeneralNode, EffortSource)}). Such
     * sources are grounded ideal voltage sources and define a node with a known
     * potential, so they do not need a series resistor for a Norton conversion.
     *
     * @param nodes The nodes of the network to check.
     * @param elements The elements of the network to check.
     * @param forcedNodeProviders Effort sources that will become forced nodes,
     * or null if there are none.
     * @return true if the network can be solved by this solver.
     */
    public static boolean isSolvableByNodalAnalysis(
            List<GeneralNode> nodes, List<AbstractElement> elements,
            java.util.Collection<AbstractElement> forcedNodeProviders) {
        int numberOfOrigins = 0;

        // Step 1: only known, supported element types are allowed.
        for (AbstractElement e : elements) {
            switch (e.getElementType()) {
                case ORIGIN:
                    numberOfOrigins++;
                    break;
                case DISSIPATOR:
                case OPEN:
                case BRIDGED:
                case FLOWSOURCE:
                case EFFORTSOURCE:
                    break;
                default:
                    // capacitance, inductance, enforcer, none, etc. are not
                    // supported by this solver.
                    LOGGER.log(Level.INFO, "NodalAnalysis not usable: element "
                            + "\"{0}\" has unsupported type {1}. Falling back "
                            + "to another solver.", new Object[]{
                                e.toString(), e.getElementType()});
                    return false;
            }
        }

        // Step 2: there must be exactly one reference origin.
        if (numberOfOrigins != 1) {
            LOGGER.log(Level.INFO, "NodalAnalysis not usable: the network needs "
                    + "exactly one origin as reference but has {0}. Falling "
                    + "back to another solver.", numberOfOrigins);
            return false;
        }

        // Step 3: every effort source must be a real voltage source, that is
        // it must have at least one node that is an internal node connecting
        // only the source and one resistor. Effort sources that share their
        // series resistor (for example two sources with a single resistor
        // between them) are still accepted here; the shared resistor is split
        // into two halves during setup so that each source gets its own series
        // resistor for the Norton conversion (see buildMergedNetwork).
        boolean hasIdealEffortSource = false;
        for (AbstractElement e : elements) {
            if (e.getElementType() == ElementType.EFFORTSOURCE
                    && !hasSeriesResistor(e)) {
                if (forcedNodeProviders != null
                        && forcedNodeProviders.contains(e)) {
                    // a grounded ideal source that becomes a forced node, it
                    // does not need a series resistor.
                    continue;
                }
                LOGGER.log(Level.INFO, "NodalAnalysis not usable: effort source "
                        + "\"{0}\" is an ideal voltage source, it has no node "
                        + "that connects only the source and a single series "
                        + "resistor.{1} Falling back to another solver.",
                        new Object[]{e.toString(), describeSourceNodes(e)});
                hasIdealEffortSource = true;
            }
        }
        return !hasIdealEffortSource;
    }

    /**
     * Builds a human readable description of the two nodes of an effort source
     * and the other elements connected to them. Used to explain why an effort
     * source was not recognized as a real voltage source.
     *
     * @param source The effort source to describe.
     * @return A descriptive string, starting with a space, listing each node
     * and its neighbouring elements.
     */
    private static String describeSourceNodes(AbstractElement source) {
        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < source.getNumberOfNodes(); idx++) {
            GeneralNode node = source.getNode(idx);
            if (node == null) {
                continue;
            }
            sb.append(" Node \"").append(node.toString()).append("\" connects ");
            for (int jdx = 0; jdx < node.getNumberOfElements(); jdx++) {
                AbstractElement other = node.getElement(jdx);
                if (other == source) {
                    continue;
                }
                sb.append("\"").append(other.toString()).append("\" (")
                        .append(other.getElementType()).append(") ");
            }
            sb.append(".");
        }
        return sb.toString();
    }

    /**
     * Checks whether an effort source has a usable series resistance, which
     * makes it a real (Norton convertible) voltage source. This is the case if
     * one of its nodes is an internal node connecting only the source and
     * exactly one resistor that is not bridged.
     *
     * @param source The effort source to check.
     * @return true if a series resistor was found.
     */
    private static boolean hasSeriesResistor(AbstractElement source) {
        for (int idx = 0; idx < source.getNumberOfNodes(); idx++) {
            GeneralNode node = source.getNode(idx);
            if (node == null || node.getNumberOfElements() != 2) {
                continue; // not an internal node
            }
            AbstractElement other = node.getElement(0) == source
                    ? node.getElement(1) : node.getElement(0);
            // a finite or open resistor can act as series resistance, a bridged
            // one would turn the source into an ideal one.
            if (other.getElementType() == ElementType.DISSIPATOR
                    || other.getElementType() == ElementType.OPEN) {
                return true;
            }
        }
        return false;
    }

    //  Setup
    // ===================================================================
    /**
     * Builds the merged network, the converted network, the conductance matrix
     * and all contributor lists. Has to be called once after all nodes and
     * elements have been registered, before the first calculation.
     *
     * @return The size of the conductance matrix (number of unknown node
     * potentials).
     */
    public int nodalAnalysisSetup() {
        LOGGER.log(Level.INFO, "Setting up NodalAnalysis solver...");

        buildMergedNetwork();
        buildConvertedNetwork();
        buildMatrix();

        baseNodeEffort = new double[nodes.size()];

        LOGGER.log(Level.INFO, "... NodalAnalysis set up with " + matrixSize
                + " unknown node potentials.");
        return matrixSize;
    }

    /**
     * Collapses all base nodes that are connected by a BRIDGED element into a
     * single merged node and creates a duplicate of every non-bridged element
     * inside the merged network.
     */
    private void buildMergedNetwork() {
        int idx, baseN = nodes.size();

        // Mark the base elements that are forced node providers. They are
        // grounded ideal sources whose node becomes a forced (fixed) node, so
        // they must not be turned into a matrix element.
        baseIsForcedProvider = new boolean[elements.size()];
        for (idx = 0; idx < forcedProviderList.size(); idx++) {
            int bi = elements.indexOf(forcedProviderList.get(idx));
            if (bi >= 0) {
                baseIsForcedProvider[bi] = true;
            }
        }

        // Union-find: start with every base node being its own group.
        unionParent = new int[baseN];
        for (idx = 0; idx < baseN; idx++) {
            unionParent[idx] = idx;
        }

        // Join the two nodes of every BRIDGED element into one group.
        baseWasBridge = new boolean[elements.size()];
        for (idx = 0; idx < elements.size(); idx++) {
            if (elements.get(idx).getElementType() == ElementType.BRIDGED) {
                baseWasBridge[idx] = true;
                union(node0idx[idx], node1idx[idx]);
            }
        }

        // Create one merged node per group (per union-find root).
        int[] mergedNodeOfRoot = new int[baseN];
        for (idx = 0; idx < baseN; idx++) {
            mergedNodeOfRoot[idx] = -1;
        }
        mergedNodeOfBaseNode = new int[baseN];
        for (idx = 0; idx < baseN; idx++) {
            int root = find(idx);
            if (mergedNodeOfRoot[root] < 0) {
                GeneralNode mn = new GeneralNode(PhysicalDomain.ELECTRICAL);
                mn.setName("m-" + nodes.get(idx).toString());
                mergedNetwork.registerNode(mn);
                mergedNodeOfRoot[root] = mergedNetwork.getNodeIndex(mn);
            }
            mergedNodeOfBaseNode[idx] = mergedNodeOfRoot[root];
        }

        // Pre-pass: gather the connectivity of the merged nodes so that
        // resistors which are shared as the series resistor of two effort
        // sources can be detected. For every merged node we count how many
        // surviving merged elements are incident, remember one incident effort
        // source and count incident resistors. A merged node is the internal
        // node of an effort source when it has exactly two incident elements,
        // one of which is an effort source and one a resistor.
        int mergedN = mergedNetwork.getNumberOfNodes();
        int[] mergedDegree = new int[mergedN];
        int[] incidentSourceBase = new int[mergedN];
        int[] incidentResistorCount = new int[mergedN];
        for (idx = 0; idx < mergedN; idx++) {
            incidentSourceBase[idx] = -1;
        }
        for (idx = 0; idx < elements.size(); idx++) {
            if (baseWasBridge[idx] || baseIsForcedProvider[idx]) {
                continue;
            }
            AbstractElement be = elements.get(idx);
            if (be.getElementType() == ElementType.ORIGIN) {
                continue;
            }
            int ma = mergedNodeOfBaseNode[node0idx[idx]];
            int mb = mergedNodeOfBaseNode[node1idx[idx]];
            if (ma == mb) {
                continue; // collapsed by a parallel bridge, not surviving
            }
            mergedDegree[ma]++;
            mergedDegree[mb]++;
            if (be.getElementType() == ElementType.EFFORTSOURCE) {
                incidentSourceBase[ma] = idx;
                incidentSourceBase[mb] = idx;
            } else if (be.getElementType() == ElementType.DISSIPATOR
                    || be.getElementType() == ElementType.OPEN) {
                incidentResistorCount[ma]++;
                incidentResistorCount[mb]++;
            }
        }

        // Create a merged element for every non-bridged base element. Half flags
        // are collected in parallel with baseOfMergedElement.
        List<Boolean> halfFlags = new ArrayList<>();
        for (idx = 0; idx < elements.size(); idx++) {
            AbstractElement be = elements.get(idx);
            if (baseWasBridge[idx]) {
                continue; // absorbed into a merged node
            }
            if (baseIsForcedProvider[idx]) {
                continue; // grounded ideal source, becomes a forced node
            }
            if (be.getElementType() == ElementType.ORIGIN) {
                // remember the reference, no merged element is needed for it.
                originElement = (Origin) be;
                continue;
            }

            int ma = mergedNodeOfBaseNode[node0idx[idx]];
            int mb = mergedNodeOfBaseNode[node1idx[idx]];
            if (ma == mb) {
                // a resistor whose both ends got merged (shorted by a parallel
                // bridge). It carries no defining current for the potentials,
                // so it is left out of the merged network. Its base value will
                // still be resolved by the iterative solver afterwards.
                continue;
            }

            // A resistor that sits directly between the internal nodes of two
            // different effort sources is the series resistor of both of them.
            // A single resistor cannot serve as the series resistance for two
            // Norton conversions, so it is split into two halves with a new
            // middle node. Each half (carrying half the resistance) becomes the
            // dedicated series resistor of one source while the total
            // resistance stays unchanged.
            boolean isResistor = be.getElementType() == ElementType.DISSIPATOR
                    || be.getElementType() == ElementType.OPEN;
            boolean shared = isResistor
                    && isSourceInternalNode(ma, mergedDegree,
                            incidentSourceBase, incidentResistorCount)
                    && isSourceInternalNode(mb, mergedDegree,
                            incidentSourceBase, incidentResistorCount)
                    && incidentSourceBase[ma] != incidentSourceBase[mb];

            if (shared) {
                GeneralNode middle = new GeneralNode(PhysicalDomain.ELECTRICAL);
                middle.setName("m-split-" + be.toString());
                mergedNetwork.registerNode(middle);
                int mm = mergedNetwork.getNodeIndex(middle);

                AbstractElement half1 = createLikeElement(be);
                half1.connectBetween(mergedNetwork.getNode(ma),
                        mergedNetwork.getNode(mm));
                mergedNetwork.registerElement(half1);
                baseOfMergedElement.add(be);
                halfFlags.add(Boolean.TRUE);

                AbstractElement half2 = createLikeElement(be);
                half2.connectBetween(mergedNetwork.getNode(mm),
                        mergedNetwork.getNode(mb));
                mergedNetwork.registerElement(half2);
                baseOfMergedElement.add(be);
                halfFlags.add(Boolean.TRUE);
            } else {
                AbstractElement me = createLikeElement(be);
                me.connectBetween(mergedNetwork.getNode(ma),
                        mergedNetwork.getNode(mb));
                mergedNetwork.registerElement(me);
                baseOfMergedElement.add(be);
                halfFlags.add(Boolean.FALSE);
            }
        }

        // Freeze the half flags into a primitive array for allocation free use.
        mergedElementIsHalf = new boolean[halfFlags.size()];
        for (idx = 0; idx < halfFlags.size(); idx++) {
            mergedElementIsHalf[idx] = halfFlags.get(idx);
        }

        if (originElement == null) {
            throw new ModelErrorException("NodalAnalysis requires exactly one "
                    + "origin element as reference, none was found.");
        }
    }

    /**
     * Tells whether a merged node is the internal node of an effort source,
     * meaning it has exactly two incident elements, one effort source and one
     * resistor. Such a node is eliminated during the Norton conversion.
     *
     * @param mergedNode The merged node index to test.
     * @param mergedDegree Incident element count per merged node.
     * @param incidentSourceBase Base index of an incident effort source per
     * merged node, or -1.
     * @param incidentResistorCount Incident resistor count per merged node.
     * @return true if the node is the internal node of an effort source.
     */
    private static boolean isSourceInternalNode(int mergedNode,
            int[] mergedDegree, int[] incidentSourceBase,
            int[] incidentResistorCount) {
        return mergedDegree[mergedNode] == 2
                && incidentSourceBase[mergedNode] >= 0
                && incidentResistorCount[mergedNode] >= 1;
    }

    /**
     * Builds the converted network by transforming every real voltage source
     * into a current source (Norton) and eliminating the internal node of each
     * such source.
     */
    private void buildConvertedNetwork() {
        int idx, mergedN = mergedNetwork.getNumberOfNodes();

        convertedNodeOfMergedNode = new int[mergedN];
        internalTerminalConvertedNode = new int[mergedN];
        internalMergedSource = new EffortSource[mergedN];
        internalSourceIsNode1 = new boolean[mergedN];
        for (idx = 0; idx < mergedN; idx++) {
            convertedNodeOfMergedNode[idx] = -1;
            internalTerminalConvertedNode[idx] = -1;
        }

        // Step 1: for every effort source in the merged network, find its
        // internal node (the eliminated one) and its series resistor. Mark the
        // internal node so it does not become a converted node and mark the
        // series resistor so it is not duplicated as an ordinary resistor.
        boolean[] mergedNodeEliminated = new boolean[mergedN];
        boolean[] mergedElementConsumed
                = new boolean[mergedNetwork.getNumberOfElements()];
        // collected per source for the second step
        List<EffortSource> srcSource = new ArrayList<>();
        List<LinearDissipator> srcSeries = new ArrayList<>();
        List<Integer> srcInternalMerged = new ArrayList<>();
        List<Integer> srcTerminalMerged = new ArrayList<>(); // terminal2
        List<Integer> srcSeriesFarMerged = new ArrayList<>(); // terminal1
        List<Boolean> srcInternalIsNode1 = new ArrayList<>();

        for (idx = 0; idx < mergedNetwork.getNumberOfElements(); idx++) {
            AbstractElement me = mergedNetwork.getElement(idx);
            if (me.getElementType() != ElementType.EFFORTSOURCE) {
                continue;
            }
            int na = mergedNetwork.getNode0Index(idx); // source node 0
            int nb = mergedNetwork.getNode1Index(idx); // source node 1

            // Determine which of the two source nodes is the internal node.
            int internalMerged = -1;
            boolean internalIsNode1 = false;
            int seriesElementIdx = -1;
            // prefer node 0 if it qualifies, then node 1.
            seriesElementIdx = findSeriesResistor(na, me);
            if (seriesElementIdx >= 0) {
                internalMerged = na;
                internalIsNode1 = false;
            } else {
                seriesElementIdx = findSeriesResistor(nb, me);
                if (seriesElementIdx >= 0) {
                    internalMerged = nb;
                    internalIsNode1 = true;
                }
            }
            if (internalMerged < 0) {
                throw new ModelErrorException("Effort source " + me.toString()
                        + " is not a real voltage source (no series resistor "
                        + "found). The network should have been rejected by "
                        + "isSolvableByNodalAnalysis().");
            }

            LinearDissipator series
                    = (LinearDissipator) mergedNetwork.getElement(seriesElementIdx);
            // the non-internal node of the source is terminal 2.
            int terminal2Merged = internalIsNode1 ? na : nb;
            // the other node of the series resistor is terminal 1.
            int seriesFarMerged
                    = mergedNetwork.getNode0Index(seriesElementIdx) == internalMerged
                    ? mergedNetwork.getNode1Index(seriesElementIdx)
                    : mergedNetwork.getNode0Index(seriesElementIdx);

            mergedNodeEliminated[internalMerged] = true;
            mergedElementConsumed[idx] = true; // the effort source
            mergedElementConsumed[seriesElementIdx] = true; // the series resistor

            srcSource.add((EffortSource) me);
            srcSeries.add(series);
            srcInternalMerged.add(internalMerged);
            srcTerminalMerged.add(terminal2Merged);
            srcSeriesFarMerged.add(seriesFarMerged);
            srcInternalIsNode1.add(internalIsNode1);
        }

        // Step 2: create the converted nodes (all merged nodes except the
        // eliminated internal ones).
        for (idx = 0; idx < mergedN; idx++) {
            if (mergedNodeEliminated[idx]) {
                continue;
            }
            GeneralNode cn = new GeneralNode(PhysicalDomain.ELECTRICAL);
            cn.setName("c-" + mergedNetwork.getNode(idx).toString());
            convertedNetwork.registerNode(cn);
            convertedNodeOfMergedNode[idx] = convertedNetwork.getNodeIndex(cn);
        }
        referenceConvertedNode
                = convertedNodeOfMergedNode[mergedNodeOfBaseNode[nodes.indexOf(originElement.getNode(0))]];
        if (referenceConvertedNode < 0) {
            throw new ModelErrorException("The reference (origin) node was "
                    + "eliminated, which is not supported.");
        }

        // Mark fixed (known potential) converted nodes: the reference node and
        // every forced node. Forced nodes keep their provider so their relative
        // potential can be refreshed every cycle.
        int convertedN = convertedNetwork.getNumberOfNodes();
        convertedNodeIsFixed = new boolean[convertedN];
        convertedNodeIsForced = new boolean[convertedN];
        fixedRelativePotential = new double[convertedN];
        forcedProviderOfConvertedNode = new EffortSource[convertedN];
        forcedSignOfConvertedNode = new double[convertedN];
        convertedNodeIsFixed[referenceConvertedNode] = true;
        for (int fdx = 0; fdx < forcedNodeList.size(); fdx++) {
            GeneralNode forcedNode = forcedNodeList.get(fdx);
            EffortSource provider = forcedProviderList.get(fdx);
            int baseIdx = nodes.indexOf(forcedNode);
            if (baseIdx < 0) {
                throw new ModelErrorException("Forced node " + forcedNode
                        + " is not part of the registered network.");
            }
            int cn = convertedNodeOfMergedNode[mergedNodeOfBaseNode[baseIdx]];
            if (cn < 0 || cn == referenceConvertedNode) {
                // forcing the reference node (or an eliminated node) has no
                // effect, the reference potential already governs it.
                continue;
            }
            convertedNodeIsFixed[cn] = true;
            convertedNodeIsForced[cn] = true;
            forcedProviderOfConvertedNode[cn] = provider;
            forcedSignOfConvertedNode[cn]
                    = forcedNode == provider.getNode(1) ? 1.0 : -1.0;
        }

        // Step 3: duplicate all ordinary (not consumed) resistors and flow
        // sources of the merged network into the converted network.
        for (idx = 0; idx < mergedNetwork.getNumberOfElements(); idx++) {
            if (mergedElementConsumed[idx]) {
                continue;
            }
            AbstractElement me = mergedNetwork.getElement(idx);
            int ca = convertedNodeOfMergedNode[mergedNetwork.getNode0Index(idx)];
            int cb = convertedNodeOfMergedNode[mergedNetwork.getNode1Index(idx)];
            if (ca < 0 || cb < 0 || ca == cb) {
                // touches an eliminated node or collapses to a single node;
                // such elements do not contribute to the converted topology.
                continue;
            }
            if (isElementResistorType(me)) {
                LinearDissipator cr = new LinearDissipator(
                        PhysicalDomain.ELECTRICAL);
                cr.setName("c-" + me.toString());
                cr.connectBetween(convertedNetwork.getNode(ca),
                        convertedNetwork.getNode(cb));
                convertedNetwork.registerElement(cr);
                convertedSources.add(ConvertedSource.resistor(
                        cr, (LinearDissipator) me));
            } else if (me.getElementType() == ElementType.FLOWSOURCE) {
                FlowSource cf = new FlowSource(PhysicalDomain.ELECTRICAL);
                cf.setName("c-" + me.toString());
                // keep node order so the flow direction stays the same.
                cf.connectBetween(convertedNetwork.getNode(ca),
                        convertedNetwork.getNode(cb));
                convertedNetwork.registerElement(cf);
                convertedSources.add(ConvertedSource.plainCurrent(
                        cf, (FlowSource) me));
            }
        }

        // Step 4: create the Norton pair (current source in parallel with the
        // series resistor) for every real voltage source between its two
        // terminals.
        for (idx = 0; idx < srcSource.size(); idx++) {
            int internalMerged = srcInternalMerged.get(idx);
            int terminal1 = convertedNodeOfMergedNode[srcSeriesFarMerged.get(idx)];
            int terminal2 = convertedNodeOfMergedNode[srcTerminalMerged.get(idx)];
            boolean internalIsNode1 = srcInternalIsNode1.get(idx);

            // The parallel resistor is the former series resistor, now
            // connected directly between the two terminals.
            LinearDissipator parallel = new LinearDissipator(
                    PhysicalDomain.ELECTRICAL);
            parallel.setName("c-par-" + srcSeries.get(idx).toString());
            parallel.connectBetween(convertedNetwork.getNode(terminal1),
                    convertedNetwork.getNode(terminal2));
            convertedNetwork.registerElement(parallel);
            convertedSources.add(ConvertedSource.resistor(
                    parallel, srcSeries.get(idx)));

            // The current source injects E/R into the higher terminal. From the
            // Thevenin open circuit analysis the higher terminal is terminal1
            // when the internal node is the source's node 1, otherwise it is
            // terminal2. A FlowSource injects its flow into its node 1 and
            // draws it from its node 0, so we order the nodes accordingly.
            FlowSource current = new FlowSource(PhysicalDomain.ELECTRICAL);
            current.setName("c-norton-" + srcSource.get(idx).toString());
            if (internalIsNode1) {
                // inject into terminal1, draw from terminal2
                current.connectBetween(convertedNetwork.getNode(terminal2),
                        convertedNetwork.getNode(terminal1));
            } else {
                // inject into terminal2, draw from terminal1
                current.connectBetween(convertedNetwork.getNode(terminal1),
                        convertedNetwork.getNode(terminal2));
            }
            convertedNetwork.registerElement(current);
            convertedSources.add(ConvertedSource.nortonCurrent(
                    current, srcSource.get(idx), srcSeries.get(idx)));

            // Store the reconstruction info for the eliminated internal node.
            internalTerminalConvertedNode[internalMerged] = terminal2;
            internalMergedSource[internalMerged] = srcSource.get(idx);
            internalSourceIsNode1[internalMerged] = internalIsNode1;
        }
    }

    /**
     * Searches, on a merged node, for the single resistor that is in series
     * with a given source. Returns the merged element index of that resistor,
     * or -1 if the node is not a valid internal node for the source.
     *
     * @param mergedNodeIndex Merged node to inspect.
     * @param source The source element connected to the node.
     * @return Merged element index of the series resistor, or -1.
     */
    private int findSeriesResistor(int mergedNodeIndex, AbstractElement source) {
        GeneralNode node = mergedNetwork.getNode(mergedNodeIndex);
        if (node.getNumberOfElements() != 2) {
            return -1; // an internal node has exactly the source and one resistor
        }
        AbstractElement other = node.getElement(0) == source
                ? node.getElement(1) : node.getElement(0);
        if (other.getElementType() == ElementType.DISSIPATOR
                || other.getElementType() == ElementType.OPEN) {
            return mergedNetwork.getElementIndex(other);
        }
        return -1;
    }

    /**
     * Assembles the conductance matrix structure and the per-entry contributor
     * lists from the converted network. The actual values are filled in later
     * by {@link #prepareCalculation()}.
     */
    @SuppressWarnings("unchecked")
    private void buildMatrix() {
        int idx, convertedN = convertedNetwork.getNumberOfNodes();

        // Assign a matrix index to every converted node except the fixed ones
        // (the reference node and the forced nodes, whose potential is known).
        matrixIndexOfConvertedNode = new int[convertedN];
        matrixSize = 0;
        for (idx = 0; idx < convertedN; idx++) {
            if (convertedNodeIsFixed[idx]) {
                matrixIndexOfConvertedNode[idx] = -1;
            } else {
                matrixIndexOfConvertedNode[idx] = matrixSize;
                matrixSize++;
            }
        }

        g = new double[matrixSize][matrixSize];
        rhs = new double[matrixSize];
        potential = new double[matrixSize];
        choleskyDiagonal = new double[matrixSize];
        choleskyColumn = new double[matrixSize];

        diagonalContributors = new List[matrixSize];
        rhsContributors = new List[matrixSize];
        forcedRhsContributors = new List[matrixSize];
        for (idx = 0; idx < matrixSize; idx++) {
            diagonalContributors[idx] = new ArrayList<>();
            rhsContributors[idx] = new ArrayList<>();
            forcedRhsContributors[idx] = new ArrayList<>();
        }

        // Walk every converted element and register it with the matrix entries
        // it contributes to. Resistors contribute their conductance, current
        // sources contribute to the right hand side.
        for (idx = 0; idx < convertedNetwork.getNumberOfElements(); idx++) {
            AbstractElement ce = convertedNetwork.getElement(idx);
            int ca = convertedNetwork.getNode0Index(idx);
            int cb = convertedNetwork.getNode1Index(idx);
            int ia = matrixIndexOfConvertedNode[ca];
            int ib = matrixIndexOfConvertedNode[cb];

            if (ce instanceof LinearDissipator) {
                LinearDissipator r = (LinearDissipator) ce;
                // each resistor adds its conductance to both diagonals and
                // subtracts it from both off diagonal entries.
                if (ia >= 0) {
                    diagonalContributors[ia].add(r);
                }
                if (ib >= 0) {
                    diagonalContributors[ib].add(r);
                }
                if (ia >= 0 && ib >= 0) {
                    getOffDiagonalEntry(ia, ib).resistors.add(r);
                }
                // a resistor to a forced node moves the known potential to the
                // right hand side of the free node's row.
                if (ia >= 0 && ib < 0 && convertedNodeIsForced[cb]) {
                    forcedRhsContributors[ia].add(
                            new ForcedContribution(r, cb));
                } else if (ib >= 0 && ia < 0 && convertedNodeIsForced[ca]) {
                    forcedRhsContributors[ib].add(
                            new ForcedContribution(r, ca));
                }
            } else if (ce instanceof FlowSource) {
                FlowSource s = (FlowSource) ce;
                // a current source injects into its node 1 and draws from its
                // node 0 (FlowThrough convention).
                if (ib >= 0) {
                    rhsContributors[ib].add(new CurrentContribution(s, true));
                }
                if (ia >= 0) {
                    rhsContributors[ia].add(new CurrentContribution(s, false));
                }
            }
        }

        // The sparsity pattern is now fixed: precompute the sparse Cholesky
        // symbolic factorization once so the cyclic solve only refactorizes.
        buildSparseSymbolic();
    }

    /**
     * Returns the off-diagonal matrix entry for the unordered pair (i, j),
     * creating it if it does not exist yet. Only used during setup.
     */
    private OffDiagonalEntry getOffDiagonalEntry(int i, int j) {
        int row = Math.min(i, j);
        int col = Math.max(i, j);
        for (OffDiagonalEntry e : offDiagonalEntries) {
            if (e.row == row && e.col == col) {
                return e;
            }
        }
        OffDiagonalEntry created = new OffDiagonalEntry(row, col);
        offDiagonalEntries.add(created);
        return created;
    }

    // ===================================================================
    //  Cyclic calculation
    // ===================================================================
    /**
     * Reads the current values out of the base network and propagates them
     * through the merged and converted networks down into the conductance
     * matrix and the source current vector. No objects are created here.
     */
    @Override
    public void prepareCalculation() {
        int idx, jdx;

        // reset the base network nodes and elements first.
        super.prepareCalculation();

        // verify the BRIDGED assumption: elements that were bridged must stay
        // bridged and no other element is allowed to become bridged.
        for (idx = 0; idx < elements.size(); idx++) {
            ElementType t = elements.get(idx).getElementType();
            if (baseWasBridge[idx] && t != ElementType.BRIDGED) {
                throw new ModelErrorException("A BRIDGED element changed its "
                        + "type. NodalAnalysis cannot be used, the caller has "
                        + "to fall back to a different solver.");
            }
            if (!baseWasBridge[idx] && t == ElementType.BRIDGED) {
                throw new ModelErrorException("An element became BRIDGED. "
                        + "NodalAnalysis cannot be used, the caller has to "
                        + "fall back to a different solver.");
            }
        }

        // base network -> merged network
        for (idx = 0; idx < baseOfMergedElement.size(); idx++) {
            AbstractElement merged = mergedNetwork.getElement(idx);
            copyElementValue(baseOfMergedElement.get(idx), merged);
            // a half of a split shared series resistor carries half the base
            // resistance so the two halves in series equal the original value.
            if (mergedElementIsHalf[idx]
                    && merged.getElementType() == ElementType.DISSIPATOR) {
                LinearDissipator r = (LinearDissipator) merged;
                r.setResistanceParameter(r.getResistance() * 0.5);
            }
        }

        // merged network -> converted network
        for (idx = 0; idx < convertedSources.size(); idx++) {
            convertedSources.get(idx).update();
        }

        // refresh the relative potential of every forced (fixed) node from its
        // provider, so the changing boundary value is followed each cycle.
        for (idx = 0; idx < fixedRelativePotential.length; idx++) {
            if (convertedNodeIsForced[idx]) {
                fixedRelativePotential[idx] = forcedSignOfConvertedNode[idx]
                        * forcedProviderOfConvertedNode[idx].getEffort();
            }
        }

        // converted network -> conductance matrix and source current vector
        for (idx = 0; idx < matrixSize; idx++) {
            // diagonal entry: sum of conductances of all incident resistors
            double sum = 0.0;
            List<LinearDissipator> diag = diagonalContributors[idx];
            for (jdx = 0; jdx < diag.size(); jdx++) {
                sum += diag.get(jdx).getConductance();
            }
            g[idx][idx] = sum;
            // clear the rest of the row, off diagonals are added below.
            for (jdx = 0; jdx < matrixSize; jdx++) {
                if (jdx != idx) {
                    g[idx][jdx] = 0.0;
                }
            }
            // right hand side: sum of injected source currents
            double current = 0.0;
            List<CurrentContribution> rc = rhsContributors[idx];
            for (jdx = 0; jdx < rc.size(); jdx++) {
                current += rc.get(jdx).signedFlow();
            }
            rhs[idx] = current;
        }

        // off diagonal entries: minus the sum of the connecting conductances.
        for (idx = 0; idx < offDiagonalEntries.size(); idx++) {
            OffDiagonalEntry e = offDiagonalEntries.get(idx);
            double sum = 0.0;
            for (jdx = 0; jdx < e.resistors.size(); jdx++) {
                sum += e.resistors.get(jdx).getConductance();
            }
            g[e.row][e.col] = -sum;
            g[e.col][e.row] = -sum;
        }

        // forced (fixed potential) nodes: a resistor towards such a node adds
        // its conductance times the known node potential to the right hand
        // side of the free node's row.
        for (idx = 0; idx < matrixSize; idx++) {
            List<ForcedContribution> fc = forcedRhsContributors[idx];
            for (jdx = 0; jdx < fc.size(); jdx++) {
                ForcedContribution c = fc.get(jdx);
                rhs[idx] += c.resistor.getConductance()
                        * fixedRelativePotential[c.forcedConvertedNode];
            }
        }
    }

    /**
     * Solves the prepared conductance matrix for the node potentials and
     * transfers the result back onto the base network, mirroring the output
     * contract of {@link SuperPosition}: every node effort and every resistor
     * flow of the base network is provided.
     */
    @Override
    public void doCalculation() {
        int idx;

        // 1) solve G * potential = rhs for the unknown node potentials.
        // The conductance matrix is symmetric positive (semi-)definite and very
        // sparse, so the sparse L D L^T solver is used as it is the fastest of
        // the three. To compare against the other implementations, replace this
        // call with solveLinearSystemCholesky() (dense L D L^T) or
        // solveLinearSystem() (Gaussian elimination).
        solveLinearSystemSparseCholesky();

        // 2) reference offset: the matrix potentials are relative to the
        // reference node, whose absolute effort is fixed by the origin.
        double referenceEffort = originElement.getEffort();

        // 3) compute the absolute effort of every base node.
        for (idx = 0; idx < nodes.size(); idx++) {
            int mergedNode = mergedNodeOfBaseNode[idx];
            int convertedNode = convertedNodeOfMergedNode[mergedNode];
            if (convertedNode >= 0) {
                baseNodeEffort[idx]
                        = convertedNodeEffort(convertedNode, referenceEffort);
            } else {
                // an eliminated internal node: reconstruct it from the terminal
                // potential and the effort source value.
                double terminalEffort = convertedNodeEffort(
                        internalTerminalConvertedNode[mergedNode],
                        referenceEffort);
                double e = internalMergedSource[mergedNode].getEffort();
                baseNodeEffort[idx] = internalSourceIsNode1[mergedNode]
                        ? terminalEffort + e : terminalEffort - e;
            }
        }

        // 4) let origins and sources force their values first (origin sets the
        // reference effort and a zero flow, flow sources set their flow). This
        // avoids conflicts when assigning efforts below.
        iterativeSolver.doCalculationOnEnforcerElements();

        // 5) push the computed efforts onto every base node that is not forced
        // by an enforcer element yet.
        for (idx = 0; idx < nodes.size(); idx++) {
            if (!nodes.get(idx).effortUpdated()) {
                nodes.get(idx).setEffort(baseNodeEffort[idx]);
            }
        }

        // 6) with all node efforts known the iterative solver can derive every
        // remaining flow (resistor flows via Ohm's law, source and bridge flows
        // via the node flow balance).
        iterativeSolver.doCalculation();

        if (!iterativeSolver.isCalculationFinished()) {
            LOGGER.log(Level.WARNING,
                    "NodalAnalysis did not finish with a full solution.");
        }
    }

    /**
     * Returns the absolute effort of a converted node, taking the reference
     * offset into account.
     *
     * @param convertedNode Index of the converted node.
     * @param referenceEffort Effort of the reference node.
     * @return Absolute effort value of the converted node.
     */
    private double convertedNodeEffort(int convertedNode,
            double referenceEffort) {
        int matrixIndex = matrixIndexOfConvertedNode[convertedNode];
        if (matrixIndex < 0) {
            // a fixed node: the reference (relative potential 0) or a forced
            // node (relative potential supplied by its provider).
            return referenceEffort + fixedRelativePotential[convertedNode];
        }
        return potential[matrixIndex] + referenceEffort;
    }

    /**
     * Solves the linear system {@code g * potential = rhs} in place using
     * Gaussian elimination with partial pivoting. The matrix and right hand
     * side are rebuilt each cycle by {@link #prepareCalculation()}, so it is
     * safe to destroy them here. No objects are created.
     * <p>
     * A node with no conductive path to the reference produces a zero pivot
     * (floating node). Such a node is given the reference potential (relative
     * value 0) so that the rest of the network still receives a usable
     * solution, just like the superposition solver handles floating nodes.
     * <p>
     * Unused, kept as a reference to compare against L D L^T
     */
    @SuppressWarnings("unused")
    private void solveLinearSystem() {
        int col, row, k, pivotRow;
        double pivotValue, factor;

        boolean floatingNodeSeen = false;

        // forward elimination
        for (col = 0; col < matrixSize; col++) {
            // partial pivoting: find the row with the largest absolute value.
            pivotRow = col;
            pivotValue = Math.abs(g[col][col]);
            for (row = col + 1; row < matrixSize; row++) {
                if (Math.abs(g[row][col]) > pivotValue) {
                    pivotValue = Math.abs(g[row][col]);
                    pivotRow = row;
                }
            }

            if (pivotValue < PIVOT_EPSILON) {
                // floating node, no usable pivot. Leave the column as it is and
                // let the back substitution assign the reference potential.
                floatingNodeSeen = true;
                continue;
            }

            if (pivotRow != col) {
                // swap the two rows (only references and a single rhs value).
                double[] tmpRow = g[pivotRow];
                g[pivotRow] = g[col];
                g[col] = tmpRow;
                double tmpRhs = rhs[pivotRow];
                rhs[pivotRow] = rhs[col];
                rhs[col] = tmpRhs;
            }

            // eliminate the current column from all rows below.
            for (row = col + 1; row < matrixSize; row++) {
                factor = g[row][col] / g[col][col];
                if (factor == 0.0) {
                    continue;
                }
                for (k = col; k < matrixSize; k++) {
                    g[row][k] -= factor * g[col][k];
                }
                rhs[row] -= factor * rhs[col];
            }
        }

        // back substitution
        for (row = matrixSize - 1; row >= 0; row--) {
            double sum = rhs[row];
            for (k = row + 1; k < matrixSize; k++) {
                sum -= g[row][k] * potential[k];
            }
            if (Math.abs(g[row][row]) < PIVOT_EPSILON) {
                potential[row] = 0.0; // floating node -> reference potential
            } else {
                potential[row] = sum / g[row][row];
            }
        }

        if (floatingNodeSeen && !floatingNodeWarned) {
            LOGGER.log(Level.WARNING, "NodalAnalysis encountered a floating "
                    + "node without a conductive path to the reference.");
            floatingNodeWarned = true;
        } else if (!floatingNodeSeen) {
            floatingNodeWarned = false;
        }
    }

    /**
     * Solves the linear system {@code g * potential = rhs} in place using an
     * {@code L D L^T} factorization, the square-root-free variant of the
     * Cholesky decomposition. This is the faster alternative to
     * {@link #solveLinearSystem()} (Gaussian elimination) and produces the same
     * result.
     * <p>
     * The conductance matrix of a resistor network is <i>symmetric positive
     * (semi-)definite</i>: it is symmetric by construction and all conductances
     * are non-negative. For such a matrix the factorization needs no pivoting
     * for stability and only touches the lower triangle, so it does roughly
     * {@code n^3 / 6} operations versus the {@code n^3 / 3} of the Gaussian
     * elimination, about twice as fast and with half the memory traffic. The
     * speed advantage grows with the matrix size.
     * <p>
     * The matrix is factored into {@code g = L * D * L^T} where {@code L} is a
     * unit lower triangular matrix (stored in the strict lower triangle of
     * {@code g}, overwriting it) and {@code D} is a diagonal matrix (stored in
     * {@code choleskyDiagonal}). The solution is then obtained by three cheap
     * triangular solves: {@code L y = rhs}, {@code D z = y} and
     * {@code L^T x = z}. Only the lower triangle and the diagonal of {@code g}
     * are ever read, so the (numerically identical) upper triangle is left
     * untouched. The matrix and right hand side are rebuilt each cycle by
     * {@link #prepareCalculation()}, so it is safe to destroy them here. No
     * objects are created.
     * <p>
     * A node with no conductive path to the reference produces a zero pivot
     * {@code D[j]} (a floating node). Such a node is decoupled from the rest
     * and given the reference potential (relative value 0), so the rest of the
     * network still receives a usable solution, exactly like
     * {@link #solveLinearSystem()} and the superposition solver handle floating
     * nodes.
     * <p>
     * Unused, kept as a reference to compare against sparse
     */
    @SuppressWarnings("unused")
    private void solveLinearSystemCholesky() {
        int i, j, k;
        double sum;

        boolean floatingNodeSeen = false;

        // L D L^T factorization, processed one column j at a time. After column
        // j is done, the strict lower triangle of column j holds L[i][j] and
        // choleskyDiagonal[j] holds D[j].
        for (j = 0; j < matrixSize; j++) {
            // choleskyColumn[k] = D[k] * L[j][k] for all earlier columns k.
            for (k = 0; k < j; k++) {
                choleskyColumn[k] = choleskyDiagonal[k] * g[j][k];
            }

            // D[j] = g[j][j] - sum_{k<j} L[j][k] * D[k] * L[j][k]
            double dj = g[j][j];
            for (k = 0; k < j; k++) {
                dj -= g[j][k] * choleskyColumn[k];
            }

            if (Math.abs(dj) < PIVOT_EPSILON) {
                // floating node: no conductive coupling left for this node.
                // Decouple its column (set L below the diagonal to zero) and use
                // a sentinel pivot so the triangular solves leave it at the
                // reference potential.
                floatingNodeSeen = true;
                choleskyDiagonal[j] = 1.0;
                for (i = j + 1; i < matrixSize; i++) {
                    g[i][j] = 0.0;
                }
                continue;
            }

            choleskyDiagonal[j] = dj;

            // L[i][j] = (g[i][j] - sum_{k<j} L[i][k] * D[k] * L[j][k]) / D[j]
            for (i = j + 1; i < matrixSize; i++) {
                sum = g[i][j];
                for (k = 0; k < j; k++) {
                    sum -= g[i][k] * choleskyColumn[k];
                }
                g[i][j] = sum / dj;
            }
        }

        // Forward substitution: solve L y = rhs (unit lower triangular). The
        // intermediate y is stored in potential.
        for (i = 0; i < matrixSize; i++) {
            sum = rhs[i];
            for (k = 0; k < i; k++) {
                sum -= g[i][k] * potential[k];
            }
            potential[i] = sum;
        }

        // Diagonal solve: z = D^-1 y, in place in potential.
        for (i = 0; i < matrixSize; i++) {
            potential[i] /= choleskyDiagonal[i];
        }

        // Back substitution: solve L^T x = z. L^T[i][k] equals the stored
        // L[k][i] = g[k][i] for k > i.
        for (i = matrixSize - 1; i >= 0; i--) {
            sum = potential[i];
            for (k = i + 1; k < matrixSize; k++) {
                sum -= g[k][i] * potential[k];
            }
            potential[i] = sum;
        }

        if (floatingNodeSeen && !floatingNodeWarned) {
            LOGGER.log(Level.WARNING, "NodalAnalysis encountered a floating "
                    + "node without a conductive path to the reference.");
            floatingNodeWarned = true;
        } else if (!floatingNodeSeen) {
            floatingNodeWarned = false;
        }
    }

    /**
     * Builds the symbolic part of the sparse {@code L D L^T} factorization used
     * by {@link #solveLinearSystemSparseCholesky()}. This depends only on the
     * sparsity pattern of the conductance matrix (the network topology), which
     * is fixed once {@link #buildMatrix()} has run, so it is computed a single
     * time and reused by every cyclic solve.
     * <p>
     * The conductance matrix is stored in compressed-column form, upper
     * triangle only (which equals the lower triangle by rows of the symmetric
     * matrix). The {@link #offDiagonalEntries} already hold every off-diagonal
     * connection as a {@code (row, col)} pair with {@code row < col}, so each
     * one is exactly one upper-triangle entry in column {@code col}; together
     * with the diagonal of every column this is the full pattern. From it the
     * elimination tree ({@link #eliminationParent}) and the column counts and
     * pointers of the factor {@code L} ({@link #sparseLColPtr}) are derived and
     * the factor storage is allocated. Follows the compressed-column "LDL"
     * scheme of T. Davis.
     */
    private void buildSparseSymbolic() {
        int n = matrixSize;
        int c, e, p, k, i;

        // -- compressed-column upper triangle of A (pattern only) -----------
        int nnzA = n + offDiagonalEntries.size();
        sparseAColPtr = new int[n + 1];
        sparseARowIndex = new int[nnzA];
        sparseAValue = new double[nnzA];

        // one entry per column for the diagonal, plus one per off-diagonal in
        // its (upper-triangle) column.
        for (c = 0; c < n; c++) {
            sparseAColPtr[c + 1] = 1;
        }
        for (e = 0; e < offDiagonalEntries.size(); e++) {
            sparseAColPtr[offDiagonalEntries.get(e).col + 1]++;
        }
        for (c = 0; c < n; c++) {
            sparseAColPtr[c + 1] += sparseAColPtr[c];
        }

        // fill the row indices; a per-column write cursor places the off
        // diagonals first and the diagonal (the largest row index) last.
        int[] cursor = new int[n];
        for (c = 0; c < n; c++) {
            cursor[c] = sparseAColPtr[c];
        }
        for (e = 0; e < offDiagonalEntries.size(); e++) {
            OffDiagonalEntry od = offDiagonalEntries.get(e);
            sparseARowIndex[cursor[od.col]++] = od.row;
        }
        for (c = 0; c < n; c++) {
            sparseARowIndex[cursor[c]++] = c; // diagonal
        }

        // -- symbolic factorization: elimination tree and column counts -----
        eliminationParent = new int[n];
        sparseLColPtr = new int[n + 1];
        sparseLNz = new int[n];
        sparseFlag = new int[n];
        sparsePattern = new int[n];
        sparseY = new double[n];
        sparseDiagonal = new double[n];

        for (k = 0; k < n; k++) {
            eliminationParent[k] = -1;
            sparseFlag[k] = k;
            sparseLNz[k] = 0;
            for (p = sparseAColPtr[k]; p < sparseAColPtr[k + 1]; p++) {
                i = sparseARowIndex[p];
                if (i < k) {
                    // walk from i to the root of the tree, marking the path.
                    for (; sparseFlag[i] != k; i = eliminationParent[i]) {
                        if (eliminationParent[i] == -1) {
                            eliminationParent[i] = k;
                        }
                        sparseLNz[i]++;
                        sparseFlag[i] = k;
                    }
                }
            }
        }

        sparseLColPtr[0] = 0;
        for (k = 0; k < n; k++) {
            sparseLColPtr[k + 1] = sparseLColPtr[k] + sparseLNz[k];
        }

        int nnzL = sparseLColPtr[n];
        sparseLRowIndex = new int[nnzL];
        sparseLValue = new double[nnzL];
    }

    /**
     * Solves the linear system {@code g * potential = rhs} with a sparse
     * {@code L D L^T} factorization. This is the fastest of the three solvers
     * for large networks and produces the same result as
     * {@link #solveLinearSystem()} (Gaussian elimination) and
     * {@link #solveLinearSystemCholesky()} (dense {@code L D L^T}).
     * <p>
     * A conductance matrix is symmetric positive (semi-)definite <i>and</i>
     * very sparse: every node only connects to the few resistors incident to
     * it. The dense factorizations spend almost all of their {@code O(n^3)}
     * work on structural zeros; a sparse factorization stores and operates only
     * on the non-zero entries, so for an {@code n = 500} network it is
     * dramatically faster. Because the pattern is fixed by the topology, the
     * costly symbolic analysis runs once in {@link #buildSparseSymbolic()} and
     * only the cheap numeric refactorization happens here, every cycle.
     * <p>
     * Each cycle the numeric values are read out of the freshly built dense
     * matrix {@code g} into the compressed-column storage, the factor
     * {@code A = L * D * L^T} is computed column by column (a sparse triangular
     * solve driven by the elimination tree), and the solution is obtained by
     * the three sparse triangular solves {@code L y = rhs}, {@code D z = y} and
     * {@code L^T x = z}. No objects are created. Follows the compressed-column
     * "LDL" scheme of T. Davis.
     * <p>
     * As in the other two solvers, a node without a conductive path to the
     * reference produces a (near) zero pivot {@code D[k]}; a sentinel value of
     * one is substituted so the solve stays finite and the rest of the network
     * still receives a usable solution.
     */
    private void solveLinearSystemSparseCholesky() {
        int n = matrixSize;
        int c, p, k, i, len, top;

        boolean floatingNodeSeen = false;

        // refill the numeric values of A from the dense conductance matrix; only
        // the stored upper-triangle non-zeros are read.
        for (c = 0; c < n; c++) {
            for (p = sparseAColPtr[c]; p < sparseAColPtr[c + 1]; p++) {
                sparseAValue[p] = g[sparseARowIndex[p]][c];
            }
        }

        // -- numeric factorization, one column k at a time ------------------
        for (k = 0; k < n; k++) {
            sparseY[k] = 0.0;
            top = n;            // the pattern stack grows down from n
            sparseFlag[k] = k;
            sparseLNz[k] = 0;   // reused as the write cursor of column k

            // scatter column k of A into Y and build the topological pattern of
            // the row of L being computed.
            for (p = sparseAColPtr[k]; p < sparseAColPtr[k + 1]; p++) {
                i = sparseARowIndex[p];
                if (i <= k) {
                    sparseY[i] += sparseAValue[p];
                    for (len = 0; sparseFlag[i] != k; i = eliminationParent[i]) {
                        sparsePattern[len++] = i;
                        sparseFlag[i] = k;
                    }
                    while (len > 0) {
                        sparsePattern[--top] = sparsePattern[--len];
                    }
                }
            }

            // sparse triangular solve producing the row of L and the pivot D[k].
            double dk = sparseY[k];
            sparseY[k] = 0.0;
            for (; top < n; top++) {
                i = sparsePattern[top];
                double yi = sparseY[i];
                sparseY[i] = 0.0;
                int pEnd = sparseLColPtr[i] + sparseLNz[i];
                for (p = sparseLColPtr[i]; p < pEnd; p++) {
                    sparseY[sparseLRowIndex[p]] -= sparseLValue[p] * yi;
                }
                double lki = yi / sparseDiagonal[i];
                dk -= lki * yi;
                sparseLRowIndex[pEnd] = k;   // store L(k,i) in column i
                sparseLValue[pEnd] = lki;
                sparseLNz[i]++;
            }

            if (Math.abs(dk) < PIVOT_EPSILON) {
                // floating node: substitute a sentinel pivot so the solve stays
                // finite, matching the dense solvers' handling.
                floatingNodeSeen = true;
                dk = 1.0;
            }
            sparseDiagonal[k] = dk;
        }

        // -- solve A x = rhs via the three triangular systems ---------------
        for (i = 0; i < n; i++) {
            potential[i] = rhs[i];
        }
        // forward solve L y = rhs (unit lower triangular).
        for (k = 0; k < n; k++) {
            double yk = potential[k];
            for (p = sparseLColPtr[k]; p < sparseLColPtr[k + 1]; p++) {
                potential[sparseLRowIndex[p]] -= sparseLValue[p] * yk;
            }
        }
        // diagonal solve z = D^-1 y.
        for (i = 0; i < n; i++) {
            potential[i] /= sparseDiagonal[i];
        }
        // back solve L^T x = z.
        for (k = n - 1; k >= 0; k--) {
            double xk = potential[k];
            for (p = sparseLColPtr[k]; p < sparseLColPtr[k + 1]; p++) {
                xk -= sparseLValue[p] * potential[sparseLRowIndex[p]];
            }
            potential[k] = xk;
        }

        if (floatingNodeSeen && !floatingNodeWarned) {
            LOGGER.log(Level.WARNING, "NodalAnalysis encountered a floating "
                    + "node without a conductive path to the reference.");
            floatingNodeWarned = true;
        } else if (!floatingNodeSeen) {
            floatingNodeWarned = false;
        }
    }

    // ===================================================================
    //  Helpers
    // ===================================================================
    /**
     * Creates a new, unconnected element of the same network type as the given
     * element. Used to build duplicates for the merged network.
     *
     * @param e Template element.
     * @return A new element of the matching type.
     */
    private static AbstractElement createLikeElement(AbstractElement e) {
        switch (e.getElementType()) {
            case DISSIPATOR:
            case OPEN:
                return new LinearDissipator(PhysicalDomain.ELECTRICAL);
            case EFFORTSOURCE:
                return new EffortSource(PhysicalDomain.ELECTRICAL);
            case FLOWSOURCE:
                return new FlowSource(PhysicalDomain.ELECTRICAL);
            default:
                throw new ModelErrorException("Cannot duplicate element of "
                        + "type " + e.getElementType());
        }
    }

    /**
     * Copies the value of a source element onto a target element of the same
     * type. Used to transfer values from the base to the merged network.
     *
     * @param source Element to read from.
     * @param target Element to write to.
     */
    private static void copyElementValue(AbstractElement source,
            AbstractElement target) {
        switch (source.getElementType()) {
            case DISSIPATOR:
                ((LinearDissipator) target).setResistanceParameter(
                        ((LinearDissipator) source).getResistance());
                break;
            case OPEN:
                ((LinearDissipator) target).setOpenConnection();
                break;
            case EFFORTSOURCE:
                ((EffortSource) target).setEffort(
                        ((EffortSource) source).getEffort());
                break;
            case FLOWSOURCE:
                ((FlowSource) target).setFlow(
                        ((FlowSource) source).getFlow());
                break;
            default:
                throw new ModelErrorException("Cannot copy value of element of "
                        + "type " + source.getElementType());
        }
    }

    /**
     * Union-find: returns the root of the group a node belongs to.
     */
    private int find(int node) {
        while (unionParent[node] != node) {
            unionParent[node] = unionParent[unionParent[node]]; // path halving
            node = unionParent[node];
        }
        return node;
    }

    /**
     * Union-find: joins the groups of the two given nodes.
     */
    private void union(int a, int b) {
        int ra = find(a);
        int rb = find(b);
        if (ra != rb) {
            unionParent[ra] = rb;
        }
    }

    // ===================================================================
    //  Inner helper types
    // ===================================================================
    /**
     * A minimal concrete linear network used to hold the merged and converted
     * derived networks. Adds index lookups that {@link LinearNetwork} keeps
     * protected.
     */
    private static final class DerivedNetwork extends LinearNetwork {

        int getNumberOfNodes() {
            return nodes.size();
        }

        int getNumberOfElements() {
            return elements.size();
        }

        GeneralNode getNode(int index) {
            return nodes.get(index);
        }

        int getNodeIndex(GeneralNode node) {
            return nodes.indexOf(node);
        }

        int getElementIndex(AbstractElement element) {
            return elements.indexOf(element);
        }

        int getNode0Index(int elementIndex) {
            return node0idx[elementIndex];
        }

        int getNode1Index(int elementIndex) {
            return node1idx[elementIndex];
        }
    }

    /**
     * Describes how one converted element gets its value updated from the
     * merged network in {@link NodalAnalysis#prepareCalculation()}.
     */
    private static final class ConvertedSource {

        private static final int RESISTOR = 0;
        private static final int PLAIN_CURRENT = 1;
        private static final int NORTON_CURRENT = 2;

        private final int kind;
        private final LinearDissipator convertedResistor;
        private final FlowSource convertedCurrent;
        private final LinearDissipator mergedResistor;
        private final FlowSource mergedCurrent;
        private final EffortSource mergedSource;
        private final LinearDissipator mergedSeriesResistor;

        private ConvertedSource(int kind, LinearDissipator convertedResistor,
                FlowSource convertedCurrent, LinearDissipator mergedResistor,
                FlowSource mergedCurrent, EffortSource mergedSource,
                LinearDissipator mergedSeriesResistor) {
            this.kind = kind;
            this.convertedResistor = convertedResistor;
            this.convertedCurrent = convertedCurrent;
            this.mergedResistor = mergedResistor;
            this.mergedCurrent = mergedCurrent;
            this.mergedSource = mergedSource;
            this.mergedSeriesResistor = mergedSeriesResistor;
        }

        static ConvertedSource resistor(LinearDissipator converted,
                LinearDissipator merged) {
            return new ConvertedSource(RESISTOR, converted, null, merged,
                    null, null, null);
        }

        static ConvertedSource plainCurrent(FlowSource converted,
                FlowSource merged) {
            return new ConvertedSource(PLAIN_CURRENT, null, converted, null,
                    merged, null, null);
        }

        static ConvertedSource nortonCurrent(FlowSource converted,
                EffortSource source, LinearDissipator series) {
            return new ConvertedSource(NORTON_CURRENT, null, converted, null,
                    null, source, series);
        }

        /**
         * Copies the value from the merged network onto the converted element.
         */
        void update() {
            switch (kind) {
                case RESISTOR:
                    if (mergedResistor.getElementType() == ElementType.OPEN) {
                        convertedResistor.setOpenConnection();
                    } else {
                        convertedResistor.setResistanceParameter(
                                mergedResistor.getResistance());
                    }
                    break;
                case PLAIN_CURRENT:
                    convertedCurrent.setFlow(mergedCurrent.getFlow());
                    break;
                case NORTON_CURRENT:
                    if (mergedSeriesResistor.getElementType()
                            == ElementType.BRIDGED) {
                        throw new ModelErrorException("The series resistor of a "
                                + "real voltage source became BRIDGED, turning "
                                + "it into an ideal source. NodalAnalysis "
                                + "cannot solve this, fall back to another "
                                + "solver.");
                    }
                    // Norton current = effort * conductance of the series
                    // resistor. An open series resistor yields zero current.
                    convertedCurrent.setFlow(mergedSource.getEffort()
                            * mergedSeriesResistor.getConductance());
                    break;
            }
        }
    }

    /**
     * One off-diagonal matrix entry and the resistors that connect its two
     * nodes. The conductance sum is applied with a negative sign to both
     * {@code g[row][col]} and {@code g[col][row]}.
     */
    private static final class OffDiagonalEntry {

        final int row;
        final int col;
        final List<LinearDissipator> resistors = new ArrayList<>();

        OffDiagonalEntry(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }

    /**
     * A current source contributing to one entry of the source current vector.
     * The sign depends on whether the source injects current into the node (its
     * node 1) or draws current from it (its node 0).
     */
    private static final class CurrentContribution {

        private final FlowSource source;
        private final boolean injecting;

        CurrentContribution(FlowSource source, boolean injecting) {
            this.source = source;
            this.injecting = injecting;
        }

        double signedFlow() {
            return injecting ? source.getFlow() : -source.getFlow();
        }
    }

    /**
     * A resistor that connects a free (unknown) node to a forced (fixed
     * potential) node. Its conductance times the forced node potential is added
     * to the right hand side of the free node's matrix row, which is the way a
     * known node potential enters the node potential method.
     */
    private static final class ForcedContribution {

        private final LinearDissipator resistor;
        private final int forcedConvertedNode;

        ForcedContribution(LinearDissipator resistor, int forcedConvertedNode) {
            this.resistor = resistor;
            this.forcedConvertedNode = forcedConvertedNode;
        }
    }
}
