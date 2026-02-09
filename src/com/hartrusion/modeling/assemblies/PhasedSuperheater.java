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
package com.hartrusion.modeling.assemblies;

import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.converters.PhasedEnergyExchangerHandler;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.phasedfluid.PhasedClosedSteamedReservoir;
import com.hartrusion.modeling.phasedfluid.PhasedElement;
import com.hartrusion.modeling.phasedfluid.PhasedFluidProperties;
import com.hartrusion.modeling.phasedfluid.PhasedNoMassExchangerElement;
import com.hartrusion.modeling.phasedfluid.PhasedNode;
import com.hartrusion.modeling.phasedfluid.PhasedThermalExchanger;
import com.hartrusion.modeling.phasedfluid.PhasedThermalVolumeHandler;

/**
 * Basically a PhasedExchangerNoMass but completely using phased fluid. This is
 * designed to be used as a superheater, the condensation of the fluid will do a
 * superheating of the phased fluid in the secondary part as there is a lower
 * pressure.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedSuperheater {

    private final PhasedThermalExchanger secondaryReservoirLoop;
    private final PhasedNoMassExchangerElement secondarySideCondenser;
    private final PhasedNoMassExchangerElement primarySideCondenser;
    private final PhasedClosedSteamedReservoir primarySideReservoir;

    private final PhasedNode primaryInnerNode = new PhasedNode();
    private final PhasedNode secondaryInnerNode = new PhasedNode();

    private final GeneralNode primaryReservoirThermalNode
            = new GeneralNode(PhysicalDomain.THERMAL);
    private final GeneralNode secondaryThermalNode
            = new GeneralNode(PhysicalDomain.THERMAL);
    private final EffortSource primaryReservoirTemperature
            = new EffortSource(PhysicalDomain.THERMAL);
    private final EffortSource secondaryTemperature
            = new EffortSource(PhysicalDomain.THERMAL);
    private final GeneralNode thermalOriginNode
            = new GeneralNode(PhysicalDomain.THERMAL);
    private final ClosedOrigin thermalOrigin
            = new ClosedOrigin(PhysicalDomain.THERMAL);
    private final LinearDissipator thermalFlowReservoirResistance
            = new LinearDissipator(PhysicalDomain.THERMAL);

    private final PhasedThermalVolumeHandler primaryReservoirHandler;

    private boolean nodesGenerated = false;

    public static final int PRIMARY_IN = 1;
    public static final int PRIMARY_OUT = 2;
    public static final int SECONDARY_IN = 3;
    public static final int SECONDARY_OUT = 4;
    public static final int PRIMARY_INNER = 5;
    public static final int SECONDARY_INNER = 6;

    public PhasedSuperheater(PhasedFluidProperties fluidProperties) {
        // Generate elements that require the PhasedFluidProperties
        secondaryReservoirLoop
                = new PhasedThermalExchanger(fluidProperties);
        secondarySideCondenser
                = new PhasedNoMassExchangerElement(fluidProperties);
        primarySideCondenser
                = new PhasedNoMassExchangerElement(fluidProperties);
        primarySideReservoir
                = new PhasedClosedSteamedReservoir(fluidProperties);
        // Generate a different handler fot the reservoir and attach it to
        // the reservoir element. This handler has the ability to do thermal
        // transfer.
        primaryReservoirHandler = new PhasedThermalVolumeHandler(
                fluidProperties, (PhasedElement) primarySideReservoir);
        primarySideReservoir.setPhasedHandler(primaryReservoirHandler);

        // manually build the thermal network
        thermalOrigin.connectTo(thermalOriginNode);
        primaryReservoirTemperature.connectBetween(
                thermalOriginNode, primaryReservoirThermalNode);
        secondaryTemperature.connectBetween(
                thermalOriginNode, secondaryThermalNode);
        thermalFlowReservoirResistance.connectBetween(
                primaryReservoirThermalNode, secondaryThermalNode);

        // connect the thermal network to the elements
        primaryReservoirHandler.setThermalEffortSource(
                primaryReservoirTemperature);
        // As the primary primaryReservoirHandler was called and not the element
        // itself, we manually need to set the coupled element for the solver.
        primarySideReservoir.setCoupledElement(primaryReservoirTemperature);
        // This call here does call the setCoupledElement:
        secondaryReservoirLoop.setInnerThermalEffortSource(
                secondaryTemperature);

        // connect both primary side and secondary sides elements. This will 
        // occupy node index 0 on all 4 elements.
        primarySideCondenser.connectToVia(
                primarySideReservoir, primaryInnerNode);
        secondarySideCondenser.connectToVia(
                secondaryReservoirLoop, secondaryInnerNode);

        // link both no-mass sides (a cast must be possible here) handlers so
        // they can exchange the heat energy.
        primarySideCondenser.setOtherSide(
                (PhasedEnergyExchangerHandler) secondarySideCondenser
                        .getPhasedHandler());
        secondarySideCondenser.setOtherSide(
                (PhasedEnergyExchangerHandler) primarySideCondenser
                        .getPhasedHandler());

        // Some shortcut for the solver
        //primarySideCondenser.setCoupledElement(secondarySideCondenser);
        //secondarySideCondenser.setCoupledElement(primarySideCondenser);

        // Use an efficiency value of 1.0 for 100 % energy transfer
        ((PhasedEnergyExchangerHandler) primarySideCondenser.getPhasedHandler()).setEfficency(1.0);
        ((PhasedEnergyExchangerHandler) secondarySideCondenser.getPhasedHandler()).setEfficency(1.0);
    }

    /**
     *
     * @param primaryBaseArea Base area of the phased side (m²)
     * @param secondaryMass Constant heated mass inside the secondary side (kg)
     * @param kTimesA Thermal conductance k * A in W/K, k is around 80 and A is
     * the area in m², for the thermal resistor in the reservoir part.
     * @param ambientPressure Minimum pressure, there will be no pressure below
     * this value on the reservoir. Default: 1e5 Pa
     */
    public void initCharacteristic(double primaryBaseArea,
            double secondaryMass, double kTimesA,
            double ambientPressure) {
        primarySideReservoir.setBaseArea(primaryBaseArea);
        primarySideReservoir.setAmbientPressure(ambientPressure);
        secondaryReservoirLoop.getPhasedHandler().setInnerHeatedMass(secondaryMass);
        thermalFlowReservoirResistance.setConductanceParameter(kTimesA);
    }

    /**
     * Generates a set of nodes that are connected to the heat exchangers
     * primary and secondary side. Those nodes can then be named with initName
     * and be accessed with getHeatNode or getPhasedNode.
     * <p>
     * This is not required for the assembly to work, it's just a convenient
     * method that allows generating the required nodes instead of generating
     * them outside and connect them.
     * <p>
     * Nodes can be accessed by using getHeatNode or getPhasedNode, no matter if
     * they were created using this method or outside this assembly.
     */
    public void initGenerateNodes() {
        if (nodesGenerated) {
            throw new IllegalStateException("Nodes were already generated, "
                    + "this must only be called once.");
        }
        nodesGenerated = true;
        GeneralNode node;
        node = new PhasedNode();
        primarySideCondenser.connectTo(node);
        node = new PhasedNode();
        primarySideReservoir.connectTo(node);
        node = new PhasedNode();
        secondarySideCondenser.connectTo(node);
        node = new PhasedNode();
        secondaryReservoirLoop.connectTo(node);
    }

    /**
     * Names elements and nodes of this assembly with the given name prefix. The
     * heat nodes connected to the primary and secondary sides will only be
     * named here if they were created using initGenerateHeatNodes().
     *
     * @param name Desired prefix for all nodes and elements
     */
    public void initName(String name) {
        primarySideCondenser.setName(
                name + "PrimarySideCondenser");
        primarySideReservoir.setName(
                name + "PrimarySideReservoir");
        secondarySideCondenser.setName(
                name + "SecondarySideCondenser");
        secondaryReservoirLoop.setName(
                name + "SecondaryReservoirLoop");
        if (nodesGenerated) {
            getPhasedNode(PRIMARY_IN).setName(
                    name + "PrimaryIn");
            getPhasedNode(PRIMARY_OUT).setName(
                    name + "PrimaryOut");
            getPhasedNode(SECONDARY_IN).setName(
                    name + "SecondaryIn");
            getPhasedNode(SECONDARY_OUT).setName(
                    name + "SecondaryOut");
        }
        primaryInnerNode.setName(
                name + "PrimaryMiddleNode");
        secondaryInnerNode.setName(
                name + "SecondaryMiddleNode");
        // Thermal network
        thermalOrigin.setName(name + "ThermalOrigin");
        thermalOriginNode.setName(name + "ThermalOrigin");
        primaryReservoirThermalNode.setName(
                name + "PrimaryReservoirThermalNode");
        secondaryThermalNode.setName(
                name + "SecondaryThermalNode");
        primaryReservoirTemperature.setName(
                name + "PrimaryReservoirTemperature");
        secondaryTemperature.setName(
                name + "SecondaryTemperature");
        thermalFlowReservoirResistance.setName(
                name + "ThermalFlowReservoirResistance");
    }

    /**
     * Sets the fluid temperature on both sides (initial condition). This also
     * sets the pressure on the primary side.
     * <p>
     * The initial condition with this method is restricted to fluid phase only,
     * meaning there is X=0 in the mass inside the primary side condenser.
     * <p>
     * The pressure will be set to saturation pressure of the given temperature.
     *
     * @param primaryTemp in Kelvin
     * @param secondaryHeatEnergy Energy in the mass of the secondary reservoir.
     * @param fillHeight fill level of the primary reservoir (meters)
     * @param secondaryPressure pressure of the secondary loop (needed to get
     * the temperature out of the secondaryHeatEnergy value).
     *
     */
    public void initConditions(double primaryTemp, double secondaryHeatEnergy,
            double fillHeight, double secondaryPressure) {
        // get properties from the element that knows it.
        PhasedFluidProperties fp
                = primarySideReservoir.getPhasedFluidProperties();
        double pressure = fp.getSaturationEffort(primaryTemp);
        // Limit the pressure to be at least the ambient pressure, this is 
        // important fo setting the correct previousPressure before anythign
        // can be calculated automatically. The reservoir will later force the 
        // effort and pressure but this is not known during the call of this
        // method.
        if (pressure < primarySideReservoir.getAmbientPressure()) {
            pressure = primarySideReservoir.getAmbientPressure();
        }
        // Reservoir:
        primaryReservoirHandler.setInitialHeatEnergy(
                primaryTemp * fp.getSpecificHeatCapacity());
        primarySideReservoir.setInitialEffort(pressure);
        secondaryReservoirLoop.getPhasedHandler().setInitialHeatEnergy(
                secondaryHeatEnergy);
        ((PhasedThermalVolumeHandler) secondaryReservoirLoop.getPhasedHandler())
                .setPreviousPressure(secondaryPressure);
        primarySideReservoir.setInitialState(
                primarySideReservoir.getMassForHeight(fillHeight, primaryTemp),
                primaryTemp);
    }

    /**
     * Access the phased nodes which are connected with this phased condenser.
     *
     * @param identifier can be PRIMARY_IN (1), PRIMARY_OUT (2)
     * @return PhasedNode
     */
    public PhasedNode getPhasedNode(int identifier) {
        switch (identifier) {
            case PRIMARY_IN:
                return (PhasedNode) primarySideCondenser.getNode(1);
            case PRIMARY_OUT:
                return (PhasedNode) primarySideReservoir.getNode(1);
            case PRIMARY_INNER:
                return primaryInnerNode;
            case SECONDARY_IN:
                return (PhasedNode) secondaryReservoirLoop.getNode(1);
            case SECONDARY_OUT:
                return (PhasedNode) secondarySideCondenser.getNode(1);
            case SECONDARY_INNER:
                return secondaryInnerNode;
        }
        return null;
    }

    public PhasedNoMassExchangerElement getPrimarySideCondenser() {
        return primarySideCondenser;
    }

    public PhasedClosedSteamedReservoir getPrimarySideReservoir() {
        return primarySideReservoir;
    }

    public PhasedThermalExchanger getSecondarySideReservoir() {
        return secondaryReservoirLoop;
    }

    public PhasedNoMassExchangerElement getSecondarysideCondenser() {
        return secondarySideCondenser;
    }

    /**
     * Gets the mass flow into the primary condenser. Positive means it goes
     * into the condenser (expected behavior).
     *
     * @return value in kg/s
     */
    public double getPrimaryInFlow() {
        return -primaryInnerNode.getFlow(primarySideCondenser);
    }

    /**
     * Gets the mass flow out of the primary condensers reservoir (condensate).
     * Positive means it goes out of the reservoir (expected behavior).
     *
     * @return value in kg/s
     */
    public double getPrimaryOutFlow() {
        // it is possible to add more nodes to the reservoir so the out flow 
        // will be generated by adding all flows to all nodes except the one 
        // that is the connection between reservoir and condenser in this 
        // assembly.
        double flow = 0.0;
        int numberOfNodes = primarySideReservoir.getNumberOfNodes();
        for (int idx = 0; idx < numberOfNodes; idx++) {
            if (primarySideReservoir.getNode(idx) == primaryInnerNode) {
                continue;
            }
            // sum up negative as we want to have outflow as a positive value.
            flow -= primarySideReservoir.getNode(idx)
                    .getFlow(primarySideReservoir);
        }
        return flow;
    }
}
