package org.example;

public class Simulator
{
    public static void main( String[] args )
    {
        if (args.length < 2) {
            throw new IllegalArgumentException("User must provide at least a number of nodes and cluster name for the cluster.");
        }

        String clusterName = args[0];
        int totalNodes = Integer.parseInt(args[1]);
        Cluster myCluster = new Cluster(totalNodes, clusterName);

        // Cluster myCluster = new Cluster(17, "myCluster");

        long startTime = System.currentTimeMillis();
        int minutes = 3;
        if (args.length > 2) {
            minutes = Integer.parseInt(args[2]);
            if (minutes < 1) {
                throw new IllegalArgumentException("User must provide a valid number (at least 1) of minutes to run the simulation.");
            }
        }
        long maxRunTime = minutes * 60 * 1000; // 5 minutes in milliseconds

        while (System.currentTimeMillis() - startTime < maxRunTime) {
            myCluster.probe();
        }
    }
}
