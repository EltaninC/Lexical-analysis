package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

public class Parser {
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
        if (choice>-1&&choice<=num){
            choice = choice -1;
            identifyVnVt(grammar.get(choice));// 符号分类,并以key-value形式存于MAP中
            try {
                reformMap();// 消除左递归和提取左公因子
            }catch (Exception e){
                System.out.println("无法自上而下");
                return;
            }

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
        System.out.println("文件输入内容如下:");
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

    /**
     * 对给定的文法进行符号分类，将符号分为非终结符集合（Vn）和终结符集合（Vt）。
     * @param list 包含文法规则的字符串列表，每个规则格式为"非终结符 → 终结符组合"。
     */
    private static void identifyVnVt(ArrayList<String> list) {
        // 初始化开始符号
        START = list.get(0).charAt(0) + "";

        // 遍历文法规则进行符号分类
        for (int i = 0; i < list.size(); i++) {
            String oneline = list.get(i);
            String[] vnvt = oneline.split("→"); // 通过"→"分割规则的左右两边
            String left = vnvt[0].trim(); // 获取左边的非终结符
            VN.add(left);
            autoIncr.put(left, left); // 为非终结符创建唯一标识

            // 处理右边的终结符组合
            ArrayList<ArrayList<String>> mapValue = new ArrayList<>();
            ArrayList<String> right = new ArrayList<>();

            for (int j = 0; j < vnvt[1].length(); j++) { // 遍历右边部分，按"|"分割终结符
                if (vnvt[1].charAt(j) == '|') {
                    VT.addAll(right);
                    mapValue.add(right);
                    right = new ArrayList<>(); // 重置以处理下一个终结符组合
                    continue;
                }
                right.add(vnvt[1].charAt(j) + "");
            }
            VT.addAll(right);
            mapValue.add(right);

            // 将非终结符和对应的终结符组合映射关系存入MAP
            MAP.put(left, mapValue);
        }

        // 从终结符集合中移除非终结符
        VT.removeAll(VN);

        // 打印非终结符集合和终结符集合
        System.out.println("\nVn集合:\t{" + String.join("、", VN.toArray(new String[VN.size()])) + "}");
        System.out.println("Vt集合:\t{" + String.join("、", VT.toArray(new String[VT.size()])) + "}");
    }



    private static void reformMap()  throws Exception{
        // 消除直接左递归和隐式左递归
        leftRecursion();
        YleftRecursion();
        // 提取左公因子
        leftFactor();

        // 输出修改后的文法
        System.out.println("消除文法的左递归,左因子:");
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
    private static void leftRecursion() throws Exception{
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
                }else {
                    if(!rightList.get(i).get(0).equals("ε")) {
                        oldRightCell.addAll(rightList.get(i));
                    }
                    oldRightCell.add(autoIncr.get(left) + "\'");
                    oldRightOld.add(oldRightCell);
                }
            }
            if (flag) {// 如果有左递归，则更新MAP
                if(oldRightOld.isEmpty()) throw new Exception();
                newLeftNew.add(nullSign);
                autoIncr.put(left, autoIncr.get(left) + "\'");// 自动加'的map,保证符号唯一性
                MAP.put(autoIncr.get(left), newLeftNew);
                VN.add(autoIncr.get(left)); // 加入新的VN
                VT.add("ε"); // 加入ε到VT
                MAP.put(left, oldRightOld);
            }
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
                    VT.add("ε"); // 加入ε到VT
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


    /**
     * 寻找两个字符串列表中共同的前缀字符串。
     *
     * @param str1 第一个字符串列表，用于比较共同前缀。
     * @param str2 第二个字符串列表，用于比较共同前缀。
     * @return 包含两个列表共同前缀的字符串列表。如果没有共同前缀，则返回空列表。
     */
    public static ArrayList<String> commonPrefix(ArrayList<String> str1, ArrayList<String> str2) {
        // 初始化索引i为0
        int i = 0;
        // 遍历两个列表，直到遇到不相等的字符或其中一个列表的末尾
        for (; i < str1.size() && i < str2.size(); i++) {
            // 如果当前位置的字符串不相等，则停止遍历
            if (!str1.get(i).equals(str2.get(i))){
                break;
            }
        }
        // 返回从列表开始到索引i的子列表，即为共同前缀
        return new ArrayList(str1.subList(0, i)) ;
    }


    /**
     * 判断给定的文法是否是LL(1)文法。
     * LL(1)文法是指左至右的扫描（Left-to-right scanning）和左most的 derivations（Leftmost derivation），
     * 同时使用一个Lookahead（1个符号）的解析方法。
     *
     * @return boolean 如果文法是LL(1)文法则返回true，否则返回false。
     */
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
                        if (aLeft.equals("ε") || bLeft.equals("ε")) { // 情况1：若b＝ε,则要FIRST(A)∩FOLLOW(A)=φ
                            HashSet<String> retainSet = new HashSet<>();
                            // retainSet=FIRST.get(key);//需要要深拷贝，否则修改retainSet时FIRST同样会被修改
                            retainSet.addAll(FIRST.get(key));
                            if (FOLLOW.get(key) != null)
                                retainSet.retainAll(FOLLOW.get(key));
                            if (!retainSet.isEmpty()) {
                                flag = false;// 不是LL(1)文法，输出FIRST(a)FOLLOW(a)的交集
//                                System.out.println("\tFIRST(" + key + ") ∩ FOLLOW(" + key + ") = {"
//                                        + String.join("、", retainSet.toArray(new String[retainSet.size()])) + "}");
                                break;
                            } else {
//                                System.out.println("\tFIRST(" + key + ") ∩ FOLLOW(" + key + ") = φ");
                            }
                        } else { // 情况2：b!＝ε若,则要FIRST(a)∩FIRST(b)= Ф
//                            HashSet<String> retainSet = new HashSet<>();
//                            retainSet.addAll(FIRST.get(key + "→" + aLeft));
//                            retainSet.retainAll(FIRST.get(key + "→" + bLeft));
//                            if (!retainSet.isEmpty()) {
//                                flag = false;// 不是LL(1)文法，输出FIRST(a)FIRST(b)的交集
//                                System.out.println("\tFIRST(" + aLeft + ") ∩ FIRST(" + bLeft + ") = {"
//                                        + String.join("、", retainSet.toArray(new String[retainSet.size()])) + "}");
//                                break;
//                            } else {
//                                System.out.println("\tFIRST(" + aLeft + ") ∩ FIRST(" + bLeft + ") = φ");
//                            }
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


    /**
     * 构建预测分析表FORM
     * 此方法不接受参数，也不返回任何值。
     * 它通过遍历和计算，动态地构建一个用于分析语法的预测表，并将结果打印出来。
     * 同时，将构建的预测表以特定格式存储在预定义的Map中，以便于后续快速查找。
     */
    private static void preForm() {
        HashSet<String> set = new HashSet<>();
        set.addAll(VT);
        set.remove("ε");  // 从VT中移除ε，因为ε不能作为预测分析表中的元素

        // 初始化FORM表，其大小根据VN和VT的大小动态确定
        FORM = new String[VN.size() + 1][set.size() + 2];
        Iterator<String> itVn = VN.iterator();
        Iterator<String> itVt = set.iterator();

        // 初始化FORM表的第一行和第一列，以及根据oneLeftFirst函数填入其他元素
        for (int i = 0; i < FORM.length; i++)
            for (int j = 0; j < FORM[0].length; j++) {
                if (i == 0 && j > 0) { // 第一行为Vt，除去ε
                    if (itVt.hasNext()) {
                        FORM[i][j] = itVt.next();
                    }
                    if (j == FORM[0].length - 1) // 最后一列加入符号#
                        FORM[i][j] = "#";
                }
                if (j == 0 && i > 0) { // 第一列为Vn
                    if (itVn.hasNext())
                        FORM[i][j] = itVn.next();
                }
                if (i > 0 && j > 0) { // 根据oneLeftFirst函数的结果填表
                    String oneLeftKey = FORM[i][0] + "$" + FORM[0][j];
                    FORM[i][j] = oneLeftFirst.get(oneLeftKey);
                }
            }

        // 处理产生了ε的情况，根据FOLLOW集合进一步填充FORM表
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

        // 打印并存储预测分析表
        System.out.println("\n该文法的预测分析表为：");
        for (int i = 0; i < FORM.length; i++) {
            for (int j = 0; j < FORM[0].length; j++) {
                if (FORM[i][j] == null)
                    System.out.print(" " + "\t");
                else {
                    System.out.print(FORM[i][j] + "\t");
                    if (i > 0 && j > 0) { // 仅对非初始行和列的元素存储到preMap中
                        String[] tmp = FORM[i][j].split("→");
                        preMap.put(FORM[i][0] + "" + FORM[0][j], tmp[1]);
                    }
                }
            }
            System.out.println();
        }
        System.out.println();
    }


    /**
     * 计算每个非终结符号的FIRST集合以及分解单个产生式的FIRST集合。
     * 该过程不接受参数，也不返回值，但会更新全局数据结构FIRST和oneLeftFirst。
     */
    private static void findFirst() {
        System.out.println("\nFIRST集合:");
        Iterator<String> it = VN.iterator(); // 遍历非终结符号集合VN
        while (it.hasNext()) {
            HashSet<String> firstCell = new HashSet<>(); // 用于存放当前非终结符号的FIRST集合
            String key = it.next(); // 当前非终结符号
            ArrayList<ArrayList<String>> list = MAP.get(key); // 获取当前非终结符号的所有产生式

            // 遍历单个产生式的左边
            for (int i = 0; i < list.size(); i++) {
                ArrayList<String> listCell = list.get(i); // 一个产生式的右边部分
                HashSet<String> firstCellOne = new HashSet<>(); // 用于存放当前产生式左边部分的FIRST集合（单个）
                String oneLeft = String.join("", listCell.toArray(new String[listCell.size()])); // 将产生式左边的字符串连接起来

                // 判断产生式右边起始符号是否为终结符号
                if (VT.contains(listCell.get(0))) {
                    firstCell.add(listCell.get(0));
                    firstCellOne.add(listCell.get(0));
                    oneLeftFirst.put(key + "$" + listCell.get(0), key + "→" + oneLeft);
                } else {
                    boolean[] isVn = new boolean[listCell.size()]; // 标记非终结符号位置
                    isVn[0] = true; // 第一个元素默认为非终结符号
                    int p = 0;
                    while (isVn[p]) {
                        if (VT.contains(listCell.get(p))) {
                            firstCell.add(listCell.get(p));
                            firstCellOne.add(listCell.get(p));
                            oneLeftFirst.put(key + "$" + listCell.get(p), key + "→" + oneLeft);
                            break;
                        }
                        // 对非终结符号进行递归分解
                        String vnGo = listCell.get(p);
                        Stack<String> stack = new Stack<>();
                        stack.push(vnGo);
                        while (!stack.isEmpty()) {
                            ArrayList<ArrayList<String>> listGo = MAP.get(stack.pop());
                            for (int k = 0; k < listGo.size(); k++) {
                                ArrayList<String> listGoCell = listGo.get(k);
                                if (VT.contains(listGoCell.get(0))) {
                                    if (listGoCell.get(0).equals("ε")) {
                                        if (!key.equals(START)) { // 避免开始符号推出空
                                            firstCell.add(listGoCell.get(0));
                                            firstCellOne.add(listGoCell.get(0));
                                            oneLeftFirst.put(key + "$" + listGoCell.get(0), key + "→" + oneLeft);
                                        }
                                        if (p + 1 < isVn.length) {
                                            isVn[p + 1] = true;
                                        }
                                    } else {
                                        firstCell.add(listGoCell.get(0));
                                        firstCellOne.add(listGoCell.get(0));
                                        oneLeftFirst.put(key + "$" + listGoCell.get(0), key + "→" + oneLeft);
                                    }
                                } else {
                                    stack.push(listGoCell.get(0)); // 将非终结符号入栈继续分解
                                }
                            }
                        }
                        p++;
                        if (p > isVn.length - 1)
                            break;
                    }
                }
                FIRST.put(key + "→" + oneLeft, firstCellOne); // 存储每个产生式的FIRST集合
            }
            FIRST.put(key, firstCell); // 存储非终结符号的FIRST集合
            // 输出当前非终结符号的FIRST集合
            System.out.println(
                    "\tFIRST(" + key + ")={" + String.join("、", firstCell.toArray(new String[firstCell.size()])) + "}");
        }
    }

    /**
     * 计算每个非终结符号的FOLLOW集合。
     * FOLLOW集合是文法解析过程中用于确定语法分析树构建的重要依据之一，
     * 它包含了在某个非终结符号后面可能跟的所有终结符号和非终结符号。
     */
    private static void findFollow() {
        System.out.println("\nFOLLOW集合:");
        Iterator<String> it = VN.iterator();
        HashMap<String, HashSet<String>> keyFollow = new HashMap<>(); // 用于存储非终结符号和其对应的FOLLOW集合

        ArrayList<HashMap<String, String>> vn_VnList = new ArrayList<>(); // 用于存放/A->...B 或者 A->...Bε的组合

        HashSet<String> vn_VnListLeft = new HashSet<>(); // 存放vn_VnList的左边非终结符号
        HashSet<String> vn_VnListRight = new HashSet<>(); // 存放vn_VnList的右边非终结符号

        // 将开始符号加入FOLLOW集合，以"#"为标识
        keyFollow.put(START, new HashSet<String>() {
            private static final long serialVersionUID = 1L;
            {
                add(new String("#"));
            }
        });

        while (it.hasNext()) {
            String key = it.next();
            ArrayList<ArrayList<String>> list = MAP.get(key); // 获取与当前非终结符号相关联的产生式规则列表
            ArrayList<String> listCell;

            // 初始化当前非终结符号的FOLLOW集合
            if (!keyFollow.containsKey(key)) {
                keyFollow.put(key, new HashSet<>());
            }

            for (int i = 0; i < list.size(); i++) {
                listCell = list.get(i);

                // 计算每个非终结符号的FOLLOW集合
                // 1. 直接找非终结符号后面跟着的终结符号
                for (int j = 1; j < listCell.size(); j++) {
                    HashSet<String> set = new HashSet<>();
                    if (VT.contains(listCell.get(j)) && VN.contains(listCell.get(j - 1))) { // 如果当前符号是终结符号
                        set.add(listCell.get(j));
                        if (keyFollow.containsKey(listCell.get(j - 1)))
                            set.addAll(keyFollow.get(listCell.get(j - 1)));
                        keyFollow.put(listCell.get(j - 1), set);
                    }
                }
                // 2. 找...VnVn...组合
                for (int j = 0; j < listCell.size() - 1; j++) {
                    HashSet<String> set = new HashSet<>();
                    if (VN.contains(listCell.get(j)) && VN.contains(listCell.get(j + 1))) { // 如果当前符号和下一个符号都是非终结符号
                        set.addAll(FIRST.get(listCell.get(j + 1))); // 获取下一个非终结符号的FIRST集合
                        set.remove("ε"); // 移除ε

                        if (keyFollow.containsKey(listCell.get(j)))
                            set.addAll(keyFollow.get(listCell.get(j))); // 将当前非终结符号的FOLLOW集合合并
                        keyFollow.put(listCell.get(j), set);
                    }
                }
                // 3. 存储A->...B 或者 A->...Bε(可以有n个ε)的组合
                for (int j = 0; j < listCell.size(); j++) {
                    HashMap<String, String> vn_Vn;
                    if (VN.contains(listCell.get(j)) && !listCell.get(j).equals(key)) { // 如果当前符号是VN且不等于产生式的左边非终结符号
                        boolean isAllNull = false; // 标记VN后是否为空
                        if (j + 1 < listCell.size()) // 即A->...Bε(可以有n个ε)
                            for (int k = j + 1; k < listCell.size(); k++) {
                                if ((FIRST.containsKey(listCell.get(k)) ? FIRST.get(listCell.get(k)).contains("ε") : false)) { // 如果其后面的都是VN且其FIRST中包含ε
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
                            vn_VnListLeft.add(key); // 存储左边非终结符号
                            vn_VnListRight.add(listCell.get(j)); // 存储右边非终结符号

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
                            if (!isHaveAdd) { // 如果没有添加，表示是新的组合
                                vn_Vn = new HashMap<>();
                                vn_Vn.put(key, listCell.get(j));
                                vn_VnList.add(vn_Vn);
                            }
                        }
                    }
                }
            }
        }

        // 处理vn_VnListLeft和vn_VnListRight，计算额外的FOLLOW集合
        vn_VnListLeft.removeAll(vn_VnListRight);
        Queue<String> keyQueue = new LinkedList<>(); // 使用队列进行迭代处理
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
                    // 原来的FOLLOW集合加上左边非终结符号的FOLLOW集合
                    if (keyFollow.containsKey(keyLeft))
                        set.addAll(keyFollow.get(keyLeft));
                    if (keyFollow.containsKey(vn_VnListCell.get(keyLeft)))
                        set.addAll(keyFollow.get(vn_VnListCell.get(keyLeft)));
                    keyFollow.put(vn_VnListCell.get(keyLeft), set);
                    keyQueue.add(vn_VnListCell.get(keyLeft)); // 将右边的非终结符号加入队列进行继续处理

                    // 移除已处理的组合
                    vn_VnListCell.remove(keyLeft);
                    vn_VnList.set(t, vn_VnListCell);
                }
            }
        }

        // 此时keyFollow为完整的FOLLOW集
        FOLLOW = keyFollow;
        // 打印FOLLOW集合结果
        Iterator<String> itF = keyFollow.keySet().iterator();
        while (itF.hasNext()) {
            String key = itF.next();
            HashSet<String> f = keyFollow.get(key);
            System.out.println("\tFOLLOW(" + key + ")={" + String.join("、", f.toArray(new String[f.size()])) + "}");
        }
    }

    /**
     * 对输入的单词串进行分析推导过程的打印。
     * @param str 待分析的单词串。
     */
    public static void printAutoPre(String str) {
        // 打印开始标志和句子拆分
        System.out.println(str + "的分析过程:");
        Queue<String> queue = new LinkedList<>();// 句子拆分存于队列
        for (int i = 0; i < str.length(); i++) {
            String t = str.charAt(i) + "";
            // 处理单词串中的单引号或撇号
            while(i + 1 < str.length() && str.charAt(i) == '\''){
                t+="'";
                i++;
            }
            queue.offer(t);
        }
        queue.offer("#");// 添加结束标志"#"

        // 初始化分析栈
        Stack<String> stack = new Stack<>();
        stack.push("#");// 栈顶开始标志
        stack.push(START);// 初态为开始符号
        boolean isSuccess = false;
        int step = 1;
        // 开始分析过程
        while (!stack.isEmpty()) {
            String left = stack.peek();
            String right = queue.peek();

            // 分析成功的情况
            if (left.equals(right) && right.equals("#")) {
                isSuccess = true;
                System.out.println((step++) + "\t#\t#\t" + "分析成功");
                break;
            }

            // 匹配栈顶和当前符号，均为终结符号，进行消去
            if (left.equals(right)) {
                String stackStr = String.join("", stack.toArray(new String[stack.size()]));
                String queueStr = String.join("", queue.toArray(new String[queue.size()]));
                System.out.println((step++) + "\t" + stackStr + "\t" + queueStr + "\t匹配成功" + left);
                stack.pop();
                queue.poll();
                continue;
            }

            // 从预测表中查询分析动作
            if (preMap.containsKey(left + right)) {
                String stackStr = String.join("", stack.toArray(new String[stack.size()]));
                String queueStr = String.join("", queue.toArray(new String[queue.size()]));
                System.out.println((step++) + "\t" + stackStr + "\t" + queueStr + "\t用" + left + "→"
                        + preMap.get(left + right) + "," + right + "逆序进栈");
                stack.pop();
                // 逆序进栈处理
                String tmp = preMap.get(left + right);
                for (int i = tmp.length() - 1; i >= 0; i--) {
                    String t = "";
                    // 处理逆序进栈中的撇号
                    if (tmp.charAt(i) == '\'') {
                        String s = "";
                        while(tmp.charAt(i) == '\''){
                            i--;
                            s+="'";
                        }
                        t = tmp.charAt(i)+s;
                    } else {
                        t = tmp.charAt(i) + "";
                    }
                    if (!t.equals("ε"))
                        stack.push(t);
                }
                continue;
            }
            break;// 其他情况分析失败并退出
        }
        // 打印最终分析结果
        if (!isSuccess)
            System.out.println((step++) + "\t#\t#\t" + "分析失败");
    }
}