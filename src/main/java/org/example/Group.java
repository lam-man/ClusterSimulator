package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Group {
    private static final String INDENT = "    ";

    public String getGroupName() {
        return groupName;
    }

    private String groupName;

    private List<Node> nodes;

    public Group(String groupName, List<Node> nodes) {
        this.groupName = groupName;
        this.nodes = new ArrayList<>(nodes);
    }

    public List<Node> getHealthyNodes() {
        List<Node> healthyNodes = new ArrayList<>();
        for (Node node : this.nodes) {
            if (node.getNodeState() == NodeState.RUNNING) {
                healthyNodes.add(node);
            }
        }
        return healthyNodes;
    }

    @Override
    public String toString() {

        StringBuilder infoBuilder = new StringBuilder("Group " + groupName + " info: [");
        synchronized (this) {
            for (Node node : nodes) {
                infoBuilder.append(System.lineSeparator());
                infoBuilder.append(INDENT);
                infoBuilder.append(node.toString());
            }
            infoBuilder.append(" ]");
        }

        return infoBuilder.toString();
    }

    public void removeNode(Node node) {
        nodes.remove(node);
    }

    public void addNode(Node node) {
        nodes.add(node);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }
}
