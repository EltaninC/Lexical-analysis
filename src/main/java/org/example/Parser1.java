package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

public class Parser1 {
    public static final String PATH = "./grammar.txt";// 文法
    private static String START; // 开始符号
    private static HashSet<String> VN, VT; // 非终结符号集、终结符号集
    private static HashMap<String, ArrayList<ArrayList<String>>> MAP;// key:产生式左边 value:产生式右边(含多条)
    private static HashMap<String, String> oneLeftFirst;// "|" 分开的单条产生式对应的FIRST集合,用于构建预测分析表
    private static HashMap<String, HashSet<String>> FIRST, FOLLOW; // FIRST、FOLLOW集合
    private static String[][] FORM; // 存放预测分析表的数组，用于输出
    private static HashMap<String, String> preMap;// 存放预测分析表的map，用于快速查找
    private static HashMap<String, String> autoIncr;// 存放非终结符号,进行左递归等操作时自动加'的map,保证符号唯一性

    public static void run() {
        init(); // 初始化变量
        // 文件读入
        ArrayList<String> fileRead = readFile(new File(PATH));
        ArrayList<ArrayList<String>> grammar = new ArrayList<>();
        ArrayList<String> aGrammar = new ArrayList<>();
        int num = 0;
        boolean flag = false;
        for (String line: fileRead) {
            if (flag == true && line.isEmpty() == true) {
                flag = false;

                aGrammar = new ArrayList<>();
                continue;
            }
            if (flag == false && line.isEmpty() == false) {
                num++;
                flag = true;
                grammar.add(aGrammar);
                grammar.get(num-1).add(line);
                continue;
            }
            if (!line.isEmpty()) { grammar.get(num-1).add(line); continue;}
        }
        System.out.println("文件中共有" + num + "个文法，如果所示");
        for (int i = 0; i < num; i++){
            System.out.println("文法"+(i+1)+":");
            for (String s: grammar.get(i)) {
                System.out.println(s);
            }
            System.out.println("\n");
        }
        int choice = -1;
        System.out.println("请选择其中一个文法进一步分析");
        Scanner in = new Scanner(System.in);
        choice = in.nextInt();
        if (choice>0&&choice<=num){
            identifyVnVt(grammar.get(choice-1));// 符号分类,并以key-value形式存于MAP中
            reformMap();// 消除左递归和提取左公因子
            findFirst(); // 求FIRST集合
            findFollow(); // 求FOLLOW集合
            if (isLL1()) {
                preForm(); // 构建预测分析表
                // printAutoPre("aacbd"); // 示例推导
                System.out.println("请输入要分析的单词串:");
                in.nextLine();
                printAutoPre(in.nextLine());
                in.close();
                return;
            }
        }
        else {
            System.out.println("请选择正确的文法编号");
        }
        in.close();
        return;
    }

    // 变量初始化
    private static void init() {
        VN = new HashSet<>();
        VT = new HashSet<>();
        MAP = new HashMap<>();
        FIRST = new HashMap<>();
        FOLLOW = new HashMap<>();
        oneLeftFirst = new HashMap<>();
        preMap = new HashMap<>();
        autoIncr = new HashMap<>();
    }

    // 从文件读文法
    public static ArrayList<String> readFile(File file) {
        System.out.println("从文件读入的文法为:");
        ArrayList<String> result = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String s = null;
            while ((s = br.readLine()) != null) {
                System.out.println("\t" + s);
                result.add(s.trim());
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    // 符号分类
    private static void identifyVnVt(ArrayList<String> list) {
        START = list.get(0).charAt(0) + "";// 存放开始符号

        for (int i = 0; i < list.size(); i++) {
            String oneline = list.get(i);
            String[] vnvt = oneline.split("→");// 用定义符号分割
            String left = vnvt[0].trim(); // 文法的左边
            VN.add(left);
            autoIncr.put(left, left); // 自动加'的map,保证符号唯一性

            // 文法右边
            ArrayList<ArrayList<String>> mapValue = new ArrayList<>();
            ArrayList<String> right = new ArrayList<>();

            for (int j = 0; j < vnvt[1].length(); j++) { // 用 “|”分割右边
                if (vnvt[1].charAt(j) == '|') {
                    VT.addAll(right);
                    mapValue.add(right);
                    right = new ArrayList<>();  // 保存的是地址，依然是同一个地址，需要重新new对象
                    continue;
                }
                right.add(vnvt[1].charAt(j) + "");
            }
            VT.addAll(right);
            mapValue.add(right);

            MAP.put(left, mapValue);
        }
        VT.removeAll(VN); // 从终结字符集中移除非终结符
        // 打印Vn、Vt
        System.out.println("\nVn集合:\t{" + String.join("、", VN.toArray(new String[VN.size()])) + "}");
        System.out.println("Vt集合:\t{" + String.join("、", VT.toArray(new String[VT.size()])) + "}");

    }


    private static void reformMap() {
        // 消除直接左递归和隐式左递归
        YleftRecursion();
        // 提取左公因子
        leftFactor();

        // 输出修改后的文法
        System.out.println("消除文法的左递归:");
        Set<String> kSet = new HashSet<>(MAP.keySet());
        Iterator<String> itk = kSet.iterator();
        while (itk.hasNext()) {
            String k = itk.next();
            ArrayList<ArrayList<String>> leftList = MAP.get(k);
            System.out.print("\t" + k + "→");
            for (int i = 0; i < leftList.size(); i++) {
                System.out.print(String.join("", leftList.get(i).toArray(new String[leftList.get(i).size()])));
                if (i + 1 < leftList.size())
                    System.out.print("|");
            }
            System.out.println();
        }
    }

    //消除左递归
    private static void YleftRecursion() {
        Set<String> keys = new HashSet<>();
        keys.addAll(MAP.keySet());
        Iterator<String> it = keys.iterator();
        ArrayList<String> nullSign = new ArrayList<>();
        nullSign.add("ε");
        while (it.hasNext()) {
            String left = it.next();
            boolean flag = false;// 是否有左递归
            ArrayList<ArrayList<String>> rightList = MAP.get(left);
            ArrayList<ArrayList<String>> oldRightOld = new ArrayList<>(); // 旧产生的右边
            ArrayList<ArrayList<String>> newLeftNew = new ArrayList<>();// 存放新的右边

            // 消除左递归
            for (int i = 0; i < rightList.size(); i++) {
                ArrayList<String> newRightCell = new ArrayList<>(); // 新产生式的右边子项
                ArrayList<String> oldRightCell = new ArrayList<>(); // 旧产生式的右边子项
                if (rightList.get(i).get(0).equals(left)) {
                    for (int j = 1; j < rightList.get(i).size(); j++) {
                        newRightCell.add(rightList.get(i).get(j));
                    }
                    flag = true;
                    newRightCell.add(autoIncr.get(left) + "\'");
                    newLeftNew.add(newRightCell);
                } else if (VN.contains(rightList.get(i).get(0))) { //消除隐式左递归
                    ArrayList<ArrayList<String>> conversion = conversion(rightList.get(i), left);
                    if(!conversion.isEmpty()){
                        rightList.remove(i);
                        rightList.addAll(conversion);
                        i--;
                    }
                } else {
                    if(!rightList.get(i).get(0).equals("ε")) {
                        oldRightCell.addAll(rightList.get(i));
                    }
                    oldRightCell.add(autoIncr.get(left) + "\'");
                    oldRightOld.add(oldRightCell);
                }
            }
            if (flag && !oldRightOld.isEmpty()) {// 如果有左递归，则更新MAP
                newLeftNew.add(nullSign);
                autoIncr.put(left, autoIncr.get(left) + "\'");// 自动加'的map,保证符号唯一性
                MAP.put(autoIncr.get(left), newLeftNew);
                VN.add(autoIncr.get(left)); // 加入新的VN
                VT.add("ε"); // 加入ε到VT
                MAP.put(left, oldRightOld);
            }
        }
    }

    //隐式转非隐式
    private static ArrayList<ArrayList<String>> conversion(ArrayList<String> arrayList,String left){
        boolean isDo = false;
        ArrayList<ArrayList<String>> arrayLists1 = MAP.get(arrayList.get(0));
        for (int i = 0; i < arrayLists1.size(); i++) {
            ArrayList<String> list = arrayLists1.get(i);
            if(list.get(0).equals(left)){
                isDo = true;
            } else if (VN.contains(list.get(0))) {
                ArrayList<ArrayList<String>> conversion = conversion(list, left);
                if(!conversion.isEmpty()){
                    arrayLists1.remove(i);
                    arrayLists1.addAll(conversion);
                    i--;
                }
            }
        }
        ArrayList<ArrayList<String>> arrayLists = new ArrayList<>();
        if(isDo){
            for(int i = 0; i < arrayLists1.size(); i++){
                ArrayList<String> a = new ArrayList<>();
                if(!arrayLists1.get(i).get(0).equals("ε")) {
                    a.addAll(arrayLists1.get(i));
                }
                a.addAll(arrayList.subList(1,arrayList.size()));
                arrayLists.add(a);
            }
        }
        return arrayLists;
    }

    //消除左因子
    private static void leftFactor() {
        Deque<String> keys = new ArrayDeque<String>();
        keys.addAll(MAP.keySet());
        ArrayList<String> nullSign = new ArrayList<>();
        nullSign.add("ε");
        while (!keys.isEmpty()) {
            String left = keys.pollFirst();
            ArrayList<ArrayList<String>> rightList = MAP.get(left);
            ArrayList<ArrayList<String>> newLeftNew = new ArrayList<>();// 存放新的左边和新的右边
            ArrayList<String> prefix = new ArrayList<>();

            // 获取最长左因子
            for (int i = 0; i < rightList.size(); i++) {
                for(int j = i+1; j < rightList.size(); j++) {
                    ArrayList<String> temp = commonPrefix(rightList.get(i), rightList.get(j));
                    if(temp.size() > prefix.size()){
                        prefix = temp;
                    }
                }
            }
            if (prefix.size()>0) {// 如果有左因子，则更新MAP
                boolean flag = true;
                ArrayList<ArrayList<String>> newLeftOld = new ArrayList<>();// 存放原先，但是产生新的右边
                for (int i = 0; i < rightList.size(); i++) {
                    if(rightList.get(i).size() >= prefix.size()
                            && prefix.equals(rightList.get(i).subList(0, prefix.size()))){
                        ArrayList<String> newRightCell =
                                new ArrayList(rightList.get(i).subList(prefix.size(), rightList.get(i).size()));
                        if(newRightCell.size() == 0){
                            flag = false;
                        }
                        else{
                            newLeftNew.add(newRightCell);
                        }
                    }
                    else{
                        newLeftOld.add(rightList.get(i));
                    }
                }
                if(!flag){
                    newLeftNew.add(nullSign);
                }
                autoIncr.put(left, autoIncr.get(left) + "\'"); // 自动加'的map,保证符号唯一性
                MAP.put(autoIncr.get(left), newLeftNew);
                VN.add(autoIncr.get(left)); // 加入新的VN
                ArrayList<String> oldRightCell = new ArrayList<>(); // 旧产生的右边
                oldRightCell.addAll(prefix);
                oldRightCell.add(autoIncr.get(left));
                newLeftOld.add(oldRightCell);
                MAP.put(left, newLeftOld);
                keys.addLast(left);
                keys.addLast(autoIncr.get(left));
            }
        }
    }



    public static ArrayList<String> commonPrefix(ArrayList<String> str1, ArrayList<String> str2) {
        int i = 0;
        for (; i < str1.size() && i < str2.size(); i++) {
            if (!str1.get(i).equals(str2.get(i))){
                break;
            }
        }
        return new ArrayList(str1.subList(0, i)) ;
    }


    // 判断是否是LL(1)文法
    private static boolean isLL1() {
        System.out.println("\n正在判断是否是LL(1)文法....");
        boolean flag = true;// 标记是否是LL(1)文法
        Iterator<String> it = VN.iterator();
        while (it.hasNext()) {
            String key = it.next();
            ArrayList<ArrayList<String>> list = MAP.get(key);// 单条产生式
            if (list.size() > 1) // 如果单条产生式的左边包含两个式子以上，则进行判断
                for (int i = 0; i < list.size(); i++) {
                    String aLeft = String.join("", list.get(i).toArray(new String[list.get(i).size()]));
                    for (int j = i + 1; j < list.size(); j++) {
                        String bLeft = String.join("", list.get(j).toArray(new String[list.get(j).size()]));
                        if (aLeft.equals("ε") || bLeft.equals("ε")) { // (1)若b＝ε,则要FIRST(A)∩FOLLOW(A)=φ
                            HashSet<String> retainSet = new HashSet<>();
                            // retainSet=FIRST.get(key);//需要要深拷贝，否则修改retainSet时FIRST同样会被修改
                            retainSet.addAll(FIRST.get(key));
                            if (FOLLOW.get(key) != null)
                                retainSet.retainAll(FOLLOW.get(key));
                            if (!retainSet.isEmpty()) {
                                flag = false;// 不是LL(1)文法，输出FIRST(a)FOLLOW(a)的交集
                                System.out.println("\tFIRST(" + key + ") ∩ FOLLOW(" + key + ") = {"
                                        + String.join("、", retainSet.toArray(new String[retainSet.size()])) + "}");
                                break;
                            } else {
                                System.out.println("\tFIRST(" + key + ") ∩ FOLLOW(" + key + ") = φ");
                            }
                        } else { // (2)b!＝ε若,则要FIRST(a)∩FIRST(b)= Ф
                            HashSet<String> retainSet = new HashSet<>();
                            retainSet.addAll(FIRST.get(key + "→" + aLeft));
                            retainSet.retainAll(FIRST.get(key + "→" + bLeft));
                            if (!retainSet.isEmpty()) {
                                flag = false;// 不是LL(1)文法，输出FIRST(a)FIRST(b)的交集
                                System.out.println("\tFIRST(" + aLeft + ") ∩ FIRST(" + bLeft + ") = {"
                                        + String.join("、", retainSet.toArray(new String[retainSet.size()])) + "}");
                                break;
                            } else {
                                System.out.println("\tFIRST(" + aLeft + ") ∩ FIRST(" + bLeft + ") = φ");
                            }
                        }
                    }
                }
        }
        if (flag)
            System.out.println("\t是LL(1)文法,继续分析!");
        else
            System.out.println("\t不是LL(1)文法,退出分析!");
        return flag;
    }


    // 构建预测分析表FORM
    private static void preForm() {
        HashSet<String> set = new HashSet<>();
        set.addAll(VT);
        set.remove("ε");
        FORM = new String[VN.size() + 1][set.size() + 2];
        Iterator<String> itVn = VN.iterator();
        Iterator<String> itVt = set.iterator();

        // (1)初始化FORM,并根据oneLeftFirst(VN$VT,产生式)填表
        for (int i = 0; i < FORM.length; i++)
            for (int j = 0; j < FORM[0].length; j++) {
                if (i == 0 && j > 0) {// 第一行为Vt
                    if (itVt.hasNext()) {
                        FORM[i][j] = itVt.next();
                    }
                    if (j == FORM[0].length - 1)// 最后一列加入#
                        FORM[i][j] = "#";
                }
                if (j == 0 && i > 0) {// 第一列为Vn
                    if (itVn.hasNext())
                        FORM[i][j] = itVn.next();
                }
                if (i > 0 && j > 0) {// 其他情况先根据oneLeftFirst填表
                    String oneLeftKey = FORM[i][0] + "$" + FORM[0][j];// 作为key查找其First集合
                    FORM[i][j] = oneLeftFirst.get(oneLeftKey);
                }
            }
        // (2)如果有推出了ε，则根据FOLLOW填表
        for (int i = 1; i < FORM.length; i++) {
            String oneLeftKey = FORM[i][0] + "$ε";
            if (oneLeftFirst.containsKey(oneLeftKey)) {
                HashSet<String> followCell = FOLLOW.get(FORM[i][0]);
                Iterator<String> it = followCell.iterator();
                while (it.hasNext()) {
                    String vt = it.next();
                    for (int j = 1; j < FORM.length; j++)
                        for (int k = 1; k < FORM[0].length; k++) {
                            if (FORM[j][0].equals(FORM[i][0]) && FORM[0][k].equals(vt))
                                FORM[j][k] = oneLeftFirst.get(oneLeftKey);
                        }
                }
            }
        }

        // 打印预测表,并存于Map的数据结构中用于快速查找
        System.out.println("\n该文法的预测分析表为：");
        for (int i = 0; i < FORM.length; i++) {
            for (int j = 0; j < FORM[0].length; j++) {
                if (FORM[i][j] == null)
                    System.out.print(" " + "\t");
                else {
                    System.out.print(FORM[i][j] + "\t");
                    if (i > 0 && j > 0) {
                        String[] tmp = FORM[i][j].split("→");
                        preMap.put(FORM[i][0] + "" + FORM[0][j], tmp[1]);
                    }
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    // 求每个非终结符号的FIRST集合 和 分解单个产生式的FIRST集合
    private static void findFirst() {
        System.out.println("\nFIRST集合:");
        Iterator<String> it = VN.iterator();
        while (it.hasNext()) {
            HashSet<String> firstCell = new HashSet<>();// 存放单个非终结符号的FIRST
            String key = it.next();
            ArrayList<ArrayList<String>> list = MAP.get(key);
            // System.out.println(key+":");
            // 遍历单个产生式的左边
            for (int i = 0; i < list.size(); i++) {
                ArrayList<String> listCell = list.get(i);// listCell为“|”分割出来
                HashSet<String> firstCellOne = new HashSet<>();// 产生式左边用“ | ”分割的单个式子的First(弃用)
                String oneLeft = String.join("", listCell.toArray(new String[listCell.size()]));
                // System.out.println("oneLeft: "+oneLeft);
                if (VT.contains(listCell.get(0))) {
                    firstCell.add(listCell.get(0));
                    firstCellOne.add(listCell.get(0));
                    oneLeftFirst.put(key + "$" + listCell.get(0), key + "→" + oneLeft);
                } else {
                    boolean[] isVn = new boolean[listCell.size()];// 标记是否有定义为空,如果有则检查下一个字符
                    isVn[0] = true;// 第一个为非终结符号
                    int p = 0;
                    while (isVn[p]) {
                        // System.out.println(p+" "+listCell.size());
                        if (VT.contains(listCell.get(p))) {
                            firstCell.add(listCell.get(p));
                            firstCellOne.add(listCell.get(p));
                            oneLeftFirst.put(key + "$" + listCell.get(p), key + "→" + oneLeft);
                            break;
                        }
                        String vnGo = listCell.get(p);//
                        Stack<String> stack = new Stack<>();
                        stack.push(vnGo);
                        while (!stack.isEmpty()) {
                            ArrayList<ArrayList<String>> listGo = MAP.get(stack.pop());
                            for (int k = 0; k < listGo.size(); k++) {
                                ArrayList<String> listGoCell = listGo.get(k);
                                if (VT.contains(listGoCell.get(0))) { // 如果第一个字符是终结符号
                                    if (listGoCell.get(0).equals("ε")) {
                                        if (!key.equals(START)) { // 开始符号不能推出空
                                            firstCell.add(listGoCell.get(0));
                                            firstCellOne.add(listGoCell.get(0));
                                            oneLeftFirst.put(key + "$" + listGoCell.get(0), key + "→" + oneLeft);
                                        }
                                        if (p + 1 < isVn.length) {// 如果为空，可以查询下一个字符
                                            isVn[p + 1] = true;
                                        }
                                    } else { // 非空的终结符号加入对应的FIRST集合
                                        firstCell.add(listGoCell.get(0));
                                        firstCellOne.add(listGoCell.get(0));
                                        oneLeftFirst.put(key + "$" + listGoCell.get(0), key + "→" + oneLeft);
                                    }
                                } else {// 不是终结符号，入栈
                                    stack.push(listGoCell.get(0));
                                }
                            }
                        }
                        p++;
                        if (p > isVn.length - 1)
                            break;
                    }
                }
                FIRST.put(key + "→" + oneLeft, firstCellOne);
            }
            FIRST.put(key, firstCell);
            // 输出key的FIRST集合
            System.out.println(
                    "\tFIRST(" + key + ")={" + String.join("、", firstCell.toArray(new String[firstCell.size()])) + "}");
        }
    }

    // 求每个非终结符号的FLLOW集合
    private static void findFollow() {
        System.out.println("\nFOLLOW集合:");
        Iterator<String> it = VN.iterator();
        HashMap<String, HashSet<String>> keyFollow = new HashMap<>();

        ArrayList<HashMap<String, String>> vn_VnList = new ArrayList<>();// 用于存放/A->...B 或者 A->...Bε的组合

        HashSet<String> vn_VnListLeft = new HashSet<>();// 存放vn_VnList的左边和右边
        HashSet<String> vn_VnListRight = new HashSet<>();
        // 开始符号加入#
        keyFollow.put(START, new HashSet<String>() {
            private static final long serialVersionUID = 1L;
            {
                add(new String("#"));
            }
        });

        while (it.hasNext()) {
            String key = it.next();
            ArrayList<ArrayList<String>> list = MAP.get(key);
            ArrayList<String> listCell;

            // 先把每个VN作为keyFollow的key，之后在查找添加其FOLLOW元素
            if (!keyFollow.containsKey(key)) {
                keyFollow.put(key, new HashSet<>());
            }
            keyFollow.toString();

            for (int i = 0; i < list.size(); i++) {
                listCell = list.get(i);

                // (1)直接找非总结符号后面跟着终结符号
                for (int j = 1; j < listCell.size(); j++) {
                    HashSet<String> set = new HashSet<>();
                    if (VT.contains(listCell.get(j))) {
                        // System.out.println(listCell.get(j - 1) + ":" + listCell.get(j));
                        set.add(listCell.get(j));
                        if (keyFollow.containsKey(listCell.get(j - 1)))
                            set.addAll(keyFollow.get(listCell.get(j - 1)));
                        keyFollow.put(listCell.get(j - 1), set);
                    }
                }
                // (2)找...VnVn...组合
                for (int j = 0; j < listCell.size() - 1; j++) {
                    HashSet<String> set = new HashSet<>();
                    if (VN.contains(listCell.get(j)) && VN.contains(listCell.get(j + 1))) {
                        set.addAll(FIRST.get(listCell.get(j + 1)));
                        set.remove("ε");

                        if (keyFollow.containsKey(listCell.get(j)))
                            set.addAll(keyFollow.get(listCell.get(j)));
                        keyFollow.put(listCell.get(j), set);
                    }
                }

                // (3)A->...B 或者 A->...Bε(可以有n个ε)的组合存起来
                for (int j = 0; j < listCell.size(); j++) {
                    HashMap<String, String> vn_Vn;
                    if (VN.contains(listCell.get(j)) && !listCell.get(j).equals(key)) {// 是VN且A不等于B
                        boolean isAllNull = false;// 标记VN后是否为空
                        if (j + 1 < listCell.size())// 即A->...Bε(可以有n个ε)
                            for (int k = j + 1; k < listCell.size(); k++) {
                                if ((FIRST.containsKey(listCell.get(k)) ? FIRST.get(listCell.get(k)).contains("ε")
                                        : false)) {// 如果其后面的都是VN且其FIRST中包含ε
                                    isAllNull = true;
                                } else {
                                    isAllNull = false;
                                    break;
                                }
                            }
                        // 如果是最后一个为VN,即A->...B
                        if (j == listCell.size() - 1) {
                            isAllNull = true;
                        }
                        if (isAllNull) {
                            vn_VnListLeft.add(key);
                            vn_VnListRight.add(listCell.get(j));

                            // 往vn_VnList中添加，分存在和不存在两种情况
                            boolean isHaveAdd = false;
                            for (int x = 0; x < vn_VnList.size(); x++) {
                                HashMap<String, String> vn_VnListCell = vn_VnList.get(x);
                                if (!vn_VnListCell.containsKey(key)) {
                                    vn_VnListCell.put(key, listCell.get(j));
                                    vn_VnList.set(x, vn_VnListCell);
                                    isHaveAdd = true;
                                    break;
                                } else {
                                    // 去重
                                    if (vn_VnListCell.get(key).equals(listCell.get(j))) {
                                        isHaveAdd = true;
                                        break;
                                    }
                                    continue;
                                }
                            }
                            if (!isHaveAdd) {// 如果没有添加，表示是新的组合
                                vn_Vn = new HashMap<>();
                                vn_Vn.put(key, listCell.get(j));
                                vn_VnList.add(vn_Vn);
                            }
                        }
                    }
                }
            }
        }

        keyFollow.toString();

        // (4)vn_VnListLeft减去vn_VnListRight,剩下的就是入口产生式，
        vn_VnListLeft.removeAll(vn_VnListRight);
        Queue<String> keyQueue = new LinkedList<>();// 用栈或者队列都行
        Iterator<String> itVnVn = vn_VnListLeft.iterator();
        while (itVnVn.hasNext()) {
            keyQueue.add(itVnVn.next());
        }
        while (!keyQueue.isEmpty()) {
            String keyLeft = keyQueue.poll();
            for (int t = 0; t < vn_VnList.size(); t++) {
                HashMap<String, String> vn_VnListCell = vn_VnList.get(t);
                if (vn_VnListCell.containsKey(keyLeft)) {
                    HashSet<String> set = new HashSet<>();
                    // 原来的FOLLOW加上左边的FOLLOW
                    if (keyFollow.containsKey(keyLeft))
                        set.addAll(keyFollow.get(keyLeft));
                    if (keyFollow.containsKey(vn_VnListCell.get(keyLeft)))
                        set.addAll(keyFollow.get(vn_VnListCell.get(keyLeft)));
                    keyFollow.put(vn_VnListCell.get(keyLeft), set);
                    keyQueue.add(vn_VnListCell.get(keyLeft));

                    // 移除已处理的组合
                    vn_VnListCell.remove(keyLeft);
                    vn_VnList.set(t, vn_VnListCell);
                }
            }
        }

        // 此时keyFollow为完整的FOLLOW集
        FOLLOW = keyFollow;
        // 打印FOLLOW集合
        Iterator<String> itF = keyFollow.keySet().iterator();
        while (itF.hasNext()) {
            String key = itF.next();
            HashSet<String> f = keyFollow.get(key);
            System.out.println("\tFOLLOW(" + key + ")={" + String.join("、", f.toArray(new String[f.size()])) + "}");
        }
    }

    // 输入的单词串分析推导过程
    public static void printAutoPre(String str) {
        System.out.println(str + "的分析过程:");
        Queue<String> queue = new LinkedList<>();// 句子拆分存于队列
        for (int i = 0; i < str.length(); i++) {
            String t = str.charAt(i) + "";
            if (i + 1 < str.length() && (str.charAt(i + 1) == '\'' || str.charAt(i + 1) == '’')) {
                t += str.charAt(i + 1);
                i++;
            }
            queue.offer(t);
        }
        queue.offer("#");// "#"结束
        // 分析栈
        Stack<String> stack = new Stack<>();
        stack.push("#");// "#"开始
        stack.push(START);// 初态为开始符号
        boolean isSuccess = false;
        int step = 1;
        while (!stack.isEmpty()) {
            String left = stack.peek();
            String right = queue.peek();
            // System.out.println(left+" "+right);
            // (1)分析成功
            if (left.equals(right) && right.equals("#")) {
                isSuccess = true;
                System.out.println((step++) + "\t#\t#\t" + "分析成功");
                break;
            }
            // (2)匹配栈顶和当前符号，均为终结符号，消去
            if (left.equals(right)) {
                String stackStr = String.join("", stack.toArray(new String[stack.size()]));
                String queueStr = String.join("", queue.toArray(new String[queue.size()]));
                System.out.println((step++) + "\t" + stackStr + "\t" + queueStr + "\t匹配成功" + left);
                stack.pop();
                queue.poll();
                continue;
            }
            // (3)从预测表中查询
            if (preMap.containsKey(left + right)) {
                String stackStr = String.join("", stack.toArray(new String[stack.size()]));
                String queueStr = String.join("", queue.toArray(new String[queue.size()]));
                System.out.println((step++) + "\t" + stackStr + "\t" + queueStr + "\t用" + left + "→"
                        + preMap.get(left + right) + "," + right + "逆序进栈");
                stack.pop();
                String tmp = preMap.get(left + right);
                for (int i = tmp.length() - 1; i >= 0; i--) {// 逆序进栈
                    String t = "";
                    if (tmp.charAt(i) == '\'' || tmp.charAt(i) == '’') {
                        t = tmp.charAt(i-1)+""+tmp.charAt(i);
                        i--;
                    }else {
                        t=tmp.charAt(i)+"";
                    }
                    if (!t.equals("ε"))
                        stack.push(t);
                }
                continue;
            }
            break;// (4)其他情况失败并退出
        }
        if (!isSuccess)
            System.out.println((step++) + "\t#\t#\t" + "分析失败");
    }
}