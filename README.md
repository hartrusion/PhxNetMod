# Domain Analogy Modeling

This modeling library is designed to describe and solve complex physical 
networks by using the analogy between physical domains. As the time 
behaviour of dynamic systems in electronics, mechanics, hydraulics and 
thermal systems can be described with very similar linear ordinary 
differential equations, the circuit analysis can be used on networks of 
hydraulic systems as it would be used in electronic circuits calculation. This 
is also addressed by a theory called bond graphs.

It was designed as the core of the chornobyl RBMK simulator project.

The usual academic  approach is to generate a state space representation of 
a network model or use nodal analysis to calculate the state of a physical 
network for each step.  However, this has the downside that the matrix 
describing the network has to have certain properties which do not allow 
unlimited or absolutely zero flow rates like you would have them if you 
shortcut or cut a wire in a circuit.

## Model description
The model is set up by connecting elements to common nodes. The nodes 
actually hold most of the information. There are some basic, general domains 
available (like thermal, electric, hydraulic). Those can be extended to heat,
steam or two phased fluids.

## Solver
Contrary to popular solutions the solvers supplied here translate the 
network into multiple electronic circuits and apply simplifications 
recursively, making a calculation from top to bottom and back to make a full 
calculation of all flows and efforts in the network. This allows shortcuts and
open connections as it is clear what happens if you put a shortcut parallel 
or an open connection in series.
The library provies a low accuracy solution using the most simple euler 
method for the next time step after the network solution is obtained.

This does not give accurate solutions but allows to simulate complex 
networks like they are used in thermal power plants in real time.