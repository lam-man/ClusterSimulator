# ClusterSimulator

## How to compile and run
- Compile command
  `mvn clean compile package`
- Jar file path
  After you run the above command, you should be able to find the `jar` file in `./target` folder.
- Run jar file in terminal
  Simulation program is harded coded to run 5 minutes.
  - `cd target`
  - `java -jar ClusterSimulation-1.0-SNAPSHOT.jar "myCluster" 17`
    - First parameter is cluster name
    - Second parameter for total nodes of the cluster.
- Expected result
  - [17-nodes.log](./assets/17-nodes.log)

## Possible optimizations
- Make ChaosMonkey more realistic
  - Currently, ChaosMonkey is started within the cluster and it is single threaded. It is not realistic. In real world, ChaosMonkey should be started from outside of the cluster in a separate thread.
  - Fault and update could happen at the same time. However, the current simulation makes them randomly happen one after another.

