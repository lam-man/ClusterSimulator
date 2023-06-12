package org.example;

public class Simulator
{
    public static void main( String[] args )
    {
        if (args.length != 2) {
            throw new IllegalArgumentException("User must provide a number of nodes and cluster name for the cluster.");
        }

        String clusterName = args[0];
        int totalNodes = Integer.parseInt(args[1]);
        Cluster myCluster = new Cluster(totalNodes, clusterName);

        // Cluster myCluster = new Cluster(17, "myCluster");

        while (true) {
            myCluster.probe();
        }
    }
}
