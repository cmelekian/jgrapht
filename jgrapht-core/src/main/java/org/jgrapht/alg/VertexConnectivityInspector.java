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
    private SimpleDirectedWeightedGraph<UUID, DefaultWeightedEdge> auxGraph;
    private Map<V, UUID> mapIn;
    private Map<V, UUID> mapOut;
    private Map<Pair<V,V>, Set<V>> minimumSeparators;
    private PushRelabelMFImpl<UUID, DefaultWeightedEdge> flowFinder;

    public VertexConnectivityInspector(Graph<V, E> graph)
    {
        this.baseGraph = Objects.requireNonNull(graph);
        this.mapIn = new HashMap<>();
        this.mapOut = new HashMap<>();
        this.auxGraph = null;
        this.flowFinder = null;
        this.minimumSeparators = new LinkedHashMap<>();
    }

    private SimpleDirectedWeightedGraph<UUID, DefaultWeightedEdge> createSplitDigraph(Graph<V, E> graph)
    {
        SimpleDirectedWeightedGraph<UUID, DefaultWeightedEdge> result =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (V v : graph.vertexSet()) {
            this.mapIn.put(v, UUID.randomUUID());
            result.addVertex(this.mapIn.get(v));
            this.mapOut.put(v, UUID.randomUUID());
            result.addVertex(this.mapOut.get(v));
            result.setEdgeWeight(result.addEdge(this.mapIn.get(v), this.mapOut.get(v)), 1);;

        }      
        for (E e : graph.edgeSet()) {
            result.setEdgeWeight(result.addEdge(this.mapOut.get(graph.getEdgeSource(e)), this.mapIn.get(graph.getEdgeTarget(e))), 2);
            if (!graph.getType().isDirected()) {
                result.setEdgeWeight(result.addEdge(this.mapOut.get(graph.getEdgeTarget(e)), this.mapIn.get(graph.getEdgeSource(e))), 2);
            }
        }
        return result;
    }
    
    public Set<V> getMinimumSeparator(V source, V target) {
        if (this.auxGraph == null) {
            this.auxGraph = createSplitDigraph(this.baseGraph);
            this.flowFinder = new PushRelabelMFImpl<UUID, DefaultWeightedEdge>(this.auxGraph);
        }
        Pair<V, V> cutPair = new Pair<>(source, target);
        if (this.minimumSeparators.get(cutPair) != null) {
            return this.minimumSeparators.get(cutPair);
        }
        Set<V> result = new LinkedHashSet<V>();
        this.flowFinder.calculateMinCut(this.mapOut.get(source), this.mapIn.get(target)); 
        Set<UUID> cut = flowFinder.getSourcePartition();
        for (V v : this.baseGraph.vertexSet()) {
            if (cut.contains(this.mapIn.get(v)) && !(cut.contains(this.mapOut.get(v)))) {
                result.add(v);
            }
        }
        this.minimumSeparators.put(cutPair, result);
        return result;
    }
}
