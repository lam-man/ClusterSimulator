package org.example;

import java.util.List;
import java.util.Random;

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

    public static void updateOrFaultFix(List<List<Node>> domains, int domainIndex, NodeState fromState) {
        for (Node node : domains.get(domainIndex)) {
            if (node.getNodeState() == fromState) {
                node.setNodeState(NodeState.RUNNING);
            }
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
