/*
 * The MIT License
 *
 * Copyright 2026 viktor.
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
package com.hartrusion.util;

import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.solvers.StarPolygonTransform;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Generates Java test-case source code from a live StarPolygonTransform
 * instance at runtime.
 *
 * <p>Usage: Set a breakpoint somewhere inside StarPolygonTransform after
 * setupStar(...) has been called, then evaluate:
 *
 * <pre>
 *   com.hartrusion.util.StarPolygonTestCaseGenerator.printTestCode(this);
 * </pre>
 *
 * or
 *
 * <pre>
 *   com.hartrusion.util.StarPolygonTestCaseGenerator.generateTestCode(this);
 * </pre>
 *
 * The returned String is a complete Java test method body snippet that
 * reconstructs only the star around the current transform and calls
 * setupStar(starNode).
 */
public final class StarPolygonTestCaseGenerator {

    private StarPolygonTestCaseGenerator() {
    }

    /**
     * Generates Java source code that reconstructs the star currently stored
     * inside a StarPolygonTransform instance.
     *
     * @param transform live transform instance from debugger
     * @return Java source snippet
     */
    public static String generateTestCode(StarPolygonTransform transform) {
        try {
            GeneralNode starNode = (GeneralNode) getField(transform, "starNode");
            GeneralNode[] parentStarNodes =
                    (GeneralNode[]) getField(transform, "parentStarNodes");
            LinearDissipator[] parentStarElements =
                    (LinearDissipator[]) getField(transform, "parentStarElements");
            Integer branches = (Integer) getField(transform, "branches");

            if (starNode == null) {
                throw new IllegalStateException(
                        "starNode is null. Call this after setupStar(...) or at a point where the star is initialized.");
            }
            if (parentStarNodes == null || parentStarElements == null) {
                throw new IllegalStateException(
                        "parentStarNodes/parentStarElements are null. Call this after setupStar(...) or after star detection.");
            }

            StringBuilder sb = new StringBuilder();

            Map<GeneralNode, String> nodeNames = new IdentityHashMap<>();
            Map<LinearDissipator, String> resistorNames = new IdentityHashMap<>();

            sb.append("// ========== Auto-generated StarPolygon test code ==========\n");
            sb.append("StarPolygonTransform transform = new StarPolygonTransform();\n\n");

            sb.append("// --- Nodes ---\n");
            sb.append("GeneralNode starNode = new GeneralNode(PhysicalDomain.ELECTRICAL);\n");
            nodeNames.put(starNode, "starNode");

            String starNodeName = starNode.toString();
            if (starNodeName != null && !starNodeName.contains("@")) {
                sb.append("starNode.setName(\"")
                        .append(escapeJava(starNodeName))
                        .append("\");\n");
            }

            sb.append("GeneralNode[] outerNodes = new GeneralNode[")
                    .append(branches)
                    .append("];\n");
            sb.append("for (int idx = 0; idx < outerNodes.length; idx++) {\n");
            sb.append("    outerNodes[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);\n");
            sb.append("    outerNodes[idx].setName(\"outerNode\" + idx);\n");
            sb.append("}\n\n");

            for (int i = 0; i < parentStarNodes.length; i++) {
                nodeNames.put(parentStarNodes[i], "outerNodes[" + i + "]");
                String outerName = parentStarNodes[i] != null ? parentStarNodes[i].toString() : null;
                if (outerName != null && !outerName.contains("@")) {
                    sb.append("outerNodes[").append(i).append("].setName(\"")
                            .append(escapeJava(outerName))
                            .append("\");\n");
                }
            }

            sb.append("\n");
            sb.append("// --- Star resistors ---\n");
            sb.append("LinearDissipator[] r = new LinearDissipator[")
                    .append(branches)
                    .append("];\n");
            sb.append("for (int idx = 0; idx < r.length; idx++) {\n");
            sb.append("    r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);\n");
            sb.append("    r[idx].setName(\"R\" + idx);\n");
            sb.append("}\n\n");

            for (int i = 0; i < parentStarElements.length; i++) {
                LinearDissipator ld = parentStarElements[i];
                resistorNames.put(ld, "r[" + i + "]");

                String elemName = ld != null ? ld.toString() : null;
                if (elemName != null && !elemName.contains("@")) {
                    sb.append("r[").append(i).append("].setName(\"")
                            .append(escapeJava(elemName))
                            .append("\");\n");
                }

                switch (ld.getElementType()) {
                    case OPEN:
                        sb.append("r[").append(i).append("].setOpenConnection();\n");
                        break;
                    case BRIDGED:
                        sb.append("r[").append(i).append("].setBridgedConnection();\n");
                        break;
                    case DISSIPATOR:
                    default:
                        sb.append("r[").append(i).append("].setResistanceParameter(")
                                .append(ld.getResistance())
                                .append(");\n");
                        break;
                }
            }

            sb.append("\n");
            sb.append("// --- Connections ---\n");
            sb.append("for (int idx = 0; idx < r.length; idx++) {\n");
            sb.append("    r[idx].registerNode(starNode);\n");
            sb.append("    starNode.registerElement(r[idx]);\n");
            sb.append("    r[idx].registerNode(outerNodes[idx]);\n");
            sb.append("    outerNodes[idx].registerElement(r[idx]);\n");
            sb.append("}\n\n");

            sb.append("// --- Setup transform ---\n");
            sb.append("transform.setupStar(starNode);\n");

            sb.append("\n");
            sb.append("// Optional follow-up steps for debugging:\n");
            sb.append("// transform.calculatePolygonResistorValues();\n");
            sb.append("// ... set child polygon node efforts / flows if needed ...\n");
            sb.append("// transform.calculateStarValuesFromPolygon();\n");

            sb.append("// ========== End of auto-generated code ==========\n");

            return sb.toString();

        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(
                    "Failed to inspect StarPolygonTransform via reflection.", ex);
        }
    }

    /**
     * Prints generated code to stdout.
     *
     * @param transform live transform instance
     */
    public static void printTestCode(StarPolygonTransform transform) {
        System.out.println(generateTestCode(transform));
    }

    private static Object getField(Object instance, String fieldName)
            throws ReflectiveOperationException {
        Field f = instance.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(instance);
    }

    private static String escapeJava(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}