package edu.util;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListHelper {

    public static List<List<String>> combine2List(List<List<String>> list1, List<List<String>> list2) {
        List<List<String>> res = new ArrayList<>();
        if (CollectionUtils.isEmpty(list1) && CollectionUtils.isEmpty(list2)) {
            return res;
        }
        if (CollectionUtils.isEmpty(list1) && CollectionUtils.isNotEmpty(list2)) {
            return list2;
        }
        if (CollectionUtils.isNotEmpty(list1) && CollectionUtils.isEmpty(list2)) {
            return list2;
        }
        for (List<String> oneList : list1) {
            for (List<String> twoList : list2) {
                List<String> newList = new ArrayList<>();
                newList.addAll(oneList);
                newList.addAll(twoList);
                res.add(newList);
            }
        }

        return res;
    }

//    public static void main(String[] args) {
//        List<List<String>> list1 = new ArrayList<List<String>>();
//        List<String> lista = Arrays.asList("a", "b", "c");
//        List<String> listb = Arrays.asList("1", "2", "3");
//        list1.add(lista);
//        list1.add(listb);
//        List<List<String>> list2 = new ArrayList<List<String>>();
//        List<String> listc = Arrays.asList("4", "5", "6");
//        List<String> listd = Arrays.asList("7", "8", "9");
//        list2.add(listc);
//        list2.add(listd);
//        List<List<String>> res = combine2List(list1, list2);
//        System.out.println(res);
//    }

    public static void main(String[] args) {
        List<List<String>> list1 = new ArrayList<List<String>>();
        List<String> lista = Arrays.asList("a", "b", "c");
        List<String> listb = Arrays.asList("1", "2", "3");
        list1.add(lista);
        list1.add(listb);

        List<List<String>> lise2 = new ArrayList<>();
        lise2.addAll(list1);
        System.out.println(lise2);

    }
}
