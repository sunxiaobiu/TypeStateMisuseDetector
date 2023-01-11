package edu.src;

import edu.model.TypeStateCallback;
import edu.model.TypeStateComponent;
import edu.model.TypeStateMethod;
import edu.model.sourcefile.ResourceLeakRule;
import edu.model.sourcefile.TypeStateRule;
import edu.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.xmlpull.v1.XmlPullParserException;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.android.manifest.ProcessManifest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {

    public static void main(String[] args) throws XmlPullParserException, IOException {
        String apkPath = args[0];
        String androidJarPath = args[1];

        getMinTargetSDKVersion(apkPath);

        long startTime = System.currentTimeMillis();
        System.out.println("==>START TIME:" + startTime);

        //calculate EntryPoint to generate dummyMainMethod
        EntryPointHelper entryPointHelper = calculateEntryPoint(apkPath, androidJarPath);

        long afterEntryPoint = System.currentTimeMillis();
        System.out.println("==>after EntryPoint TIME:" + afterEntryPoint);

        //TypeStateAnalysis
        TypeStateUtil.typeStateAnalysis();

        long afterTypeStateAnalysis = System.currentTimeMillis();
        System.out.println("==>after TypeStateAnalysis TIME:" + afterTypeStateAnalysis);

        //sanitize results to API/method order list
        retrieveAPIOrder4MethodList();
        retrieveMethodOrder4ComponentList();
        retrieveMethodOrder4CallbackList();

        long afterDataTransfer = System.currentTimeMillis();
        System.out.println("==>after DATA transfer TIME:" + afterDataTransfer);

        detectMisusePatterns(GlobalRef.apiCallOrders4Method, "method");
        detectMisusePatterns(GlobalRef.apiCallOrders4Component, "Component");
        detectMisusePatterns(GlobalRef.apiCallOrders4Callback, "Callback");

        detectResourceLeaks(GlobalRef.apiCallOrders4Method, "method");
        detectResourceLeaks(GlobalRef.apiCallOrders4Component, "Component");
        detectResourceLeaks(GlobalRef.apiCallOrders4Callback, "Callback");

        long detectMisusePatterns = System.currentTimeMillis();
        System.out.println("==>after detectMisusePatterns TIME:" + detectMisusePatterns);

        if (CollectionUtils.isNotEmpty(entryPointHelper.callbackClasses)) {
            for (String s : entryPointHelper.callbackClasses) {
                System.out.println("[callbackClasses:]" + s);
            }
        }
        if (CollectionUtils.isNotEmpty(entryPointHelper.callbackMethods.keySet())) {
            for (SootClass sc : entryPointHelper.callbackMethods.keySet()) {
                System.out.println("[callbackMethods host soot class:]" + sc);
                System.out.println("[callbackMethods callback Methods:]" + entryPointHelper.callbackMethods.get(sc));
            }
        }
    }

    private static void getMinTargetSDKVersion(String apkPath) throws IOException, XmlPullParserException {
        try {
            ProcessManifest manifest = new ProcessManifest(apkPath);
            GlobalRef.minSDKVersion = manifest.getMinSdkVersion();
            GlobalRef.targetSDKVersion = manifest.getTargetSdkVersion();
            System.out.println("====APK minSDKVersion====" + GlobalRef.minSDKVersion);
            System.out.println("====APK targetSDKVersion====" + GlobalRef.targetSDKVersion);
        } catch (Exception e) {
            GlobalRef.minSDKVersion = 30;
        }
    }

    private static void detectMisusePatterns(List<List<String>> apiCallOrders, String flag) {
        for (TypeStateRule typeStateRule : GlobalRef.typeStateRules) {
            for (List<String> apiCallOrderList : apiCallOrders) {
                //apiCallOrderList indicate one api order call in real-world apps
                boolean isApiCallOrderListMisuse = false;
                for (int i = 0; i < apiCallOrderList.size(); i++) {
                    if (typeStateRule.afterAPI.equals(apiCallOrderList.get(i)) && i-1 >=0) {
                        //api i-1 must contains one of the beforeAPIs
                        //List<String> subList = apiCallOrderList.subList(0, i);
                        List<String> subList = new ArrayList<>();
                        subList.add(apiCallOrderList.get(i-1));
                        if (typeStateRule.isCorrect) {
                            if (!TypeStateRule.containsTypeStateAPIStmt(subList, typeStateRule.beforeAPIs)) {
                                isApiCallOrderListMisuse = true;
                            }
                        } else {
                            if (TypeStateRule.containsTypeStateAPIStmt(subList, typeStateRule.beforeAPIs)) {
                                isApiCallOrderListMisuse = true;
                            }
                        }
                    }
                }

                if(apiCallOrderList.contains("<android.webkit.CookieSyncManager: android.webkit.CookieSyncManager getInstance()>") && (GlobalRef.minSDKVersion > 18 || GlobalRef.targetSDKVersion > 18)){

                }else if(ApplicationClassFilter.containsStartRecording(apiCallOrderList) && (GlobalRef.targetSDKVersion < 31)){

                }else{
                    if (isApiCallOrderListMisuse) {
                        if(flag.equals("method")){
                            System.out.println("======Misuse=======" + flag + ":" + apiCallOrderList);
                        }else{
                            System.out.println("======Misuse=======" + flag + ":" + apiCallOrderList);
                        }
                    }
                }

            }
        }
    }

    private static boolean moreThan2(List<String> apis){
        if(CollectionUtils.isEmpty(apis)){
            return false;
        }
        Set<String> apiSet = new HashSet<>(apis);
        return apiSet.size() >= 2;
    }

    private static boolean belong2SameClass(List<String> apis){
        if(CollectionUtils.isEmpty(apis)){
            return false;
        }
        Set<String> className = new HashSet<>();
        for(String api : apis){
            className.add(Regex.getSubUtilSimple(api, "(<.*?:)"));
        }
        return className.size() == 1;
    }

    private static void detectResourceLeaks(List<List<String>> apiCallOrders, String flag) {
        for (ResourceLeakRule resourceLeakRule : GlobalRef.resourceLeakRules) {
            for (List<String> apiCallOrderList : apiCallOrders) {

                boolean isApiCallOrderHasResourceLeak = false;
                for (int i = 0; i < apiCallOrderList.size(); i++) {
                    if (resourceLeakRule.beforeAPI.equals(apiCallOrderList.get(i))) {
                        //api from i+1 to apiCallOrderList.size() must contains afterAPI
                        List<String> subList = apiCallOrderList.subList(i+1, apiCallOrderList.size());
                        if (!ResourceLeakRule.containsResourceLeakAPIStmt(subList, resourceLeakRule.afterAPI)) {
                            isApiCallOrderHasResourceLeak =  true;
                        }
                    }
                }
                if (isApiCallOrderHasResourceLeak) {
                    System.out.println("======ResourceLeak=======" + flag + ":" + apiCallOrderList);
                }
            }
        }
    }

    public static void retrieveMethodOrder4ComponentList() {
        //retrieve and write to methodCallOrders
        for (int i = 0; i < GlobalRef.typeStateComponentList.size(); i++) {
            TypeStateComponent typeStateComponent = GlobalRef.typeStateComponentList.get(i);
            for (List<SootMethod> methodChainList : typeStateComponent.methodChain) {
                List<SootMethod> newMethodChainList = new ArrayList<>();
                for (int j = 0; j < methodChainList.size(); j++) {
                    SootMethod currentSootMethod = methodChainList.get(j);
                    if (currentSootMethod == null) {
                        break;
                    }
                    if (TypeStateUtil.getFromList(GlobalRef.typeStateMethodList, currentSootMethod) != null) {
                        newMethodChainList.add(currentSootMethod);
                    }
                }
                if (newMethodChainList.size() >= 2) {
                    GlobalRef.methodCallOrders4Component.add(newMethodChainList);
                }
            }
        }

        //retrieve and write to apiCallOrders
        if (CollectionUtils.isNotEmpty(GlobalRef.methodCallOrders4Component)) {
            for (int i = 0; i < GlobalRef.methodCallOrders4Component.size(); i++) {
                List<SootMethod> sootMethods = GlobalRef.methodCallOrders4Component.get(i);
                List<List<String>> apiOrderRes = getAPIOrder4Method(TypeStateUtil.getFromList(GlobalRef.typeStateMethodList, sootMethods.get(0)));
                for (int m = 1; m < sootMethods.size(); m++) {
                    List<List<String>> currentAPIOrder = getAPIOrder4Method(TypeStateUtil.getFromList(GlobalRef.typeStateMethodList, sootMethods.get(m)));
                    List<List<String>> res = ListHelper.combine2List(apiOrderRes, currentAPIOrder);
                    apiOrderRes = res;
                }
                GlobalRef.apiCallOrders4Component.addAll(apiOrderRes);
            }
        }
    }

    public static void retrieveMethodOrder4CallbackList() {
        //retrieve and write to methodCallOrders
        for (int i = 0; i < GlobalRef.typeStateCallbackList.size(); i++) {
            TypeStateCallback typeStateCallback = GlobalRef.typeStateCallbackList.get(i);
            for (List<SootMethod> methodChainList : typeStateCallback.methodChain) {
                List<SootMethod> newMethodChainList = new ArrayList<>();
                for (int j = 0; j < methodChainList.size(); j++) {
                    SootMethod currentSootMethod = methodChainList.get(j);
                    if (currentSootMethod == null) {
                        break;
                    }
                    if (TypeStateUtil.getFromList(GlobalRef.typeStateMethodList, currentSootMethod) != null) {
                        newMethodChainList.add(currentSootMethod);
                    }
                }
                if (newMethodChainList.size() >= 2) {
                    GlobalRef.methodCallOrders4Callback.add(newMethodChainList);
                }
            }
        }

        //retrieve and write to apiCallOrders
        if (CollectionUtils.isNotEmpty(GlobalRef.methodCallOrders4Callback)) {
            for (int i = 0; i < GlobalRef.methodCallOrders4Callback.size(); i++) {
                List<SootMethod> sootMethods = GlobalRef.methodCallOrders4Callback.get(i);
                List<List<String>> apiOrderRes = getAPIOrder4Method(TypeStateUtil.getFromList(GlobalRef.typeStateMethodList, sootMethods.get(0)));
                for (int m = 1; m < sootMethods.size(); m++) {
                    List<List<String>> currentAPIOrder = getAPIOrder4Method(TypeStateUtil.getFromList(GlobalRef.typeStateMethodList, sootMethods.get(m)));
                    List<List<String>> res = ListHelper.combine2List(apiOrderRes, currentAPIOrder);
                    apiOrderRes = res;
                }
                GlobalRef.apiCallOrders4Callback.addAll(apiOrderRes);
            }
        }
    }

    public static void retrieveAPIOrder4MethodList() {
        for (int i = 0; i < GlobalRef.typeStateMethodList.size(); i++) {
            TypeStateMethod currentTypeStateMethod = GlobalRef.typeStateMethodList.get(i);
            extractedAPIOrder4Method(currentTypeStateMethod);
        }
    }

    private static void extractedAPIOrder4Method(TypeStateMethod currentTypeStateMethod) {
        for (List<Unit> units : currentTypeStateMethod.unitChain) {
            List<String> typeStateApiOrderList = new ArrayList<>();
            for (int j = 0; j < units.size(); j++) {
                Unit currentUnit = units.get(j);
                if (currentUnit == null) {
                    break;
                }

                String unitSignature = TypeStateUtil.containsTypeStateStmt(currentUnit, GlobalRef.allAPIList);
                if (StringUtils.isNotBlank(unitSignature)) {
                    typeStateApiOrderList.add(unitSignature);
                }
            }
            GlobalRef.apiCallOrders4Method.add(typeStateApiOrderList);
        }
    }

    private static List<List<String>> getAPIOrder4Method(TypeStateMethod currentTypeStateMethod) {
        List<List<String>> res = new ArrayList<>();
        for (List<Unit> units : currentTypeStateMethod.unitChain) {
            List<String> typeStateApiOrderList = new ArrayList<>();
            for (int j = 0; j < units.size(); j++) {
                Unit currentUnit = units.get(j);
                if (currentUnit == null) {
                    break;
                }

                String unitSignature = TypeStateUtil.containsTypeStateStmt(currentUnit, GlobalRef.allAPIList);
                if (StringUtils.isNotBlank(unitSignature)) {
                    typeStateApiOrderList.add(unitSignature);
                }
            }
            res.add(typeStateApiOrderList);
        }
        return res;
    }

    public static EntryPointHelper calculateEntryPoint(String apkPath, String androidJarPath) throws XmlPullParserException, IOException {
        EntryPointHelper entryPointHelper = new EntryPointHelper();
        entryPointHelper.calculateEntryPoint(apkPath, androidJarPath);
        return entryPointHelper;
    }

}
