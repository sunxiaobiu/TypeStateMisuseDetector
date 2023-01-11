package edu.util;

import edu.model.TypeStateComponent;
import edu.model.TypeStateMethod;
import edu.src.GlobalRef;
import org.apache.commons.collections4.CollectionUtils;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.DirectedGraph;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class CFGUtil {

    public static void getPathsFromRoot2Leaf(Unit unit, Unit[] path, int pathLen, DirectedGraph<Unit> icfg, SootMethod sootMethod, List<Unit> visited) {
        if (unit == null || visited.contains(unit))
            return;

        /* append this node to the path array */
        path[pathLen] = unit;
        pathLen++;
        visited.add(unit);

        /* it's a leaf, so print the path that lead to here  */
        List<Unit> succsNodes = icfg.getSuccsOf(unit);
        if (CollectionUtils.isEmpty(succsNodes)) {
            TypeStateMethod typeStateMethod = TypeStateUtil.getFromList(GlobalRef.typeStateMethodList, sootMethod);
            if (!typeStateMethod.unitChain.contains(Arrays.asList(path))) {
                typeStateMethod.unitChain.add(Arrays.asList(path));
            }
            System.out.println(typeStateMethod.toString());
        } else {
            for (Unit succsNode : succsNodes) {
                getPathsFromRoot2Leaf(succsNode, path, pathLen, icfg, sootMethod, visited);
            }
        }
    }

    public static void getMethodPathsFromRoot2Leaf(SootMethod sootMethod, SootMethod[] path, int pathLen, SootMethod entryPointMethod, List<SootMethod> visited) {
        if (sootMethod == null || visited.contains(sootMethod) || ApplicationClassFilter.isClassSystemPackage(sootMethod.getSignature())
        )
            return;

        /* append this node to the path array */
        path[pathLen] = sootMethod;
        pathLen++;
        visited.add(sootMethod);

        /* it's a leaf, so print the path that lead to here  */
        Iterator<Edge> edgeIterator = Scene.v().getCallGraph().edgesOutOf(sootMethod);
        if (!edgeIterator.hasNext()) {
            TypeStateComponent typeStateComponent = TypeStateUtil.getElementFromList(GlobalRef.typeStateComponentList, entryPointMethod);
            if (!typeStateComponent.methodChain.contains(Arrays.asList(path))) {
                typeStateComponent.methodChain.add(Arrays.asList(path));
            }
            System.out.println(typeStateComponent.toString());
        } else {
            while(edgeIterator.hasNext()){
                Edge edge = edgeIterator.next();
                getMethodPathsFromRoot2Leaf(edge.getTgt().method(), path, pathLen, entryPointMethod, visited);
            }
        }
    }
}
