package org.jgrapht.alg;

import java.util.*;
import org.jgrapht.*;
import org.jgrapht.graph.*;
import org.jgrapht.alg.flow.*;
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.alg.interfaces.MaximumFlowAlgorithm.MaximumFlow;
import org.jgrapht.alg.util.*;


public class VertexConnectivityInspector<V, E>
{

    private Graph<V, E> baseGraph;
    private int numVertices;
    private boolean isDirected;
    private SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> splitGraph;
    private EdgeReversedGraph<Integer, DefaultWeightedEdge> reverseSplitGraph;
    private Map<V, Integer> vertexMap;
    private int vertexConnectivity;
    private Map<Pair<V,V>, Set<V>> minimumSeparators;
    private PushRelabelMFImpl<Integer, DefaultWeightedEdge> flowFinder;
    private PushRelabelMFImpl<Integer, DefaultWeightedEdge> reverseFlowFinder;

    public VertexConnectivityInspector(Graph<V, E> graph)
    {

        this.baseGraph = Objects.requireNonNull(graph);
        if (graph.vertexSet().size() < 2) {
            throw new IllegalArgumentException("Graph has fewer than 2 vertices");
        }
        this.numVertices = graph.vertexSet().size();
        this.isDirected = graph.getType().isDirected();
        this.vertexMap = new HashMap<>();
        this.flowFinder = null;
        this.reverseFlowFinder = null;
        this.minimumSeparators = new LinkedHashMap<>();
        this.vertexConnectivity = -1;
        
        
        this.splitGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Integer index = 0;
        //Add split digraph vertices - negative vertex has all incoming edges from the original graph, positive vertex has outgoing edges
        for (V v : graph.vertexSet()) {
            index++;
            this.vertexMap.put(v, index);
            this.splitGraph.addVertex(-1*index);
            this.splitGraph.addVertex(index);
            this.splitGraph.setEdgeWeight(this.splitGraph.addEdge(-1*index, index), 1);
        }   
        
        for (E e : graph.edgeSet()) {
            this.splitGraph.setEdgeWeight(this.splitGraph.addEdge(this.vertexMap.get(graph.getEdgeSource(e)), -1*this.vertexMap.get(graph.getEdgeTarget(e))), this.numVertices+1);
            if (!this.isDirected) {
                this.splitGraph.setEdgeWeight(this.splitGraph.addEdge(this.vertexMap.get(graph.getEdgeTarget(e)), -1*this.vertexMap.get(graph.getEdgeSource(e))), this.numVertices+1);
            }
        }
        this.reverseSplitGraph = new EdgeReversedGraph<>(this.splitGraph);
        
        
        
        
    }
    
    public Set<V> getMinimumSeparator(V source, V target) {
        if (this.baseGraph.containsEdge(source, target)) {
            throw new IllegalArgumentException("The given vertices are adjacent");
        }
        if (this.flowFinder == null) {
            this.flowFinder = new PushRelabelMFImpl<Integer, DefaultWeightedEdge>(this.splitGraph);
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
    
    public boolean isKConnected() {
        return false;
    }
    
    
    
    public int getVertexConnectivity()
    {
        if (this.vertexConnectivity > -1) {
            return this.vertexConnectivity;
        }
        if (this.flowFinder == null) {
            this.flowFinder = new PushRelabelMFImpl<Integer, DefaultWeightedEdge>(this.splitGraph);
        }
        if (this.reverseFlowFinder == null) {
            this.reverseFlowFinder = new PushRelabelMFImpl<Integer, DefaultWeightedEdge>(this.reverseSplitGraph);
        }
        System.out.println(this.splitGraph.edgeSet());
        Integer index = 0;
        int connectivity = this.numVertices - 1;
        int currentConnectivity;
        int currentReverseConnectivity;
        List<Integer> successors;
        List<Integer> predecessors;
        while (index <= connectivity) {
            index++;
            currentConnectivity = this.numVertices - 1;
            currentReverseConnectivity = this.numVertices - 1;
            successors = Graphs.successorListOf(this.splitGraph, -1*index);
            predecessors = Graphs.predecessorListOf(this.splitGraph, -1*index);
            
            System.out.println("Index = " + index);
            System.out.println(successors);
            System.out.println(predecessors);
            for (Integer j = 1; j <= this.numVertices; j++) {
                if (!(successors.contains(-1*j) || j == index)) {
                    System.out.println();
                    currentConnectivity = Math.min(currentConnectivity, (int)this.flowFinder.calculateMaximumFlow(index, -1*j)); 
                    System.out.println(currentConnectivity);
                }
            }
            if (this.isDirected) {
                
                for (Integer j = 1; j <= this.numVertices; j++) {
                    if (!(predecessors.contains(j) || j == index)) {
                        currentReverseConnectivity = Math.min(currentReverseConnectivity, (int)this.reverseFlowFinder.calculateMaximumFlow(-1*index, j));    
                    } 
                }
            }
            connectivity = Math.min(connectivity, Math.min(currentConnectivity, currentReverseConnectivity));    
        
        }
            

        
        return connectivity;
        
    
    }
    
  
}
