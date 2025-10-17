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
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import static com.hartrusion.util.ArraysExt.*;

/**
 * Extends LinearNetwork with lists for referencing to a child network which is
 * created out of the network elements. This is used for providing consistent
 * variable names for classes which are doing this, therefore there are no
 * application methods implemented, just some to keep array sizes big enough.
 *
 * @author Viktor Alexander Hartung
 */
public abstract class ChildNetwork extends LinearNetwork {

    protected final List<GeneralNode> childNodes = new ArrayList<>();
    protected final List<AbstractElement> childElements = new ArrayList<>();

    /**
     * Holds index of an element related to a given child element. Usage:
     * elementOfChildElement[child-element-index] = element-index
     */
    protected int[] elementOfChildElement;
    /**
     * Holds index of a child element related to a given element. Usage:
     * childElementOfElement[element-index] = child-element-index
     */
    protected int[] childElementOfElement;
    /**
     * Holds index of a node related to a given child node. Usage:
     * nodeOfChildNode[child-node-index] = node-index
     */
    protected int[] nodeOfChildNode;
    /**
     * Holds index of a child node related to a given node. Usage:
     * childNodeOfNode[node-index] = child-node-index
     */
    protected int[] childNodeOfNode;

    public ChildNetwork() {
        elementOfChildElement = new int[INIT_ARRAY_SIZE];
        childElementOfElement = new int[INIT_ARRAY_SIZE];
        nodeOfChildNode = new int[INIT_ARRAY_SIZE];
        childNodeOfNode = new int[INIT_ARRAY_SIZE];

        for (int idx = 0; idx < INIT_ARRAY_SIZE; idx++) {
            elementOfChildElement[idx] = -1;
            childElementOfElement[idx] = -1;
            nodeOfChildNode[idx] = -1;
            childNodeOfNode[idx] = -1;
        }
    }

    /**
     * Transfers values from a parent network element to the corresponding child
     * element. Uses the arrays to determine which element belongs to which
     * child element.
     * <p>
     * This functionality is used in multiple solvers so it's a common used
     * method here.
     *
     * @param elementIndex Index of element in element-list
     */
    protected void transferPropertyToChild(int elementIndex) {
        switch (elements.get(elementIndex).getElementType()) {
            case DISSIPATOR:
                // ok, this is a nightmare. but it basically just casts
                // child and parent to the implemented element type,
                // gets parameter from parent and sets it to child.
                ((LinearDissipator) childElements.get(
                        childElementOfElement[elementIndex]))
                        .setResistanceParameter(
                                ((LinearDissipator) elements
                                        .get(elementIndex))
                                        .getResistance());
                break;
            case OPEN:
                ((LinearDissipator) childElements.get(
                        childElementOfElement[elementIndex]))
                        .setOpenConnection();
                break;
            case BRIDGED:
                ((LinearDissipator) childElements.get(
                        childElementOfElement[elementIndex]))
                        .setBridgedConnection();
                break;
            case FLOWSOURCE:
                ((FlowSource) childElements.get(
                        childElementOfElement[elementIndex]))
                        .setFlow(
                                ((FlowSource) elements
                                        .get(elementIndex))
                                        .getFlow());
                break;
            case EFFORTSOURCE:
                ((EffortSource) childElements.get(
                        childElementOfElement[elementIndex]))
                        .setEffort(
                                ((EffortSource) elements
                                        .get(elementIndex))
                                        .getEffort());
                break;
        }
    }

    /**
     * Checks the size of the array which is used for assigning elements and
     * child elements. Needs to be called to provide sufficient array length.
     *
     * @param size Desired array length. If the array has less than this number
     * of elements as its length, it will be expanded to this size.
     */
    protected void checkElementArraySize(int size) {
        if (childElementOfElement.length < size) {
            childElementOfElement = newArrayLength(childElementOfElement, size);
        }

    }

    /**
     * Checks the size of the array which is used for assigning elements and
     * child elements. Needs to be called to provide sufficient array length.
     *
     * @param size Desired array length. If the array has less than this number
     * of elements as its length, it will be expanded to this size.
     */
    protected void checkChildElementArraySize(int size) {
        if (elementOfChildElement.length < size) {
            elementOfChildElement = newArrayLength(elementOfChildElement, size);
        }
    }

    /**
     * Checks the size of the array which is used for assigning nodes and child
     * nodes. Needs to be called to provide sufficient array length. As the
     * Array childNodeOfNode holds information about each node, the size must be
     * the size of child nodes list.
     *
     * @param size Desired array length. If the array has less than this number
     * of elements as its length, it will be expanded to this size.
     */
    protected void checkNodesArraySize(int size) {
        if (childNodeOfNode.length < size) {
            childNodeOfNode = newArrayLength(childNodeOfNode, size);
        }
    }

    /**
     * Checks the size of the array which is used for assigning nodes and child
     * nodes. Needs to be called to provide sufficient array length. As the
     * Array nodeOfChildNode holds information about each child node, the size
     * must be the size of child nodes list.
     *
     * @param size Desired array length. If the array has less than this number
     * of elements as its length, it will be expanded to this size.
     */
    protected void checkChildNodesArraySize(int size) {
        if (nodeOfChildNode.length < size) {
            nodeOfChildNode = newArrayLength(nodeOfChildNode, size);
        }
    }

}
