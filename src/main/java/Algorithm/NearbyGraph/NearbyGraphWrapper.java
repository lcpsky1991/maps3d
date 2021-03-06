package Algorithm.NearbyGraph;

import Isolines.IIsoline;
import Utils.Constants;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.KruskalMinimumSpanningTree;
import org.jgrapht.graph.AsUnweightedGraph;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;

import java.util.*;

/**
 * Created by Artyom.Fomenko on 26.07.2016.
 */
public class NearbyGraphWrapper {

    private SimpleWeightedGraph<Isoline_attributed.LineSide,NearbyEstimator.EdgeAttributed> graph;
    private Graph<Isoline_attributed.LineSide,NearbyEstimator.EdgeAttributed> graph_unweighted;
    private Set<Isoline_attributed> isolines;
    public NearbyGraphWrapper(SimpleWeightedGraph<Isoline_attributed.LineSide,NearbyEstimator.EdgeAttributed> graph) {
        setGraph(graph);
    }

    /**
     * Set empty closed isoline rings slope height, since almost all empty closed isoline rings are hills
     */
    public void SetHillsSlopeSides() {
        LinkedList<Isoline_attributed.LineSide> sides = new LinkedList<>();
        graph.vertexSet().forEach(sides::add);

        GeometryFactory gf = new GeometryFactory();

        for (Isoline_attributed.LineSide side : sides) {
            List<Isoline_attributed.LineSide> neighbours = Graphs.neighborListOf(graph,side);
            // If side have only one neighbour, this neighbour is other side of line. So, we found inside side of empty cirlce.
            if (neighbours.size() == 1 &&
                    side.getIsoline().getIsoline().isClosed() &&
                    side.getIsoline().getIsoline().getSlopeSide() == 0) {
                LinearRing ring = gf.createLinearRing(side.getIsoline().getIsoline().getLineString().getCoordinateSequence());
                Polygon poly = gf.createPolygon(ring);
                double area = poly.getArea();
                if (area < Constants.NEARBY_HILL_THRESHOLD_AREA) {
                    int SlopeSide = side.isPositive() ? -1 : 1;
                    side.getIsoline().getIsoline().setSlopeSide(SlopeSide);
                }
            }
        }
    }

    public void ConvertToSpanningTree() {

        System.out.println("Creating spanning tree...");

        SimpleWeightedGraph<Isoline_attributed.LineSide,NearbyEstimator.EdgeAttributed> new_graph =
                new SimpleWeightedGraph<>(NearbyEstimator.EdgeAttributed.class);
        KruskalMinimumSpanningTree<Isoline_attributed.LineSide,NearbyEstimator.EdgeAttributed> spanningTree =
                new KruskalMinimumSpanningTree<>(graph);

        for (Isoline_attributed.LineSide side : graph.vertexSet()) {
            new_graph.addVertex(side);
        }

        for (NearbyEstimator.EdgeAttributed edge : spanningTree.getMinimumSpanningTreeEdgeSet()) {
            NearbyEstimator.EdgeAttributed new_edge = new_graph.addEdge(graph.getEdgeSource(edge),graph.getEdgeTarget(edge));
            new_graph.setEdgeWeight(new_edge, graph.getEdgeWeight(edge));
        }
        setGraph(new_graph);
    }

    private void setGraph(SimpleWeightedGraph<Isoline_attributed.LineSide,NearbyEstimator.EdgeAttributed> graph) {
        this.graph = graph;
        this.graph_unweighted = new AsUnweightedGraph<>(this.graph);
        isolines = new HashSet<>();
        for (Isoline_attributed.LineSide side : this.graph.vertexSet()) {
            isolines.add(side.getIsoline());
        }

    }

    public SimpleWeightedGraph<Isoline_attributed.LineSide,NearbyEstimator.EdgeAttributed> getGraph() {
        return graph;
    }

    private static Isoline_attributed.LineSide propagateSlope(Isoline_attributed.LineSide side1, Isoline_attributed.LineSide side2, boolean inverted) {
        IIsoline isoline1 = side1.getIsoline().getIsoline();
        IIsoline isoline2 = side2.getIsoline().getIsoline();
        if (isoline1 != isoline2) {

            if (isoline1.getSlopeSide() != 0 && isoline2.getSlopeSide() == 0) {
                if (inverted) {
                    isoline2.setSlopeSide(-1 * isoline1.getSlopeSide());
                } else {
                    isoline2.setSlopeSide(isoline1.getSlopeSide());
                }
                return side2;
            }
            if (isoline1.getSlopeSide() == 0 && isoline2.getSlopeSide() != 0) {
                if (inverted) {
                    isoline1.setSlopeSide(-1 * isoline2.getSlopeSide());
                } else {
                    isoline1.setSlopeSide(isoline2.getSlopeSide());
                }
                return side1;
            }

        }
        return null;
    }

    /**
     * Recover missing slope sides, this should be done before trying to recover heights
     */
    public void recoverAllSlopes() {

        System.out.println("Recovering slopes...");

        LinkedList<NearbyEstimator.EdgeAttributed> edges = new LinkedList<>();
        graph.edgeSet().forEach(edges::add);

        edges.sort( (lhs,rhs)-> Double.compare(graph.getEdgeWeight(lhs),graph.getEdgeWeight(rhs)) );
        Iterator<NearbyEstimator.EdgeAttributed> it = edges.iterator();

        while (it.hasNext()) {
            NearbyEstimator.EdgeAttributed edge = it.next();
            double current_weight = graph.getEdgeWeight(edge);
            Isoline_attributed.LineSide side1 = graph.getEdgeSource(edge);
            Isoline_attributed.LineSide side2 = graph.getEdgeTarget(edge);
            boolean inverted = side1.isPositive() == side2.isPositive();
            Isoline_attributed.LineSide propSide;
            if ( (propSide = propagateSlope(side1,side2,inverted)) != null) {
                it = edges.iterator();
                // TODO: optimize
            }
        }
    }

    /**
     * Recover heights of isolines, slope side of each isoline should be known. Also, it's recommended
     * to perform a line-connection pre-processing.
     */
    public void recoverAllHeights() {

        System.out.print("Recovering heights...");
        BreadthFirstHeightRoceveryIterator it = new BreadthFirstHeightRoceveryIterator(getGraph(),isolines.iterator().next());
        while (it.hasNext()) {
            it.next();
        }
        System.out.println("success.");
    }

    public static class BreadthFirstHeightRoceveryIterator extends BreadthFirstIterator<Isoline_attributed.LineSide,NearbyEstimator.EdgeAttributed> {

        Graph<Isoline_attributed.LineSide, NearbyEstimator.EdgeAttributed> g;
        public BreadthFirstHeightRoceveryIterator(Graph<Isoline_attributed.LineSide, NearbyEstimator.EdgeAttributed> g, Isoline_attributed startLine ) {
            super(g, startLine.getSidePositive());
            this.g = g;
            super.encounterVertex(startLine.getSideNegative(),g.getEdge(startLine.getSidePositive(),startLine.getSideNegative()));
            startLine.getIsoline().setHeight(0);
            startLine.height_recovered = true;
        }

        @Override
        protected void encounterVertex(Isoline_attributed.LineSide target, NearbyEstimator.EdgeAttributed edge) {
            super.encounterVertex(target, edge);
            if (target.getIsoline().height_recovered || edge == null) return;
            Isoline_attributed.LineSide pivot = Graphs.getOppositeVertex(getGraph(),edge,target);
            if (!pivot.getIsoline().height_recovered) {
                throw new RuntimeException("Encountering vertex from vertex with unrecovered height");
            }
            double height_delta = 1;
            if (pivot.getIsoline().getIsoline().isHalf() || target.getIsoline().getIsoline().isHalf()) {
                height_delta = 0.5;
            }
            if (pivot.getIsoline().getIsoline().getSlopeSide() == 0) {
                throw new RuntimeException("Slope side undetermined!");
                //pivot.getIsoline().getIsoline().setHeight(-100000);
            }
            int mul1 = pivot.getSign() == pivot.getIsoline().getIsoline().getSlopeSide() ? -1 : 1;
            int mul2 = target.getSign() == target.getIsoline().getIsoline().getSlopeSide() ? -1 : 1;
            if (mul1 == -mul2) {
                height_delta = height_delta*mul1;
            } else {
                height_delta = 0;
            }

            target.getIsoline().getIsoline().setHeight(  pivot.getIsoline().getIsoline().getHeight()+height_delta );
            target.getIsoline().height_recovered = true;
        }
    }


}
