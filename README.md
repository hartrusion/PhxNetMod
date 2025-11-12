# PhxNetMod - Physical Complex Network Modeling

This modeling library is designed to describe and solve complex physical 
networks by using the analogy between physical domains. As the time 
behaviour of dynamic systems in electronics, mechanics, hydraulics and 
thermal systems can be described with very similar linear ordinary 
differential equations, the circuit analysis can be used on networks of 
hydraulic systems as it would be used in electronic circuits calculation. This 
is also addressed by a theory called **bond graphs**.

**Please note: This is work in process. It's probably not good code and only
published to have easy access to it and disclose source code of my projects
for security reasons.** I strongly believe that being able to disclose the 
full source and allow people to compile and build projects themselves is
a proper way to prove that an application does not pose any risks.

It was designed as the core of the chornobyl RBMK simulator project.

The usual academic approach is to generate a state space representation of 
a network model or use nodal analysis to calculate the state of a physical 
network for each step. However, this has the downside that the matrix 
describing the network has to have certain properties which do not allow 
unlimited or absolutely zero flow rates like you would have them if you 
shortcut or cut a wire in a circuit.

The focus on this project is to provide stable solutions outside operating 
range. For example, the heat exchanger classes are very inaccurate in terms of 
thermal transfer, but they provide solutions for all possible cases, even when 
the flow on one side is zero or even reversed.

## Model description
The model is set up by connecting elements to common nodes. The nodes 
actually hold most of the information. There are some basic, general domains 
available (like thermal, electric, hydraulic). Those can be extended to heat,
steam or two phased fluids.

Each node has a so-called effort variable which can be, depending on the 
physical domain, voltage or pressure for example. It also holds flow values 
with flows to each element which can be amperage or mass flow. Check the
package-info files for more information.

Heat fluid, phased fluid and steam are some extensions that use the network 
to calculate flow directions first and provide scalar values on top of 
those basic elements.

## Solver
Contrary to popular solutions the solvers supplied here compile the 
network as multiple electronic circuits and apply simplifications 
recursively, making a calculation from top to bottom and back to make a full 
calculation of all flows and efforts in the network. This allows shortcuts and
open connections as it is clear what happens if you put a shortcut parallel 
or an open connection in series. Having bridged connections which force equal  
effort values or open connections which force a flow of zero are explicit 
features of this modeling approach and the solving algorithm takes care of that.

The library provies a low accuracy solution using the most simple euler 
method for the next time step after the network solution is obtained.

This does not give accurate solutions but allows to simulate complex 
networks like they are used in thermal power plants in real time.

## Usage
To set up your physical model, create nodes and elements which describe it 
using the elements provided in the packages. There are some converters 
available, for example from heat fluid to phased fluid.

Some examples on how to build (not how to solve!) a model can be seen in 
the test packages, the ones in **com.hartrusion.modeling.solvers** are more
complex, like in the **TransferSubnetTest.java**.

After configuring the model, you can use **DomainAnalogySolver** class to 
compile the network by just calling **addNetwork** with any node. 
Calling **prepareCalculation** will reset the network to a non-calculated 
state for the next time step and **doCalculation** will update the network.

Values must be obtained from elements and nodes, keep in mind that most 
values are actually hold by the nodes itself.
