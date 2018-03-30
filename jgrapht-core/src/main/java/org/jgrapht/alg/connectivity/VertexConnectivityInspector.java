/*
 * (C) Copyright 2018-2018, by Chris Melekian and Contributors.
 *
 * JGraphT : a free Java graph-theory library
 *
 * This program and the accompanying materials are dual-licensed under
 * either
 *
 * (a) the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation, or (at your option) any
 * later version.
 *
 * or (per the licensee's choosing)
 *
 * (b) the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation.
 */
package org.jgrapht.alg.connectivity;

import java.util.*;
import org.jgrapht.*;
import org.jgrapht.graph.*;
import org.jgrapht.alg.flow.*;
import org.jgrapht.alg.util.*;

/**
 * 
 * 
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 * 
 * @author Chris Melekian
 */
public class VertexConnectivityInspector<V, E>
{

    private Graph<V, E> baseGraph;
    private int numVertices;
    private boolean isDirected;
    private SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> splitGraph;
    private Map<V, Integer> vertexMap;
    private int vertexConnectivity;
    private Map<Pair<V,V>, Set<V>> minimumSeparators;
    private PushRelabelMFImpl<Integer, DefaultWeightedEdge> flowFinder = null;
    private PushRelabelMFImpl<Integer, DefaultWeightedEdge> reverseFlowFinder = null;

    public VertexConnectivityInspector(Graph<V, E> graph)
    {

        this.baseGraph = Objects.requireNonNull(graph);
        if (graph.vertexSet().size() < 2) {
            throw new IllegalArgumentException("Graph has fewer than 2 vertices");
        }
        this.numVertices = graph.vertexSet().size();
        this.isDirected = graph.getType().isDirected();
        this.vertexMap = new HashMap<>();
        this.minimumSeparators = new LinkedHashMap<>();
        this.vertexConnectivity = -1;
        
        
        /*
         * Construct the split digraph of the given graph. For each vertex v of the original graph, the split digraph contains two vertices
         * v* and v', which are joined by a directed edge v*v' of weight 1. For each edge uv in the original graph, the split digraph contains
         * the edge u'v*.
         */
        //Add split digraph vertices - negative vertex has all incoming edges from the original graph, positive vertex has outgoing edges
        this.splitGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Integer index = 0;
        for (V v : graph.vertexSet()) {
            index++;
            this.vertexMap.put(v, index);
            this.splitGraph.addVertex(-1*index);
            this.splitGraph.addVertex(index);
            this.splitGraph.setEdgeWeight(this.splitGraph.addEdge(-1*index, index), 1);
        }   
        for (E e : graph.edgeSet()) {
            this.splitGraph.setEdgeWeight(this.splitGraph.addEdge(this.vertexMap.get(graph.getEdgeSource(e)), -1*this.vertexMap.get(graph.getEdgeTarget(e))), 2);
            if (!this.isDirected) {
                this.splitGraph.setEdgeWeight(this.splitGraph.addEdge(this.vertexMap.get(graph.getEdgeTarget(e)), -1*this.vertexMap.get(graph.getEdgeSource(e))), 2);
            }
        }
        
        this.flowFinder = new PushRelabelMFImpl<Integer, DefaultWeightedEdge>(this.splitGraph);
        if (this.isDirected) {
            this.reverseFlowFinder = new PushRelabelMFImpl<Integer, DefaultWeightedEdge>(new EdgeReversedGraph<Integer, DefaultWeightedEdge>(this.splitGraph));
        }  
    }
    
    /**
     * Returns a minimum-cardinality (s,t)-separator, a set of vertices that any s-t path must pass through. 
     * In an undirected graph, an (s,t)-separator is also a (t,s)-separator.
     * 
     * @param source
     * @param target
     * @return a minimum (source,target)-separator
     */
    public Set<V> getMinimumSeparator(V source, V target) {
        if (this.baseGraph.containsEdge(source, target)) {
            throw new IllegalArgumentException("The given vertices are adjacent");
        }
        Pair<V, V> cutPair = new Pair<>(source, target);
        if (this.minimumSeparators.get(cutPair) != null) {
            return this.minimumSeparators.get(cutPair);
        }
        Set<V> result = new LinkedHashSet<V>();
        this.flowFinder.calculateMinCut(this.vertexMap.get(source), -1*this.vertexMap.get(target)); 
        Set<Integer> cut = this.flowFinder.getSourcePartition();
        for (V v : this.baseGraph.vertexSet()) {
            if (cut.contains(-1*this.vertexMap.get(v)) && !(cut.contains(this.vertexMap.get(v)))) {
                result.add(v);
            }
        }
        this.minimumSeparators.put(cutPair, result);
        return result;
    }
    /**
     * Tests if the graph is k-connected, using the Even-Tarjan algorithm. A k-connected graph has at least k+1 vertices
     * and has no vertex separators with fewer than k vertices.
     * 
     * @param k the desired connectivity
     * @return true if the graph is k-connected, false otherwise
     */
    public boolean isKConnected(int k) 
    {
        if (this.vertexConnectivity > -1) {
            if (this.vertexConnectivity >= k) {
                return true;
            } else {
                return false;
            }
        } else {
            Integer index = 0;
            int connectivity = this.numVertices - 1;
            while (index <= k) {
                index++;   
                connectivity = Math.min(connectivity, singleVertexConnectivity(index));    
            }
            if (connectivity >= k) {
                return true;
            } else {
                return false;
            }
        }
    }
    
    /**
     * Returns the connectivity κ(G) of the graph, using the Even-Tarjan algorithm. The connectivity is the largest integer k 
     * such that the graph is k-connected.
     * @return the connectivity of the graph
     */
    public int getConnectivity()
    {
        if (this.vertexConnectivity > -1) {
            return this.vertexConnectivity;
        }
        Integer index = 0;
        int connectivity = this.numVertices - 1;
        while (index <= connectivity) {
            index++;   
            connectivity = Math.min(connectivity, singleVertexConnectivity(index));    
        
        }
        return connectivity;
    }
    /**
     * Returns the connectivity κ(i) of a given vertex i, defined as the minimum of κ(i,j) over all vertices j which are not adjacent to i.
     * 
     * @param i vertex
     * @return the connectivity of i
     */
    private int singleVertexConnectivity(Integer i) {
        int currentConnectivity = this.numVertices - 1;
        int currentReverseConnectivity = this.numVertices - 1;
        for (Integer j = 1; j <= this.numVertices; j++) {
            if (!(this.splitGraph.containsEdge(i, -1*j) || j == i)) {
                currentConnectivity = Math.min(currentConnectivity, (int)this.flowFinder.calculateMaximumFlow(i, -1*j)); 
            }
        }
        if (this.isDirected) {  
            for (Integer j = 1; j <= this.numVertices; j++) {
                if (!(this.splitGraph.containsEdge(j, -1*i) || j == i)) {
                    currentReverseConnectivity = Math.min(currentReverseConnectivity, (int)this.reverseFlowFinder.calculateMaximumFlow(-1*i, j));    
                } 
            }
        }
        return Math.min(currentConnectivity, currentReverseConnectivity);
    }
  
}
