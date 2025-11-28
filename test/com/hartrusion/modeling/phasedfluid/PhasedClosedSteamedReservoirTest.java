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
package com.hartrusion.modeling.phasedfluid;

import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.OpenOrigin;
import com.hartrusion.modeling.solvers.DomainAnalogySolver;
import com.hartrusion.modeling.solvers.SimpleIterator;
import com.hartrusion.util.SimpleLogOut;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests the Phased Closed Steamed Reservoir in combination with the thermal
 * exchanger element using the main solver.
 * <p>
 * Fluid can be pumped in circulation through the reservoir and the thermal
 * element. Thermal system is a forced flow source.
 * <p>
 * This is designed to validate conservation of energy.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedClosedSteamedReservoirTest {
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    PhasedPropertiesWater fluidProperties;
    PhasedClosedSteamedReservoir reservoir;
    PhasedExpandingThermalExchanger heater;
    PhasedNode inNode;
    PhasedNode outNode;
    PhasedNode srcNode;
    PhasedFlowSource flowSource;
    OpenOrigin thermalOrigin;
    GeneralNode thermalOriginNode;
    FlowSource thermalFlowSource;
    GeneralNode thermalTemperatureNode;
    DomainAnalogySolver solver;

    @BeforeMethod
    public void setUpMethod() throws Exception {
        fluidProperties = new PhasedPropertiesWater();

        reservoir = new PhasedClosedSteamedReservoir(fluidProperties);
        heater = new PhasedExpandingThermalExchanger(fluidProperties);
        inNode = new PhasedNode();
        outNode = new PhasedNode();
        srcNode = new PhasedNode();
        flowSource = new PhasedFlowSource();

        thermalOrigin = new OpenOrigin(PhysicalDomain.THERMAL);
        thermalOriginNode = new GeneralNode(PhysicalDomain.THERMAL);
        thermalFlowSource = new FlowSource(PhysicalDomain.THERMAL);
        thermalTemperatureNode = new GeneralNode(PhysicalDomain.THERMAL);

        // Initialize and build model
        reservoir.setName("Reservoir");
        heater.setName("Heater");
        heater.initComponent();
        reservoir.connectTo(inNode);
        reservoir.connectTo(outNode);
        flowSource.connectBetween(outNode, srcNode);
        flowSource.setFlow(0.0); // initialize with 0.0
        heater.connectBetween(srcNode, inNode);
        
        // Thermal system: Forced flow source
        thermalOrigin.connectToVia(thermalFlowSource, thermalOriginNode);
        thermalFlowSource.connectToVia(heater.getInnerThermalEffortSource(),
                thermalTemperatureNode);
        thermalFlowSource.setFlow(0.0); // initialize with 0.0
        
        solver = new DomainAnalogySolver();
        solver.addNetwork(srcNode);
        
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
        fluidProperties = null;
        reservoir = null;
        heater = null;
        inNode = null;
        outNode = null;
        srcNode = null;
        flowSource = null;
        thermalOrigin = null;
        thermalOriginNode = null;
        thermalFlowSource = null;
        thermalTemperatureNode = null;
    }

    /**
     * Validates the initial set energy when no flow is applied.
     */
    @Test
    public void testValidateEnergyNoFlow() {
        double temperature = 280;
        double specificHeatEnergy 
                = temperature * fluidProperties.getSpecificHeatCapacity();
        
        reservoir.setInitialState(100, temperature);
        heater.setInitialState(0.1, 1e5, temperature, temperature);
        
        for (int idx = 0; idx < 10; idx++) {
            solver.prepareCalculation();
            solver.doCalculation();
            
            assertEquals(reservoir.getPhasedHandler().getHeatEnergy(),
                   specificHeatEnergy, 1e-8);
            assertEquals(heater.getPhasedHandler().getHeatEnergy(),
                   specificHeatEnergy, 1e-8);
        }
    }
    
    /**
     * Validates the initial set energy when the heat fluid is circulated. Still
     * there is no thermal heat energy applied.
     */
    @Test
    public void testValidateEnergyCirculation() {
        double temperature = 280;
        double specificHeatEnergy 
                = temperature * fluidProperties.getSpecificHeatCapacity();
        
        reservoir.setInitialState(100, temperature);
        heater.setInitialState(0.1, 1e5, temperature, temperature);
        
        flowSource.setFlow(10.0); // 10 kg/s with 100 kg in reservoir and heater
        
        for (int idx = 0; idx < 10; idx++) {
            solver.prepareCalculation();
            solver.doCalculation();
            
            assertEquals(reservoir.getPhasedHandler().getHeatEnergy(),
                   specificHeatEnergy, 1e-8);
            assertEquals(heater.getPhasedHandler().getHeatEnergy(),
                   specificHeatEnergy, 1e-8);
        }
    }

}
