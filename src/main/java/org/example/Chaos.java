package org.example;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class Chaos {

    public static int updateTrigger(List<List<Node>> updateDomains) {

        Random random = new Random();

        int domainIndex = random.nextInt(updateDomains.size());
        for (Node node : updateDomains.get(domainIndex)) {
            if (node.getNodeState() == NodeState.RUNNING) {
                node.setNodeState(NodeState.UPDATING);
            }
        }

        return domainIndex;
    }

    public static void updateFix(List<List<Node>> updateDomains, int domainIndex) {
        for (Node node : updateDomains.get(domainIndex)) {
            if (node.getNodeState() == NodeState.UPDATING) {
                node.setNodeState(NodeState.RUNNING);
            }
        }
    }

    public static void faultFix(List<List<Node>> faultDomains, int domainIndex) {
        for (Node node : faultDomains.get(domainIndex)) {
            if (node.getNodeState() == NodeState.DOWN) {
                node.setNodeState(NodeState.RUNNING);
            }
        }

        try {
            Thread.sleep(10000);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public static int faultTrigger(List<List<Node>> faultDomains) {

        Random random = new Random();

        int domainIndex = random.nextInt(faultDomains.size());
        for (Node node : faultDomains.get(domainIndex)) {
            if (node.getNodeState() != NodeState.DOWN) {
                node.setNodeState(NodeState.DOWN);
            }
        }

        try {
            Thread.sleep(10000);
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        return domainIndex;
    }
}
