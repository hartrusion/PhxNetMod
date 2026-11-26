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
package com.hartrusion.modeling.material;

import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.MassedElement;

/**
 * Material part of an element with an own content, like a tank, a reservoir
 * or a flow resistance with a volume, which mixes all inflows ideally with its
 * content. The calculation is the same as the HeatVolumizedHandler does for
 * the temperature: All outflows leave the element with the concentration of
 * the content, and the concentration of the content for the next run is
 * calculated from the content mass and the inflow of the current run, which
 * delays the transport through the element by one time step. An active
 * material manipulation adds its virtual flow with the target concentration
 * to the inflow of a material, which also works without any real inflow.
 *
 * <p>
 * The mass of the content is read from the element if it implements the
 * MassedElement interface, so the mass that is configured on the element with
 * setMass or setInitialMass is used without setting it again here, and the
 * changing mass of tanks and reservoirs is followed in each run. Only for
 * elements without that interface, like general capacitances, the mass is
 * held by this object and has to be set with its own setMass method.
 *
 * <p>
 * To keep the network stable, a mass of zero or below does not mix at all and
 * the element keeps its last concentration instead of throwing an exception.
 * As the own mass is 0.0 until it is set, elements without the MassedElement
 * interface keep their concentration constant until a mass is assigned.
 */
public class MaterialElementMass extends MaterialElement {

    /**
     * Concentration of each material in the content of the element, which is
     * the state variable and also the concentration of all outflows.
     */
    private double[] concentration = new double[0];

    /**
     * Concentration of each material for the next run, which becomes the
     * current concentration on the next prepareMaterialCalculation call.
     */
    private double[] nextConcentration = new double[0];

    /**
     * Marks that nextConcentration was calculated in the current run. It
     * prevents a second calculation in the same run and makes repeated
     * prepareMaterialCalculation calls take over the value only once.
     */
    private boolean concentrationPrepared = false;

    /**
     * Mass of the content in the unit of the integral of the flow, so with a
     * flow in kg/s this is a mass in kg and with a volume flow it is a volume.
     * It is only used if the element does not implement MassedElement.
     */
    private double mass = 0.0;

    /**
     * Time step in seconds which is used to integrate the inflow.
     */
    private double stepTime = 0.1;

    public MaterialElementMass(AbstractElement element) {
        super(element);
    }

    @Override
    public boolean addMaterial(String materialName) {
        if (!super.addMaterial(materialName)) {
            return false;
        }
        concentration = MaterialNode.extendArray(concentration,
                getMaterialCount());
        nextConcentration = MaterialNode.extendArray(nextConcentration,
                getMaterialCount());
        return true;
    }

    @Override
    public void prepareMaterialCalculation() {
        if (concentrationPrepared) {
            System.arraycopy(nextConcentration, 0, concentration, 0,
                    concentration.length);
        }
        concentrationPrepared = false;
    }

    /**
     * Sets the concentration of the content on all outflows and calculates the
     * concentration for the next run as soon as all inflows are known.
     *
     * @return true if something was calculated.
     */
    @Override
    protected boolean calculateElementConcentrations() {
        boolean didSomething = false;
        double flow;
        // Outflows leave the element with the current concentration, which
        // does not depend on the inflows of this run.
        for (int idx = 0; idx < materialNodes.length; idx++) {
            if (!materialNodes[idx].getNode().flowUpdated(element)) {
                continue;
            }
            flow = getInflow(idx);
            if (flow == 0.0) {
                didSomething = setOutflowNoConcentration(idx) || didSomething;
            } else if (flow < 0.0) {
                didSomething = setOutflowConcentrations(idx, concentration)
                        || didSomething;
            }
        }
        if (concentrationPrepared || !allFlowsUpdated()
                || !allConcentrationsUpdated()) {
            return didSomething;
        }
        double inflowAmount = 0.0;
        double contentMass = getMass();
        for (int mdx = 0; mdx < nextConcentration.length; mdx++) {
            nextConcentration[mdx] = contentMass * concentration[mdx];
        }
        for (int idx = 0; idx < materialNodes.length; idx++) {
            flow = getInflow(idx);
            if (flow <= 0.0
                    || materialNodes[idx].hasNoConcentration(
                            connectionIndex[idx])) {
                continue;
            }
            inflowAmount = inflowAmount + flow * stepTime;
            for (int mdx = 0; mdx < nextConcentration.length; mdx++) {
                nextConcentration[mdx] = nextConcentration[mdx]
                        + flow * stepTime * materialNodes[idx]
                                .getConcentration(connectionIndex[idx], mdx);
            }
        }
        double manipulationAmount;
        for (int mdx = 0; mdx < nextConcentration.length; mdx++) {
            manipulationAmount = getActiveManipulationFlow(mdx) * stepTime;
            if (contentMass > 0.0
                    && inflowAmount + manipulationAmount > 0.0) {
                nextConcentration[mdx] = (nextConcentration[mdx]
                        + manipulationAmount * getManipulationTarget(mdx))
                        / (contentMass + inflowAmount + manipulationAmount);
            } else {
                // No mass to mix with or nothing flows in, keep the value.
                nextConcentration[mdx] = concentration[mdx];
            }
        }
        concentrationPrepared = true;
        return true;
    }

    @Override
    public boolean isMaterialCalculationFinished() {
        return concentrationPrepared && allConcentrationsUpdated();
    }

    /**
     * Sets the mass of the content which the inflows are mixed with, which is
     * only used for elements that do not implement MassedElement, as the mass
     * of the element itself is used otherwise. A mass of zero or below makes
     * the element keep its concentration.
     *
     * @param mass Mass in the unit of the integral of the flow.
     */
    public void setMass(double mass) {
        this.mass = mass;
    }

    /**
     * Returns the mass of the content which the inflows are mixed with. This
     * is the current mass of the element if it implements MassedElement and
     * the mass set on this object otherwise.
     *
     * @return Mass in the unit of the integral of the flow.
     */
    public double getMass() {
        if (element instanceof MassedElement massedElement) {
            return massedElement.getMass();
        }
        return mass;
    }

    public void setStepTime(double dt) {
        stepTime = dt;
    }

    /**
     * Sets the concentration of a material in the content, which is used to
     * define initial conditions.
     *
     * @param materialName Unique name of the material.
     * @param value Concentration value.
     */
    public void setConcentration(String materialName, double value) {
        int idx = getMaterialIndex(materialName);
        concentration[idx] = value;
        nextConcentration[idx] = value;
    }

    public double getConcentration(String materialName) {
        return concentration[getMaterialIndex(materialName)];
    }
}
