/*
 * Copyright 2010 DELVING BV
 *
 *  Licensed under the EUPL, Version 1.0 or? as soon they
 *  will be approved by the European Commission - subsequent
 *  versions of the EUPL (the "Licence");
 *  you may not use this work except in compliance with the
 *  Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  http://ec.europa.eu/idabc/eupl
 *
 *  Unless required by applicable law or agreed to in
 *  writing, software distributed under the Licence is
 *  distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied.
 *  See the Licence for the specific language governing
 *  permissions and limitations under the Licence.
 */

package eu.delving.metadata;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A tree representing the statistics gathered
 *
 * @author Gerald de Jong, Delving BV, <geralddejong@gmail.com>
 */

public class AnalysisTree implements Serializable {
    private static final long serialVersionUID = -15171971879119571L;
    private AnalysisTreeNode root;

    public interface Node extends TreeNode, Comparable<Node> {
        FieldStatistics getStatistics();

        TreePath getTreePath();

        Tag getTag();

        Path getPath();

        boolean setRecordRoot(Path recordRoot);

        boolean setUniqueElement(Path uniqueElement);

        boolean isRecordRoot();

        boolean isUniqueElement();

        Iterable<? extends Node> getChildNodes();

        boolean couldBeRecordRoot();

        String getVariableName();
    }

    public static void setRecordRoot(DefaultTreeModel model, Path recordRoot) {
        AnalysisTree.Node node = (AnalysisTree.Node) model.getRoot();
        List<AnalysisTree.Node> changedNodes = new ArrayList<AnalysisTree.Node>();
        setRecordRoot(node, recordRoot, changedNodes);
        for (AnalysisTree.Node changedNode : changedNodes) {
            model.nodeChanged(changedNode);
        }
    }

    public static void setUniqueElement(DefaultTreeModel model, Path uniqueElement) {
        AnalysisTree.Node node = (AnalysisTree.Node) model.getRoot();
        List<AnalysisTree.Node> changedNodes = new ArrayList<AnalysisTree.Node>();
        setUniqueElement(node, uniqueElement, changedNodes);
        for (AnalysisTree.Node changedNode : changedNodes) {
            model.nodeChanged(changedNode);
        }
    }

    public static AnalysisTree create(String rootTag) {
        return new AnalysisTree(new AnalysisTreeNode(Tag.create(rootTag)));
    }

    public static AnalysisTree create(List<FieldStatistics> fieldStatisticsList) {
        AnalysisTreeNode root = createSubtree(fieldStatisticsList, new Path(), null);
        if (root == null) {
            root = new AnalysisTreeNode(Tag.create("No statistics"));
        }
        return new AnalysisTree(root);
    }

    public Node getRoot() {
        return root;
    }

    public void getVariables(List<Node> variables) {
        getVariables(root, false, variables);
    }

    // ==== privates

    private AnalysisTree(AnalysisTreeNode root) {
        this.root = root;
    }

    private static void setRecordRoot(AnalysisTree.Node node, Path recordRoot, List<Node> changedNodes) {
        if (node.setRecordRoot(recordRoot)) {
            changedNodes.add(node);
        }
        for (AnalysisTree.Node child : node.getChildNodes()) {
            setRecordRoot(child, recordRoot, changedNodes);
        }
    }

    private static void setUniqueElement(AnalysisTree.Node node, Path uniqueElement, List<Node> changedNodes) {
        if (node.setUniqueElement(uniqueElement)) {
            changedNodes.add(node);
        }
        if (uniqueElement == null || !node.isUniqueElement()) {
            for (AnalysisTree.Node child : node.getChildNodes()) {
                setUniqueElement(child, uniqueElement, changedNodes);
            }
        }
    }

    private static void getVariables(AnalysisTreeNode node, boolean withinRecord, List<Node> variables) {
        if (node.isLeaf()) {
            if (withinRecord) {
                variables.add(node);
            }
        }
        else {
            for (AnalysisTreeNode child : node.getChildren()) {
                getVariables(child, withinRecord || node.isRecordRoot(), variables);
            }
        }
    }

    private static AnalysisTreeNode createSubtree(List<FieldStatistics> fieldStatisticsList, Path path, AnalysisTreeNode parent) {
        Map<Tag, List<FieldStatistics>> statisticsMap = new HashMap<Tag, List<FieldStatistics>>();
        for (FieldStatistics fieldStatistics : fieldStatisticsList) {
            Path subPath = new Path(fieldStatistics.getPath(), path.size());
            if (subPath.equals(path) && fieldStatistics.getPath().size() == path.size() + 1) {
                Tag tag = fieldStatistics.getPath().getTag(path.size());
                if (tag != null) {
                    List<FieldStatistics> list = statisticsMap.get(tag);
                    if (list == null) {
                        statisticsMap.put(tag, list = new ArrayList<FieldStatistics>());
                    }
                    list.add(fieldStatistics);
                }
            }
        }
        if (statisticsMap.isEmpty()) {
            return null;
        }
        Tag tag = path.peek();
        AnalysisTreeNode node = tag == null ? null : new AnalysisTreeNode(parent, tag);
        for (Map.Entry<Tag, List<FieldStatistics>> entry : statisticsMap.entrySet()) {
            Path childPath = new Path(path);
            childPath.push(entry.getKey());
            FieldStatistics fieldStatisticsForChild = null;
            for (FieldStatistics fieldStatistics : entry.getValue()) {
                if (fieldStatistics.getPath().equals(childPath)) {
                    fieldStatisticsForChild = fieldStatistics;
                }
            }
            AnalysisTreeNode child = createSubtree(fieldStatisticsList, childPath, node);
            if (child != null) {
                if (node == null) {
                    node = child;
                }
                else {
                    node.add(child);
                }
                child.setStatistics(fieldStatisticsForChild);
            }
            else if (fieldStatisticsForChild != null) {
                if (node == null) {
                    node = new AnalysisTreeNode(node, fieldStatisticsForChild);
                }
                else {
                    node.add(new AnalysisTreeNode(node, fieldStatisticsForChild));
                }
            }
        }
        return node;
    }

}
