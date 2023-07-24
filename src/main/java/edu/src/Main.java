package edu.src;

import edu.model.*;
import edu.model.sourcefile.ResourceLeakRule;
import edu.model.sourcefile.TypeStateRule;
import edu.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.xmlpull.v1.XmlPullParserException;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.android.manifest.ProcessManifest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        //ICC
        boolean IC3Success = prepareMethodList4ICC(apkPath);
        if(IC3Success){
            retrieveMethodOrder4ICC();
        }

        long afterDataTransfer = System.currentTimeMillis();
        System.out.println("==>after DATA transfer TIME:" + afterDataTransfer);

        detectMisusePatterns(GlobalRef.apiCallOrders4Method, "method");
        detectMisusePatterns(GlobalRef.apiCallOrders4Component, "Component");
        detectMisusePatterns(GlobalRef.apiCallOrders4Callback, "Callback");
        if(CollectionUtils.isNotEmpty(GlobalRef.apiCallOrders4ICC)){
            detectMisusePatterns(GlobalRef.apiCallOrders4ICC, "ICC");
        }

        detectResourceLeaks(GlobalRef.apiCallOrders4Method, "method");
        detectResourceLeaks(GlobalRef.apiCallOrders4Component, "Component");
        detectResourceLeaks(GlobalRef.apiCallOrders4Callback, "Callback");
        if(CollectionUtils.isNotEmpty(GlobalRef.apiCallOrders4ICC)){
            detectResourceLeaks(GlobalRef.apiCallOrders4ICC, "ICC");
        }


        long detectMisusePatterns = System.currentTimeMillis();
        System.out.println("==>after detectMisusePatterns TIME:" + detectMisusePatterns);

//        if (CollectionUtils.isNotEmpty(entryPointHelper.callbackClasses)) {
//            for (String s : entryPointHelper.callbackClasses) {
//                System.out.println("[callbackClasses:]" + s);
//            }
//        }
//        if (CollectionUtils.isNotEmpty(entryPointHelper.callbackMethods.keySet())) {
//            for (SootClass sc : entryPointHelper.callbackMethods.keySet()) {
//                System.out.println("[callbackMethods host soot class:]" + sc);
//                System.out.println("[callbackMethods callback Methods:]" + entryPointHelper.callbackMethods.get(sc));
//            }
//        }
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
        Set<String> logRes4Method = new HashSet<>();
        Set<String> logRes4NonMethod = new HashSet<>();
        for (TypeStateRule typeStateRule : GlobalRef.typeStateRules) {
            for(int listNum = 0; listNum < apiCallOrders.size(); listNum++){
                SootMethod targetMethod = null;
                if(flag.equals("method")){
                    targetMethod =  GlobalRef.apiCallOrders4Method_correspondingTargetMethod.get(listNum);
                }
                List<String> apiCallOrderList = apiCallOrders.get(listNum);
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
                                String specificReason = "[lack of]" + ListHelper.listToString(typeStateRule.beforeAPIs) + "[before]" + apiCallOrderList.get(i);
                                if(flag.equals("method")){
                                    logRes4Method.add(logAPIMisuseRes(flag, apiCallOrderList, isApiCallOrderListMisuse, specificReason, targetMethod));
                                }else{
                                    logRes4NonMethod.add(logAPIMisuseRes(flag, apiCallOrderList, isApiCallOrderListMisuse, specificReason, targetMethod));
                                }
                            }
                        } else {
                            if (TypeStateRule.containsTypeStateAPIStmt(subList, typeStateRule.beforeAPIs)) {
                                isApiCallOrderListMisuse = true;
                                String specificReason = "[Should not invoke]" + ListHelper.listToString(typeStateRule.beforeAPIs) + "[before]" + apiCallOrderList.get(i);
                                if(flag.equals("method")){
                                    logRes4Method.add(logAPIMisuseRes(flag, apiCallOrderList, isApiCallOrderListMisuse, specificReason, targetMethod));
                                }else{
                                    logRes4NonMethod.add(logAPIMisuseRes(flag, apiCallOrderList, isApiCallOrderListMisuse, specificReason, targetMethod));
                                }
                            }
                        }
                    }
                }
            }
        }

        if(CollectionUtils.isNotEmpty(logRes4Method)){
            for(String misuseLog : logRes4Method){
                if(StringUtils.isNotBlank(misuseLog)){
                    System.out.println(misuseLog);
                }
            }
        }

        if(CollectionUtils.isNotEmpty(logRes4NonMethod)){
            for(String misuseLog : logRes4NonMethod){
                if(StringUtils.isNotBlank(misuseLog)){
                    System.out.println(misuseLog);
                }
            }
        }
    }

    private static String logAPIMisuseRes(String flag, List<String> apiCallOrderList, boolean isApiCallOrderListMisuse, String specificReason, SootMethod targetMethod) {
        if(apiCallOrderList.contains("<android.webkit.CookieSyncManager: android.webkit.CookieSyncManager getInstance()>") && (GlobalRef.minSDKVersion > 18 || GlobalRef.targetSDKVersion > 18)){

        }else if(ApplicationClassFilter.containsStartRecording(apiCallOrderList) && (GlobalRef.targetSDKVersion < 31)){

        }else{
            if (isApiCallOrderListMisuse && targetMethod != null) {
                return "======Misuse=======" + flag + ":" + specificReason + "; Origin apiCallOrderList is:" + apiCallOrderList + "; targetMethod:"+targetMethod.getSignature();
            }
            if(isApiCallOrderListMisuse && targetMethod == null){
                return "======Misuse=======" + flag + ":" + specificReason + "; Origin apiCallOrderList is:" + apiCallOrderList;
            }
        }
        return "";
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
        Set<String> logRes4Method = new HashSet<>();
        Set<String> logRes4NonMethod = new HashSet<>();
        for (ResourceLeakRule resourceLeakRule : GlobalRef.resourceLeakRules) {
            for(int listNum = 0; listNum < apiCallOrders.size(); listNum++){
                SootMethod targetMethod = null;
                if(flag.equals("method")){
                    targetMethod =  GlobalRef.apiCallOrders4Method_correspondingTargetMethod.get(listNum);
                }
                List<String> apiCallOrderList = apiCallOrders.get(listNum);

                boolean isApiCallOrderHasResourceLeak = false;
                for (int i = 0; i < apiCallOrderList.size(); i++) {
                    if (resourceLeakRule.beforeAPI.equals(apiCallOrderList.get(i))) {
                        //api from i+1 to apiCallOrderList.size() must contains afterAPI
                        List<String> subList = apiCallOrderList.subList(i+1, apiCallOrderList.size());
                        if (!ResourceLeakRule.containsResourceLeakAPIStmt(subList, resourceLeakRule.afterAPI)) {
                            isApiCallOrderHasResourceLeak =  true;
                            String specificReason = "[Should invoke]" + resourceLeakRule.afterAPI.getAPIString() + "[after]" + apiCallOrderList.get(i);
                            if(flag.equals("method")){
                                logRes4Method.add(logResourceLeakRes(flag, apiCallOrderList, isApiCallOrderHasResourceLeak, specificReason, targetMethod));
                            }else{
                                logRes4NonMethod.add(logResourceLeakRes(flag, apiCallOrderList, isApiCallOrderHasResourceLeak, specificReason, targetMethod));
                            }
                        }
                    }
                }
            }
        }


        if(CollectionUtils.isNotEmpty(logRes4Method)){
            for(String misuseLog : logRes4Method){
                if(StringUtils.isNotBlank(misuseLog)){
                    System.out.println(misuseLog);
                }
            }
        }

        if(CollectionUtils.isNotEmpty(logRes4NonMethod)){
            for(String misuseLog : logRes4NonMethod){
                if(StringUtils.isNotBlank(misuseLog)){
                    System.out.println(misuseLog);
                }
            }
        }
    }

    private static String logResourceLeakRes(String flag, List<String> apiCallOrderList, boolean isApiCallOrderHasResourceLeak, String specificReason, SootMethod targetMethod) {
        if (isApiCallOrderHasResourceLeak && targetMethod != null) {
            return "======ResourceLeak=======" + flag + ":" + specificReason + "; Origin apiCallOrderList is:" + apiCallOrderList + "; targetMethod:"+targetMethod.getSignature();
        }else if(isApiCallOrderHasResourceLeak && targetMethod == null){
            return "======ResourceLeak=======" + flag + ":" + specificReason + "; Origin apiCallOrderList is:" + apiCallOrderList;
        }else{
            return "";
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

    public static void retrieveMethodOrder4ICC() {
        //retrieve and write to methodCallOrders
        for (int i = 0; i < GlobalRef.typeStateICCList.size(); i++) {
            TypeStateICC typeStateICC = GlobalRef.typeStateICCList.get(i);
            for (List<SootMethod> methodChainList : typeStateICC.methodChain) {
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
                    GlobalRef.methodCallOrders4ICC.add(newMethodChainList);
                }
            }
        }

        //retrieve and write to apiCallOrders
        if (CollectionUtils.isNotEmpty(GlobalRef.methodCallOrders4ICC)) {
            for (int i = 0; i < GlobalRef.methodCallOrders4ICC.size(); i++) {
                List<SootMethod> sootMethods = GlobalRef.methodCallOrders4ICC.get(i);
                List<List<String>> apiOrderRes = getAPIOrder4Method(TypeStateUtil.getFromList(GlobalRef.typeStateMethodList, sootMethods.get(0)));
                for (int m = 1; m < sootMethods.size(); m++) {
                    List<List<String>> currentAPIOrder = getAPIOrder4Method(TypeStateUtil.getFromList(GlobalRef.typeStateMethodList, sootMethods.get(m)));
                    List<List<String>> res = ListHelper.combine2List(apiOrderRes, currentAPIOrder);
                    apiOrderRes = res;
                }
                GlobalRef.apiCallOrders4ICC.addAll(apiOrderRes);
            }
        }
    }


    public static boolean prepareMethodList4ICC(String apkPath) throws IOException {
        //extract IC3 results[className+methodName ==> clazzName]
        String apkName = Regex.getSubUtilSimple(apkPath.replace(".apk", ""), "([^/]*$)");
        File apkFile = new File(GlobalRef.IC3_PATH + apkName+".txt");
        if(!apkFile.exists()){
            return false;
        }
        List<IC3> IC3List = new ArrayList<>();
        List<String> ic3File = new ArrayList<>(Files.readAllLines(apkFile.toPath(), StandardCharsets.UTF_8));
        int startLineNumber = 0;
        boolean hasIC3Res = false;
        for (int i = 0; i < ic3File.size(); i++) {
            String currentLine = ic3File.get(i);
            if(currentLine.equals("*****Result*****")){
                startLineNumber = i + 1;
                hasIC3Res = true;
                break;
            }
        }

        if(!hasIC3Res){
            return false;
        }

        for (int i = startLineNumber; i < ic3File.size() && i+2<ic3File.size(); i++) {
            String currentLine = ic3File.get(i);
            String currentLineClazz = ic3File.get(i+2);
            if(currentLine.startsWith(apkName) && currentLineClazz.contains("clazz=")){
                String className = Regex.getSubUtilSimple(currentLine, "(^.*/)").replace("/","");
                String methodName = Regex.getSubUtilSimple(currentLine, "( .*?\\()").replace(" ","").replace("(", "");
                String clazzName = Regex.getSubUtilSimple(currentLineClazz, "(clazz=.*?,)").replace("clazz=","").replace(",","").replace("/", ".");
                if(StringUtils.isNotBlank(className) && StringUtils.isNotBlank(methodName) && StringUtils.isNotBlank(clazzName)){
                    IC3List.add(new IC3(className, methodName, clazzName));
                    //System.out.println(className + "---" + methodName + "===="+clazzName);
                }
            }
        }

        //retrieve method order for ICC
        for(IC3 ic3 : IC3List){
            List<List<SootMethod>> leftMethodChain = new ArrayList<>();
            List<List<SootMethod>> rightMethodChain = new ArrayList<>();
            if(CollectionUtils.isNotEmpty(GlobalRef.typeStateComponentList)){
                for(TypeStateComponent typeStateComponent : GlobalRef.typeStateComponentList){
                    for(SootMethod sm : typeStateComponent.entryPointClass.getMethods()){
                        if(sm.getReturnType().toString().equals(ic3.className)){
                            leftMethodChain = typeStateComponent.methodChain;
                        }
                        if(sm.getReturnType().toString().equals(ic3.clazzName)){
                            rightMethodChain.addAll(typeStateComponent.methodChain);
                        }
                    }
                }
            }


            TypeStateICC typeStateICC = new TypeStateICC();
            typeStateICC.ic3 = ic3;
            GlobalRef.typeStateICCList.add(typeStateICC);
            if(CollectionUtils.isNotEmpty(leftMethodChain) && CollectionUtils.isNotEmpty(rightMethodChain)){
                for(List<SootMethod> leftList : leftMethodChain){
                    for(List<SootMethod> rightList : rightMethodChain){
                        List<SootMethod> combinedList = new ArrayList<>();
                        combinedList.addAll(leftList);
                        combinedList.addAll(rightList);
                        typeStateICC.methodChain.add(combinedList);
                    }
                }
            }
        }
        return true;
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
            GlobalRef.apiCallOrders4Method_correspondingTargetMethod.add(currentTypeStateMethod.sootMethod);
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
