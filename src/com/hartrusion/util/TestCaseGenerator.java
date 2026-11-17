package com.hartrusion.util;

import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.general.ClosedOrigin;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates Java test-case source code from a live network at runtime.
 * Usage: Call {@link #generateTestCode} with the nodes and elements lists
 * from a {@link com.hartrusion.modeling.solvers.LinearNetwork} instance,
 * e.g. at a debugger breakpoint via "Evaluate Expression".
 *
 * <p>Example debugger expression (IntelliJ / NetBeans):
 * <pre>
 *   TestCaseGenerator.generateTestCode(this.nodes, this.elements)
 * com.hartrusion.util.TestCaseGenerator.printTestCode(this.nodes, this.elements);
 * </pre>
 * The returned String is a complete test method body.
 *
 * @author Generated utility
 */
public final class TestCaseGenerator {

    private TestCaseGenerator() { }

    /**
     * Generates a complete Java test method body that reconstructs the given
     * network. The output can be pasted into a TestNG test class.
     *
     * @param nodes    the nodes list from LinearNetwork
     * @param elements the elements list from LinearNetwork
     * @return Java source code as a String
     */
    public static String generateTestCode(
            List<GeneralNode> nodes,
            List<AbstractElement> elements) {

        StringBuilder sb = new StringBuilder();

        // --- Assign stable variable names ---
        // Use IdentityHashMap so object identity (not equals) is the key.
        Map<GeneralNode, String> nodeNames = new IdentityHashMap<>();
        Map<AbstractElement, String> elemNames = new IdentityHashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            nodeNames.put(nodes.get(i), "node[" + i + "]");
        }

        int dissipatorCount = 0;
        int effortSrcCount = 0;
        int flowSrcCount = 0;
        int originCount = 0;

        for (AbstractElement e : elements) {
            if (e instanceof ClosedOrigin) {
                elemNames.put(e, "zero" + (originCount > 0 ? originCount : ""));
                originCount++;
            } else if (e instanceof EffortSource) {
                elemNames.put(e, "u" + (effortSrcCount > 0 ? effortSrcCount : ""));
                effortSrcCount++;
            } else if (e instanceof FlowSource) {
                elemNames.put(e, "flowSrc" + (flowSrcCount > 0 ? flowSrcCount : ""));
                flowSrcCount++;
            } else if (e instanceof LinearDissipator) {
                elemNames.put(e, "r[" + dissipatorCount + "]");
                dissipatorCount++;
            } else {
                elemNames.put(e, "elem_" + elemNames.size());
            }
        }

        // --- Header ---
        sb.append("// ========== Auto-generated test code ==========\n");
        sb.append("RecursiveSimplifier instance = new RecursiveSimplifier();\n\n");

        // --- Nodes ---
        sb.append("// --- Nodes (").append(nodes.size()).append(") ---\n");
        sb.append("GeneralNode[] node = new GeneralNode[")
          .append(nodes.size()).append("];\n");
        sb.append("for (int idx = 0; idx < node.length; idx++) {\n");
        sb.append("    node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);\n");
        sb.append("    node[idx].setName(\"node\" + idx);\n");
        sb.append("}\n\n");

        // Override names if the original nodes had custom names
        for (int i = 0; i < nodes.size(); i++) {
            String name = nodes.get(i).toString();
            if (name != null && !name.contains("@")) {
                // name was explicitly set, already handled by loop above
                sb.append("node[").append(i).append("].setName(\"")
                  .append(escapeJava(name)).append("\");\n");
            }
        }
        sb.append("\n");

        // --- Dissipators ---
        if (dissipatorCount > 0) {
            sb.append("// --- Dissipators (").append(dissipatorCount).append(") ---\n");
            sb.append("LinearDissipator[] r = new LinearDissipator[")
              .append(dissipatorCount).append("];\n");
            sb.append("for (int idx = 0; idx < r.length; idx++) {\n");
            sb.append("    r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);\n");
            sb.append("    r[idx].setName(\"R\" + idx);\n");
            sb.append("}\n\n");

            // Set individual resistance / type
            int rIdx = 0;
            for (AbstractElement e : elements) {
                if (e instanceof LinearDissipator) {
                    LinearDissipator ld = (LinearDissipator) e;
                    String name = e.toString();
                    if (name != null && !name.contains("@")) {
                        sb.append("r[").append(rIdx).append("].setName(\"")
                          .append(escapeJava(name)).append("\");\n");
                    }
                    switch (ld.getElementType()) {
                        case OPEN:
                            sb.append("r[").append(rIdx)
                              .append("].setOpenConnection();\n");
                            break;
                        case BRIDGED:
                            sb.append("r[").append(rIdx)
                              .append("].setBridgedConnection();\n");
                            break;
                        case DISSIPATOR:
                        default:
                            sb.append("r[").append(rIdx)
                              .append("].setResistanceParameter(")
                              .append(ld.getResistance()).append(");\n");
                            break;
                    }
                    rIdx++;
                }
            }
            sb.append("\n");
        }

        // --- Effort Sources ---
        if (effortSrcCount > 0) {
            int idx = 0;
            for (AbstractElement e : elements) {
                if (e instanceof EffortSource) {
                    EffortSource es = (EffortSource) e;
                    String var = (effortSrcCount == 1) ? "u"
                            : "u" + idx;
                    sb.append("EffortSource ").append(var)
                      .append(" = new EffortSource(PhysicalDomain.ELECTRICAL);\n");
                    sb.append(var).append(".setEffort(")
                      .append(es.getEffort()).append(");\n");
                    String name = e.toString();
                    if (name != null && !name.contains("@")) {
                        sb.append(var).append(".setName(\"")
                          .append(escapeJava(name)).append("\");\n");
                    }
                    sb.append("\n");
                    idx++;
                }
            }
        }

        // --- Flow Sources ---
        if (flowSrcCount > 0) {
            int idx = 0;
            for (AbstractElement e : elements) {
                if (e instanceof FlowSource) {
                    String var = (flowSrcCount == 1) ? "flowSrc"
                            : "flowSrc" + idx;
                    sb.append("FlowSource ").append(var)
                      .append(" = new FlowSource(PhysicalDomain.ELECTRICAL);\n");
                    String name = e.toString();
                    if (name != null && !name.contains("@")) {
                        sb.append(var).append(".setName(\"")
                          .append(escapeJava(name)).append("\");\n");
                    }
                    sb.append("\n");
                    idx++;
                }
            }
        }

        // --- Origins ---
        if (originCount > 0) {
            int idx = 0;
            for (AbstractElement e : elements) {
                if (e instanceof ClosedOrigin) {
                    String var = (originCount == 1) ? "zero" : "zero" + idx;
                    sb.append("ClosedOrigin ").append(var)
                      .append(" = new ClosedOrigin(PhysicalDomain.ELECTRICAL);\n");
                    String name = e.toString();
                    if (name != null && !name.contains("@")) {
                        sb.append(var).append(".setName(\"")
                          .append(escapeJava(name)).append("\");\n");
                    }
                    sb.append("\n");
                    idx++;
                }
            }
        }

        // --- Connections ---
        sb.append("// --- Connections ---\n");
        for (AbstractElement e : elements) {
            String eVar = elemNames.get(e);
            int numNodes = e.getNumberOfNodes();
            if (numNodes >= 1) {
                GeneralNode n0 = e.getNode(0);
                String n0Var = nodeNames.get(n0);
                if (n0Var == null) {
                    n0Var = "/* UNKNOWN NODE */";
                }
                if (numNodes == 1) {
                    // Origin (single node)
                    sb.append(eVar).append(".connectTo(")
                      .append(n0Var).append(");\n");
                } else {
                    GeneralNode n1 = e.getNode(1);
                    String n1Var = nodeNames.get(n1);
                    if (n1Var == null) {
                        n1Var = "/* UNKNOWN NODE */";
                    }
                    // Two-node elements: use registerNode/registerElement
                    // to preserve exact node ordering (important for flow
                    // direction and solver behaviour)
                    sb.append(eVar).append(".registerNode(")
                      .append(n0Var).append(");\n");
                    sb.append(n0Var).append(".registerElement(")
                      .append(eVar).append(");\n");
                    sb.append(eVar).append(".registerNode(")
                      .append(n1Var).append(");\n");
                    sb.append(n1Var).append(".registerElement(")
                      .append(eVar).append(");\n");
                }
            }
        }
        sb.append("\n");

        // --- Registration ---
        sb.append("// --- Register with solver ---\n");
        sb.append("for (GeneralNode n : node) {\n");
        sb.append("    instance.registerNode(n);\n");
        sb.append("}\n");
        for (AbstractElement e : elements) {
            String eVar = elemNames.get(e);
            sb.append("instance.registerElement(").append(eVar).append(");\n");
        }
        sb.append("\n");

        // --- Solver setup ---
        sb.append("// --- Run solver ---\n");
        sb.append("instance.recursiveSimplificationSetup(0);\n");
        sb.append("instance.prepareRecursiveCalculation();\n");
        sb.append("instance.doRecursiveCalculation();\n");
        sb.append("assertTrue(instance.isCalculationFinished(), ");
        sb.append("\"Calculation did not finish.\");\n");

        sb.append("// ========== End of auto-generated code ==========\n");

        return sb.toString();
    }

    /**
     * Writes the generated code to System.out for quick copy-paste.
     * Useful as a one-liner in debugger "Evaluate Expression" dialogs.
     */
    public static void printTestCode(
            List<GeneralNode> nodes,
            List<AbstractElement> elements) {
        System.out.println(generateTestCode(nodes, elements));
    }

    private static String escapeJava(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}