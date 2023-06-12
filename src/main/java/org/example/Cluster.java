package org.example;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

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
        List<Group> unavailableGroups = new ArrayList<>();
        List<Node> unavailableNodes = new ArrayList<>();

        int domainIndex = -1;
        Random random = new Random();
        int randomInt = random.nextInt(2);

        if (randomInt == 0) {
            domainIndex = Chaos.updateTrigger(updateDomains);
        } else {
            domainIndex = Chaos.faultTrigger(faultDomains);
        }

        report();

        for (Node node : nodePool) {
            if (node.getNodeState() != NodeState.RUNNING) {
                Group group = nodeGroupMap.get(node);
                if (group != null && !unavailableGroups.contains(group)) {
                    unavailableGroups.add(group);
                }
                unavailableNodes.add(node);
            }
        }
        // Make sure there is available gruops
        if (unavailableGroups.size() > 0 && unavailableGroups.size() < groups.size()) {
            for (Group group : unavailableGroups) {
                trafficRerouting(group.getGroupName(), "not available and traffic are rerouted to other groups.");
            }
        }

        if (unavailableGroups.size() > 0) {
            reGroup(unavailableNodes, unavailableGroups);
            report();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }

        if (randomInt == 0) {
            Chaos.updateOrFaultFix(updateDomains, domainIndex, NodeState.UPDATING);
        } else {
            Chaos.updateOrFaultFix(faultDomains, domainIndex, NodeState.DOWN);
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    private void trafficRerouting(String groupName, String stateMessage) {
        System.out.println(String.format("Group %s is %s", groupName, stateMessage));
    }

    private void reGroup(List<Node> unavailableNodes, List<Group> unavailableGroups) {
        System.out.println("===========Re-group started.===========");
        useBackupPool(unavailableNodes, unavailableGroups);

        if (unavailableGroups.size() > 1) {
            shuffle(unavailableNodes, unavailableGroups);
        } else if (unavailableGroups.size() == 1) {
            System.out.println("===========Not enough nodes for shuffling.===========");
        } else {
            System.out.println("===========No unavailable groups.===========");
        }
    }

    private void shuffle(List<Node> unavailableNodes, List<Group> unavailableGroups) {
        List<Node> allNodesToGroup = new ArrayList<>();
        for (Group group : unavailableGroups) {
            if (group != null) {
                List<Node> temp = group.getHealthyNodes();
                allNodesToGroup.addAll(temp);
            }
        }

        for (Group group : unavailableGroups) {
            if (group != null) {
                List<Node> temp = group.getUnhealthyNodes();
                allNodesToGroup.addAll(temp);
            }
        }

        if (allNodesToGroup.size() < 3) {
            System.out.println("===========Not enough healthy nodes for shuffling and wait for auto recover.===========");
            return;
        }
        System.out.println("===========Fix cluster by shuffle and re-group nodes.===========");

        List<List<Node>> shuffledNodes = new ArrayList<>();
        allNodesToGroup.addAll(unavailableNodes);
        for (int i = 0; i < allNodesToGroup.size(); i += GROUP_SIZE) {
            int endIndex = Math.min(i + GROUP_SIZE, allNodesToGroup.size());

            List<Node> groupNodes = allNodesToGroup.subList(i, endIndex);
            shuffledNodes.add(groupNodes);
        }

        List<Group> fixedGroups = new ArrayList<>();
        for (Group group : unavailableGroups) {
            if (shuffledNodes.size() > 0) {
                List<Node> newNodes = shuffledNodes.get(0);
                group.setNodes(new ArrayList<>(newNodes));
                fixedGroups.add(group);
                for (Node node : newNodes) {
                    nodeGroupMap.put(node, group);
                }
                shuffledNodes.remove(0);
            }
        }

        for (Group group : fixedGroups) {
            if (group.getHealthyNodes().size() == GROUP_SIZE) {
                unavailableGroups.remove(group);
                trafficRerouting(group.getGroupName(), " is fixed and able to accept traffics.");
            }
        }
    }

    private void useBackupPool(List<Node> unavailableNodes, List<Group> unavailableGroups) {
        if (backupPool.size() == 0) {
            System.out.println("===========Backup pool doesn't have any nodes.===========");
        }

        System.out.println("===========Fix cluster by using nodes from backup pool.===========");

        List<Node> newBackupPool = new ArrayList<>();
        for (Node node : unavailableNodes) {
            if (backupPool.size() > 0) {
                Node nodeToFill = backupPool.get(0);
                if (nodeToFill.getNodeState() == NodeState.RUNNING) {
                    Group currentGroup = nodeGroupMap.get(node);
                    currentGroup.removeNode(node);
                    backupPool.remove(0);
                    currentGroup.addNode(nodeToFill);
                    nodeToFill.setInAGroup(true);
                    nodeGroupMap.put(nodeToFill, currentGroup);

                    node.setInAGroup(false);
                    newBackupPool.add(node);
                    nodeGroupMap.remove(node);
                    if (currentGroup.getHealthyNodes().size() == GROUP_SIZE) {
                        unavailableGroups.remove(currentGroup);
                        trafficRerouting(currentGroup.getGroupName(), " is fixed and able to accept traffics.");
                    }
                }
            }
        }
        if (newBackupPool.size() > 0) {
            backupPool = newBackupPool;
        }
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
