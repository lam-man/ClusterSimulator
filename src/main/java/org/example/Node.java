package org.example;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Node {
    private int nodeIndex;
    private boolean inAGroup;

    private NodeState nodeState;
    private static final String INDENT = "    ";

    public Node(int nodeIndex, boolean inAGroup, NodeState nodeState) {
        this.nodeIndex = nodeIndex;
        this.inAGroup = inAGroup;
        this.nodeState = nodeState;
    }

    public boolean isInAGroup() {
        return inAGroup;
    }

    public void setInAGroup(boolean inAGroup) {
        this.inAGroup = inAGroup;
    }

    public NodeState getNodeState() {
        return nodeState;
    }

    public void setNodeState(NodeState nodeState) {
        this.nodeState = nodeState;
    }

    @Override
    public String toString() {
        return INDENT + "Node " + nodeIndex + " [State: " + nodeState + ", inAGroup: " + inAGroup + "]";
    }
}
