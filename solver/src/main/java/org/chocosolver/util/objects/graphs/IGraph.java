/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2025, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */
package org.chocosolver.util.objects.graphs;

import org.chocosolver.util.objects.setDataStructures.ISet;
import org.chocosolver.util.objects.setDataStructures.SetType;

/**
 * @author Jean-Guillaume Fages, Xavier Lorca
 *         <p/>
 *         Provide an interface for the graph manipulation
 *
 * --- GRAPH API REFACTORING 04/03/2021 ---
 *
 * - The semantic distinction between arcs and edges has been removed for more clarity. If the graph is undirected,
 *      edges are undirected, if the graph is directed, the graph is directed. Methods related to edges are
 *      `addEdge`, `removeEdge`, and `containsEdge`.
 *
 * - The object model is such that the more abstract interface specifies directed graph accessors on edges,
 *      `getSuccessorsOf` and `getPredecessorsOf`. When the graph is undirected, the method `getNeighborsOf` is
 *      available. Note that an undirected graph is equivalent to a directed graph with couples of opposite
 *      directed edges. Thus, the neighbors of a node in an undirected graph are both successors and predecessors,
 *      and this is why these methods are equivalent to getNeighbors in the case of an undirected graph. To encourage
 *      unambiguous use and facilitate code reading, the successors and predecessors related method have been defined
 *      as deprecated in explicit uses of UndirectedGraphs.
 *
 *  - The possibility to chose (in constructors) and get the set data structure for nodes has also been added,
 *      as it was only implemented for neighbors: `getNodeSetType` and `getEdgeSetType`. The previous default behaviour
 *      has been conserved with default constructors.
 *
 */
public interface IGraph  {


    /**
     * @return the collection of nodes present in the graph
     */
    ISet getNodes();

    /**
     * Adds node x to the node set of the graph
     *
     * @param x a node index
     * @return true iff x was not already present in the graph
     */
    boolean addNode(int x);

    /**
     * Remove node x from the graph
     *
     * @param x a node index
     * @return true iff x was present in the graph
     */
    boolean removeNode(int x);

    /**
     * Add edge (x,y) to the graph
     *
     * @param x a node index
     * @param y a node index
     * @return true iff (x,y) was not already in the graph
     */
    boolean addEdge(int x, int y);

    /**
     * Remove edge (x,y) from the graph
     *
     * @param x a node index
     * @param y a node index
     * @return true iff (x,y) was in the graph
     */
    boolean removeEdge(int x, int y);

    /**
     * The maximum number of nodes in the graph
	 * Vertices of the graph belong to [0,getNbMaxNodes()-1]
	 * This quantity is fixed at the creation of the graph
     *
     * @return the maximum number of nodes of the graph
     */
    int getNbMaxNodes();

    /**
     * Get the type of data structures used in the graph to represent nodes
     *
     * @return the type of data structures used in the graph to represent nodes
     */
    SetType getNodeSetType();

    /**
     * Get the type of data structures used in the graph to represent edges
	 *
     * @return the type of data structures used in the graph to represent edges
     */
    SetType getEdgeSetType();

    /**
     * Get either x's successors or neighbors.
     * <p/>
     * This method enables to capitalize some code but should be called with care
     *
     * @param x a node index
     * @return x's successors if <code>this</code> is directed
     *         x's neighbors otherwise
     */
    ISet getSuccessorsOf(int x);

    /**
     * Get either x's predecessors or neighbors.
     * <p/>
     * This method enables to capitalize some code but should be called with care
     *
     * @param x a node index
     * @return x's predecessors if <code>this</code> is directed
     *         x's neighbors otherwise
     */
    ISet getPredecessorsOf(int x);

    /**
     * If <code>this </code> is directed
     * returns true if and only if directed edge (x,y) exists
     * Else, if <code>this</code> is undirected
     * returns true if and only if edge (x,y) exists
     * <p/>
     * This method enables to capitalize some code but should be called with care
     *
     * @param x a node index
     * @param y a node index
     */
    default boolean containsEdge(int x, int y) {
        return getSuccessorsOf(x).contains(y);
    }

    /**
     * @param x a node index
     * @return True iff the graph contains the node x
     */
    default boolean containsNode(int x) {
        return getNodes().contains(x);
    }

    /**
     * @return true if and only if <code>this</code> is a directed graph
     */
    boolean isDirected();

    //***********************************************************************************
    // GraphViz
    //***********************************************************************************

    /**
     * Export graph to graphviz format, see http://www.webgraphviz.com/
     *
     * @return a String encoding the graph to be displayed by graphViz
     */
    default String graphVizExport() {
        boolean directed = isDirected();
        String arc = directed ? " -> " : " -- ";
        StringBuilder sb = new StringBuilder();
        sb.append(directed ? "digraph " : "graph ").append("G" + "{\n");
        for (int i : getNodes()) sb.append(i + " ");
        sb.append(";\n");
        for (int i : getNodes()) {
            for (int j : getSuccessorsOf(i)) {
                if (directed || i < j) sb.append(i + arc + j + " ;\n");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
