/*
 * The MIT License
 *
 * Copyright 2026 Viktor Alexander Hartung.
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
/**
 * Material layer which adds the transport of material concentrations to an
 * existing network. A material concentration is a scalar property of the
 * fluid, like a conductivity or an impurity, which mixes ideally and is
 * calculated exactly like the temperature in the heat fluid package: Elements
 * without an own content pass the concentration through, elements with an own
 * content mix the inflow ideally with their content and nodes mix all inflows
 * ideally.
 *
 * <p>
 * Contrary to the heat fluid package, the materials are not part of the node
 * and element classes. Instead, MaterialLayer.add searches an existing network
 * and attaches a MaterialNode to each GeneralNode and a MaterialElement to each
 * AbstractElement. The general nodes and elements call the material objects
 * from their prepareCalculation and doCalculation methods, so the
 * concentrations are calculated by any solver that calculates the network.
 * Any number of materials can be added, which are held in arrays and are
 * identified by a unique name.
 *
 * <p>
 * The concentrations inside the elements can be changed by a material
 * manipulation, which is switched on and off per material with
 * setMaterialManipulation of the MaterialElement, also while the model is
 * running. It mixes a virtual flow with a target concentration into the
 * element, which does not exist in the network itself. An element with a mass
 * m approaches the target concentration with the time constant m divided by
 * the flow rate of the virtual flow.
 *
 * <p>
 * A PhasedClosedSteamedReservoir, which is a steam drum, gets a
 * MaterialElementMassSplitter, where the highest concentration of a material
 * can be limited for the flow leaving through a defined node, like the steam
 * outlet. The material that is held back stays in the drum and enriches it.
 */
package com.hartrusion.modeling.material;
