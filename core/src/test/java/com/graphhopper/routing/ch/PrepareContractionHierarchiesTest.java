/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.ch;

import com.graphhopper.routing.Dijkstra;
import com.graphhopper.routing.DijkstraOneToMany;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.ch.PrepareContractionHierarchies.Shortcut;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.FastestCalc;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import java.util.Collection;
import java.util.Iterator;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Peter Karich
 */
public class PrepareContractionHierarchiesTest {

    CarFlagEncoder carEncoder = new CarFlagEncoder();

    LevelGraph createGraph() {
        return new GraphBuilder().levelGraphCreate();
    }

    LevelGraph createExampleGraph() {
        LevelGraph g = createGraph();

        //5-1-----2
        //   \ __/|
        //    0   |
        //   /    |
        //  4-----3
        //
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(0, 4, 3, true);
        g.edge(1, 2, 2, true);
        g.edge(2, 3, 1, true);
        g.edge(4, 3, 2, true);
        g.edge(5, 1, 2, true);
        return g;
    }

    @Test
    public void testShortestPathSkipNode() {
        LevelGraph g = createExampleGraph();
        double normalDist = new Dijkstra(g, carEncoder).calcPath(4, 2).distance();
        DijkstraOneToMany algo = new DijkstraOneToMany(g, carEncoder);
        algo.edgeFilter(new PrepareContractionHierarchies.LevelEdgeFilterCH(g).avoidNode(3));
        int nodeEntry = algo.limit(100).findEndNode(4, 2);
        assertTrue(algo.weight(nodeEntry) > normalDist);
    }

    @Test
    public void testShortestPathSkipNode2() {
        LevelGraph g = createExampleGraph();
        double normalDist = new Dijkstra(g, carEncoder).calcPath(4, 2).distance();
        DijkstraOneToMany algo = new DijkstraOneToMany(g, carEncoder);
        algo.edgeFilter(new PrepareContractionHierarchies.LevelEdgeFilterCH(g).avoidNode(3));
        int nodeEntry = algo.limit(10).findEndNode(4, 2);
        // assertEquals(ee.weight, normalDist, 1e-5);
        nodeEntry = algo.limit(10).findEndNode(4, 1);
        assertTrue(algo.weight(nodeEntry) > normalDist);
    }

    @Test
    public void testShortestPathLimit() {
        LevelGraph g = createExampleGraph();
        DijkstraOneToMany algo = new DijkstraOneToMany(g, carEncoder);
        algo.edgeFilter(new PrepareContractionHierarchies.LevelEdgeFilterCH(g).avoidNode(0));
        int endNode = algo.limit(2).findEndNode(4, 1);
        // did not reach endNode
        assertNotEquals(1, endNode);
    }

    @Test
    public void testAddShortcuts() {
        LevelGraph g = createExampleGraph();
        int old = g.getAllEdges().maxId();
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().graph(g);
        prepare.doWork();
        assertEquals(old, g.getAllEdges().maxId());
    }

    @Test
    public void testMoreComplexGraph() {
        LevelGraph g = initShortcutsGraph(createGraph());
        int old = g.getAllEdges().maxId();
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().graph(g);
        prepare.doWork();
        assertEquals(old + 8, g.getAllEdges().maxId());
    }

    @Test
    public void testDirectedGraph() {
        LevelGraph g = createGraph();
        g.edge(5, 4, 3, false);
        g.edge(4, 5, 10, false);
        g.edge(2, 4, 1, false);
        g.edge(5, 2, 1, false);
        g.edge(3, 5, 1, false);
        g.edge(4, 3, 1, false);
        int old = GHUtility.count(g.getAllEdges());
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().graph(g);
        prepare.doWork();
        // PrepareTowerNodesShortcutsTest.printEdges(g);
        assertEquals(old + 3, GHUtility.count(g.getAllEdges()));
        RoutingAlgorithm algo = prepare.createAlgo();
        Path p = algo.calcPath(4, 2);
        assertEquals(3, p.distance(), 1e-6);
        assertEquals(Helper.createTList(4, 3, 5, 2), p.calcNodes());
    }

    @Test
    public void testDirectedGraph2() {
        LevelGraph g = createGraph();
        initDirected2(g);
        int old = GHUtility.count(g.getAllEdges());
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().graph(g);
        prepare.doWork();
        // PrepareTowerNodesShortcutsTest.printEdges(g);
        assertEquals(old + 15, GHUtility.count(g.getAllEdges()));
        RoutingAlgorithm algo = prepare.createAlgo();

        Path p = algo.calcPath(0, 10);
        assertEquals(10, p.distance(), 1e-6);
        assertEquals(Helper.createTList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), p.calcNodes());
    }

    @Test
    public void testDirectedGraph3() {
        LevelGraph g = createGraph();
        g.edge(0, 2, 2, true);
        g.edge(10, 2, 2, true);
        g.edge(11, 2, 2, true);
        // create a longer one directional edge => no longish one-dir shortcut should be created        
        g.edge(2, 1, 2, true);
        g.edge(2, 1, 10, false);

        g.edge(1, 3, 2, true);
        g.edge(3, 4, 2, true);
        g.edge(3, 5, 2, true);
        g.edge(3, 6, 2, true);
        g.edge(3, 7, 2, true);

        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().graph(g);
        prepare.initFromGraph();
        // find all shortcuts if we contract node 1
        Collection<Shortcut> scs = prepare.testFindShortcuts(1);
        assertEquals(2, scs.size());
        Iterator<Shortcut> iter = scs.iterator();
        Shortcut sc1 = iter.next();
        Shortcut sc2 = iter.next();
        if (sc1.distance > sc2.distance) {
            Shortcut tmp = sc1;
            sc1 = sc2;
            sc2 = tmp;
        }

        assertTrue(sc1.toString(), sc1.from == 2 && sc1.to == 3);
        assertTrue(sc2.toString(), sc2.from == 2 && sc2.to == 3);

        assertEquals(sc1.toString(), 4, sc1.distance, 1e-4);
        assertEquals(sc2.toString(), 12, sc2.distance, 1e-4);
    }

    void initRoundaboutGraph(Graph g) {
        //              roundabout:
        //16-0-9-10--11   12<-13
        //    \       \  /      \
        //    17       \|        7-8-..
        // -15-1--2--3--4       /     /
        //     /         \-5->6/     /
        //  -14            \________/

        g.edge(16, 0, 1, true);
        g.edge(0, 9, 1, true);
        g.edge(0, 17, 1, true);
        g.edge(9, 10, 1, true);
        g.edge(10, 11, 1, true);
        g.edge(11, 28, 1, true);
        g.edge(28, 29, 1, true);
        g.edge(29, 30, 1, true);
        g.edge(30, 31, 1, true);
        g.edge(31, 4, 1, true);

        g.edge(17, 1, 1, true);
        g.edge(15, 1, 1, true);
        g.edge(14, 1, 1, true);
        g.edge(14, 18, 1, true);
        g.edge(18, 19, 1, true);
        g.edge(19, 20, 1, true);
        g.edge(20, 15, 1, true);
        g.edge(19, 21, 1, true);
        g.edge(21, 16, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);

        g.edge(4, 5, 1, false);
        g.edge(5, 6, 1, false);
        g.edge(6, 7, 1, false);
        g.edge(7, 13, 1, false);
        g.edge(13, 12, 1, false);
        g.edge(12, 4, 1, false);

        g.edge(7, 8, 1, true);
        g.edge(8, 22, 1, true);
        g.edge(22, 23, 1, true);
        g.edge(23, 24, 1, true);
        g.edge(24, 25, 1, true);
        g.edge(25, 27, 1, true);
        g.edge(27, 5, 1, true);
        g.edge(25, 26, 1, false);
        g.edge(26, 25, 1, false);
    }

    @Test
    public void testRoundaboutUnpacking() {
        LevelGraph g = createGraph();
        initRoundaboutGraph(g);
        int old = g.getAllEdges().maxId();
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().graph(g);
        prepare.doWork();
        assertEquals(old + 25, g.getAllEdges().maxId());
        RoutingAlgorithm algo = prepare.createAlgo();
        Path p = algo.calcPath(4, 7);
        assertEquals(Helper.createTList(4, 5, 6, 7), p.calcNodes());
    }

    @Test
    public void testFindShortcuts_Roundabout() {
        LevelGraphStorage g = (LevelGraphStorage) createGraph();
        EdgeSkipIterator iter1_1 = g.edge(1, 3, 1, true);
        EdgeSkipIterator iter1_2 = g.edge(3, 4, 1, true);
        EdgeSkipIterator iter2_1 = g.edge(4, 5, 1, false);
        EdgeSkipIterator iter2_2 = g.edge(5, 6, 1, false);
        EdgeSkipIterator iter3_1 = g.edge(6, 7, 1, true);
        EdgeSkipIterator iter3_2 = g.edge(6, 8, 2, false);
        g.edge(8, 4, 1, false);
        g.setLevel(3, 3);
        g.setLevel(5, 5);
        g.setLevel(7, 7);
        g.setLevel(8, 8);

        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().graph(g);
        g.edge(1, 4, 2, prepare.scBothDir()).skippedEdges(iter1_1.edge(), iter1_2.edge());
        int f = prepare.scOneDir();
        g.edge(4, 6, 2, f).skippedEdges(iter2_1.edge(), iter2_2.edge());
        g.edge(6, 4, 3, f).skippedEdges(iter3_1.edge(), iter3_2.edge());

        prepare.initFromGraph();
        // there should be two different shortcuts for both directions!
        Collection<Shortcut> sc = prepare.testFindShortcuts(4);
        assertEquals(2, sc.size());
    }

    void initUnpackingGraph(LevelGraphStorage g, WeightCalculation w) {
        double dist = 1;
        int flags = carEncoder.flags(30, false);
        g.edge(10, 0, w.getWeight(dist, flags), flags);
        EdgeSkipIterator iter1 = g.edge(0, 1, w.getWeight(dist, flags), flags);
        EdgeSkipIterator iter2 = g.edge(1, 2, w.getWeight(dist, flags), flags);
        EdgeSkipIterator iter3 = g.edge(2, 3, w.getWeight(dist, flags), flags);
        EdgeSkipIterator iter4 = g.edge(3, 4, w.getWeight(dist, flags), flags);
        EdgeSkipIterator iter5 = g.edge(4, 5, w.getWeight(dist, flags), flags);
        EdgeSkipIterator iter6 = g.edge(5, 6, w.getWeight(dist, flags), flags);
        int oneDirFlags = new PrepareContractionHierarchies().scOneDir();

        int tmp = iter1.edge();
        iter1 = g.edge(0, 2, 2, oneDirFlags);
        iter1.skippedEdges(tmp, iter2.edge());
        tmp = iter1.edge();
        iter1 = g.edge(0, 3, 3, oneDirFlags);
        iter1.skippedEdges(tmp, iter3.edge());
        tmp = iter1.edge();
        iter1 = g.edge(0, 4, 4, oneDirFlags);
        iter1.skippedEdges(tmp, iter4.edge());
        tmp = iter1.edge();
        iter1 = g.edge(0, 5, 5, oneDirFlags);
        iter1.skippedEdges(tmp, iter5.edge());
        tmp = iter1.edge();
        iter1 = g.edge(0, 6, 6, oneDirFlags);
        iter1.skippedEdges(tmp, iter6.edge());
        g.setLevel(0, 10);
        g.setLevel(6, 9);
        g.setLevel(5, 8);
        g.setLevel(4, 7);
        g.setLevel(3, 6);
        g.setLevel(2, 5);
        g.setLevel(1, 4);
        g.setLevel(10, 3);
    }

    @Test
    public void testUnpackingOrder() {
        LevelGraphStorage g = (LevelGraphStorage) createGraph();
        WeightCalculation calc = new ShortestCalc();
        initUnpackingGraph(g, calc);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().graph(g);
        RoutingAlgorithm algo = prepare.type(calc).vehicle(carEncoder).createAlgo();
        Path p = algo.calcPath(10, 6);
        assertEquals(7, p.distance(), 1e-5);
        assertEquals(Helper.createTList(10, 0, 1, 2, 3, 4, 5, 6), p.calcNodes());
    }

    @Test
    public void testUnpackingOrder_Fastest() {
        LevelGraphStorage g = (LevelGraphStorage) createGraph();
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().graph(g);
        WeightCalculation calc = new FastestCalc(carEncoder);
        initUnpackingGraph(g, calc);
        RoutingAlgorithm algo = prepare.type(calc).vehicle(carEncoder).createAlgo();
        Path p = algo.calcPath(10, 6);
        assertEquals(7, p.distance(), 1e-1);
        assertEquals(Helper.createTList(10, 0, 1, 2, 3, 4, 5, 6), p.calcNodes());
    }

    @Test
    public void testCircleBug() {
        LevelGraph g = createGraph();
        //  /--1
        // -0--/
        //  |
        g.edge(0, 1, 10, true);
        g.edge(0, 1, 4, true);
        g.edge(0, 2, 10, true);
        g.edge(0, 3, 10, true);
        PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().graph(g);
        prepare.doWork();
        assertEquals(0, prepare.shortcuts());
    }

    // 0-1-2-3-4
    // |     / |
    // |    8  |
    // \   /   /
    //  7-6-5-/
    void initBiGraph(Graph graph) {
        graph.edge(0, 1, 100, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 5, 25, true);
        graph.edge(5, 6, 25, true);
        graph.edge(6, 7, 5, true);
        graph.edge(7, 0, 5, true);
        graph.edge(3, 8, 20, true);
        graph.edge(8, 6, 20, true);
    }

    // 0-1-.....-9-10
    // |         ^   \
    // |         |    |
    // 17-16-...-11<-/
    public static void initDirected2(Graph g) {
        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(6, 7, 1, true);
        g.edge(7, 8, 1, true);
        g.edge(8, 9, 1, true);
        g.edge(9, 10, 1, true);
        g.edge(10, 11, 1, false);
        g.edge(11, 12, 1, true);
        g.edge(11, 9, 3, false);
        g.edge(12, 13, 1, true);
        g.edge(13, 14, 1, true);
        g.edge(14, 15, 1, true);
        g.edge(15, 16, 1, true);
        g.edge(16, 17, 1, true);
        g.edge(17, 0, 1, true);
    }

    //       8
    //       |
    //    6->0->1->3->7
    //    |        |
    //    |        v
    //10<-2---4<---5
    //    9
    public static void initDirected1(Graph g) {
        g.edge(0, 8, 1, true);
        g.edge(0, 1, 1, false);
        g.edge(1, 3, 1, false);
        g.edge(3, 7, 1, false);
        g.edge(3, 5, 1, false);
        g.edge(5, 4, 1, false);
        g.edge(4, 2, 1, true);
        g.edge(2, 9, 1, false);
        g.edge(2, 10, 1, false);
        g.edge(2, 6, 1, true);
        g.edge(6, 0, 1, false);
    }

    // prepare-routing.svg
    public static LevelGraph initShortcutsGraph(LevelGraph g) {
        g.edge(0, 1, 1, true);
        g.edge(0, 2, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(1, 4, 1, true);
        g.edge(2, 9, 1, true);
        g.edge(9, 3, 1, true);
        g.edge(10, 3, 1, true);
        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(6, 7, 1, true);
        g.edge(7, 8, 1, true);
        g.edge(8, 9, 1, true);
        g.edge(4, 11, 1, true);
        g.edge(9, 14, 1, true);
        g.edge(10, 14, 1, true);
        g.edge(11, 12, 1, true);
        g.edge(12, 15, 1, true);
        g.edge(12, 13, 1, true);
        g.edge(13, 16, 1, true);
        g.edge(15, 16, 2, true);
        g.edge(14, 16, 1, true);
        return g;
    }

//    public static void printEdges(LevelGraph g) {
//        RawEdgeIterator iter = g.getAllEdges();
//        while (iter.next()) {
//            EdgeSkipIterator single = g.getEdgeProps(iter.edge(), iter.nodeB());
//            System.out.println(iter.nodeA() + "<->" + iter.nodeB() + " \\"
//                    + single.skippedEdge1() + "," + single.skippedEdge2() + " (" + iter.edge() + ")"
//                    + ", dist: " + (float) iter.distance()
//                    + ", level:" + g.getLevel(iter.nodeA()) + "<->" + g.getLevel(iter.nodeB())
//                    + ", bothDir:" + CarFlagEncoder.isBoth(iter.flags()));
//        }
//        System.out.println("---");
//    }
    @Test
    public void testBits() {
        int fromNode = Integer.MAX_VALUE / 3 * 2;
        int endNode = Integer.MAX_VALUE / 37 * 17;

        long edgeId = (long) fromNode << 32 | endNode;
        assertEquals((BitUtil.toBitString(edgeId)),
                BitUtil.toBitString(fromNode, 32) + BitUtil.toBitString(endNode, 32));
    }
}
