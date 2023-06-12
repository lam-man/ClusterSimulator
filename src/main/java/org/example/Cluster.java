package org.example;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Cluster {

    private static final int GROUP_SIZE = 3;
    private static final int FAULT_DOMAIN_COUNT = 3;
    private static final String INDENT = "    ";
    private static final int MIN_UPDATE_DOMAIN = 5;
    private static final int MAX_UPDATE_DOMAIN = 20;
    private static final String GROUP_NAME_PREFIX = "Group-";

    private String clusterName;
    private int totalNodes;
    private boolean isUpdating;
    private boolean isRepairing;
    private List<Node> backupPool;
    private List<List<Node>> faultDomains;
    private List<List<Node>> updateDomains;
    public HashMap<String, Group> groups;
    public HashMap<Node, Group> nodeGroupMap;
    private List<Node> nodePool;

    public Cluster(int totalNodes, String clusterName) {
        this.totalNodes = totalNodes;
        this.clusterName = clusterName;

        initCluster(totalNodes);
        report();
    }

    public synchronized String getInfo() {
        StringBuilder clusterInfo = new StringBuilder(clusterName + " info: [");
        for (Group group : groups.values()) {
            clusterInfo.append(System.lineSeparator());
            clusterInfo.append(INDENT);
            clusterInfo.append(group.toString());
        }

        if (backupPool.size() != 0) {
            clusterInfo.append(System.lineSeparator());
            clusterInfo.append(INDENT + "Backup Pool info: [");
            for (Node node : backupPool) {
                clusterInfo.append(System.lineSeparator());
                clusterInfo.append(INDENT);

                clusterInfo.append(node.toString());
            }
            clusterInfo.append(" ]");
        }

        clusterInfo.append(" ]");
        return clusterInfo.toString();
    }

    public List<List<Node>> getFaultDomains() {
        return faultDomains;
    }

    public List<List<Node>> getUpdateDomains() {
        return updateDomains;
    }

    public void report() {
        LocalTime currentTime = LocalTime.now();
        System.out.println("Time: " + currentTime);
        System.out.println(getInfo());
    }

    public void probe() {
        List<String> unavailableGroups = new ArrayList<>();
        List<Node> unavailableNodes = new ArrayList<>();
//        int updateDomainIndex = Chaos.updateTrigger(updateDomains);
        int faultDomainIndex = Chaos.faultTrigger(faultDomains);
        report();
        for (Node node : nodePool) {
            if (node.getNodeState() != NodeState.RUNNING) {
                Group group = nodeGroupMap.get(node);
                if (group == null) {
                    continue;
                }
                String groupName = group.getGroupName();
                unavailableGroups.add(groupName);
                unavailableNodes.add(node);
            }
        }
        // Make sure there is available gruops
        if (unavailableGroups.size() > 0 && unavailableGroups.size() < groups.size()) {
            for (String groupName : unavailableGroups) {
                trafficRerouting(groupName);
            }
        }

        if (unavailableGroups.size() > 0) {
            reGroup(unavailableNodes);
        }

        report();
//        Chaos.updateFix(updateDomains, updateDomainIndex);
        Chaos.faultFix(faultDomains, faultDomainIndex);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    private void trafficRerouting(String groupName) {
        System.out.println(String.format("Group %s is not available and traffic are rerouted to other groups.", groupName));
    }

    private void reGroup(List<Node> unavailableNodes) {
        System.out.println("===========Re-group started.===========");
        int replaced = useBackupPool(unavailableNodes);

        if (unavailableNodes.size() > 1) {
            shuffle(unavailableNodes, replaced);
        }
    }

    private void shuffle(List<Node> unavailableNodes, int replaced) {
        List<Node> allHealthyNodes = new ArrayList<>();
        int start = replaced > 0 ? replaced - 1 : replaced;
        for (int i = start; i < unavailableNodes.size(); i++) {
            Node node = unavailableNodes.get(i);
            Group group = nodeGroupMap.get(node);
            if (group != null) {
                List<Node> temp = group.getHealthyNodes();
                allHealthyNodes.addAll(temp);
            }
        }

        if (allHealthyNodes.size() < 3) {
            System.out.println("===========Not enough healthy nodes for shuffling and wait for auto recover.===========");
            return;
        }
        System.out.println("===========Fix cluster by shuffle and re-group nodes.===========");

        Collections.shuffle(allHealthyNodes);
        List<List<Node>> shuffledNodes = new ArrayList<>();
        for (int i = 0; i < allHealthyNodes.size(); i += GROUP_SIZE) {
            int endIndex = Math.min(i + GROUP_SIZE, shuffledNodes.size());

            List<Node> groupNodes = allHealthyNodes.subList(i, endIndex);
            if (groupNodes.size() < GROUP_SIZE) {
                backupPool.addAll(groupNodes);
                return;
            }

            shuffledNodes.add(groupNodes);
        }

        shuffledNodes.add(unavailableNodes);

        for (Node node : unavailableNodes) {
            Group group = nodeGroupMap.get(node);
            if (shuffledNodes.size() > 0) {
                List<Node> newNodes = shuffledNodes.get(0);
                if (newNodes.size() == GROUP_SIZE) {
                    group.setNodes(new CopyOnWriteArrayList<>(newNodes));
                } else {
                    backupPool.addAll(newNodes);
                    for (Node availableNode : newNodes) {
                        availableNode.setInAGroup(false);
                    }
                }
                shuffledNodes.remove(0);
            }
        }
    }

    private int useBackupPool(List<Node> unavailableNodes) {
        int replaced = 0;
        if (backupPool.size() == 0) {
            System.out.println("===========Backup pool doesn't have any nodes.===========");
            return replaced;
        }

        System.out.println("===========Fix cluster by using nodes from backup pool.===========");

        List<Node> newBackupPool = new ArrayList<>();
        for (Node node : unavailableNodes) {
            if (backupPool.size() > 0) {
                Group currentGroup = nodeGroupMap.get(node);
                currentGroup.removeNode(node);
                Node nodeToFill = backupPool.get(0);
                if (nodeToFill.getNodeState() == NodeState.RUNNING) {
                    backupPool.remove(0);
                    currentGroup.addNode(nodeToFill);
                    nodeToFill.setInAGroup(true);
                    nodeGroupMap.put(nodeToFill, currentGroup);
                    node.setInAGroup(false);
                    newBackupPool.add(node);
                    nodeGroupMap.remove(node);
                    replaced++;
                }
            }
        }
        if (newBackupPool.size() > 0) {
            backupPool = newBackupPool;
        }
        return replaced;
    }

    private void initCluster(int totalNodes) {
        if (totalNodes < 3) {
            throw new IllegalArgumentException("A cluster must have at least 3 nodes.");
        }

        nodePool = new ArrayList<>();
        backupPool = new ArrayList<>();
        faultDomains = new ArrayList<>();
        updateDomains = new ArrayList<>();
        groups = new HashMap<>();
        nodeGroupMap = new HashMap<>();
        isRepairing = false;
        isUpdating = false;

        for (int i = 0; i < totalNodes; i++) {
            nodePool.add(new Node(i, false, NodeState.RUNNING));
        }

        setFaultDomains();
        setUpdateDomains();
        setGroups();
    }

    private void setFaultDomains() {
        int faultDomainSize = totalNodes / FAULT_DOMAIN_COUNT;
        for (int i = 0; i < FAULT_DOMAIN_COUNT; i++) {
            int startIndex = i * faultDomainSize;
            int endIndex = Math.min(startIndex + faultDomainSize, nodePool.size());
            List<Node> domainNodes = nodePool.subList(startIndex, endIndex);
            faultDomains.add(new CopyOnWriteArrayList<>(domainNodes));
        }
    }

    private void setUpdateDomains() {
        int updateDomainCount = getUpdateDomainCount(totalNodes);
        int updateDomainSize = totalNodes / updateDomainCount;
        for (int i = 0; i < updateDomainCount; i++) {
            int startIndex = i * updateDomainSize;
            int endIndex = Math.min(startIndex + updateDomainSize, nodePool.size());
            List<Node> domainNodes = nodePool.subList(startIndex, endIndex);
            updateDomains.add(new CopyOnWriteArrayList<>(domainNodes));
        }
    }

    private int getUpdateDomainCount(int totalNodes) {
        int updateDomainCount = totalNodes / MIN_UPDATE_DOMAIN + MIN_UPDATE_DOMAIN;

        if (updateDomainCount > MAX_UPDATE_DOMAIN) {
            return MAX_UPDATE_DOMAIN;
        }
        return updateDomainCount;
    }

    private void setGroups() {
        randomGroup(nodePool);
    }

    private void randomGroup(List<Node> inputList) {
        for (int i = 0; i < inputList.size(); i += GROUP_SIZE) {
            int endIndex = Math.min(i + GROUP_SIZE, this.nodePool.size());

            List<Node> groupNodes = inputList.subList(i, endIndex);
            if (groupNodes.size() < GROUP_SIZE) {
                backupPool.addAll(groupNodes);
                return;
            }
            String groupName = i == 0 ? GROUP_NAME_PREFIX + i : GROUP_NAME_PREFIX + (i / GROUP_SIZE);

            Group group = new Group(groupName, groupNodes);

            for (Node node : groupNodes) {
                node.setInAGroup(true);
                nodeGroupMap.put(node, group);
            }

            groups.put(groupName, group);
        }
    }

    public boolean isUpdating() {
        return isUpdating;
    }

    public void setUpdating(boolean updating) {
        isUpdating = updating;
    }

    public boolean isRepairing() {
        return isRepairing;
    }

    public void setRepairing(boolean repairing) {
        isRepairing = repairing;
    }
}
