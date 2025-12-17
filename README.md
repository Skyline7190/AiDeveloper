# 项目二

### 步骤1：α-β剪枝

> 先了解MinMax算法，一种找出失败的最大可能性中的最小值的算法。**正方形来代表先手（选择估价最大的局面，即从所有子节点中选择最大的**），圆形来代表后手（选择估价最小的局面，**即从所有子节点中选择最小的**）。**只有叶子节点才可以直接计算估价值**。如果按照MinMax算法来进行决策的话，需要的计算量是随着向后看的步数的增加而呈指数级增长的。但是，这些状态中其实是包含很多不必要的状态的，所以我们可以进行剪枝。
>
> 进而引出Alpha–Beta 剪枝，Alpha-beta(α − β )剪枝的名称来自计算过程中传递的两个边界，这些边界基于已经看到的搜索树部分来限制可能的解决方案集。 Alpha(α )表示目前所有可能解中的最大下界，Beta(β )表示目前所有可能解中的最小上界，即α≤N≤β（N是当前节点的估价值）。如果对于某一个节点，出现了α > β的情况，那么，说明这个点一定不会产生最优解了，所以，我们就不再对其进行扩展（也就是不再生成子节点），这样就完成了对博弈树的剪枝。即，
>
> - 当一个 Min 节点的 β值≤任何一个父节点的α值时 ，剪掉该节点的所有子节点
> - 当一个 Max 节点的 α值≥任何一个父节点的β值时 ，剪掉该节点的所有子节点

不需要使用Ai平台，直接在Idea中用Java完成即可。数据文件tree.txt

下图中的树结构以结点三元组（结点序号，父节点序号，结点估值）的形式， 存放于tree.txt 文本文件中。

![](./asset/image.png)


1. 请编写一个程序，输出以下内容：
   第一行是搜索得到的走步及其估值，用三个空格分隔的数字表示。如1 3 1;
   表示从结点1到3的走步，其估值为1。
   后面若干行，代表剪掉的枝子。每行前面两个数字分别代表被剪掉的枝子的
   父结点和子结点，如果是α剪枝，则两个数字后面是字符串alpha，否则是beta。
   数字和字符串之间都用空格分隔。如
   5 6 alpha
   10 11 beta
   10 12 beta
   10 13 beta
   ……

```java
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class AlphaBetaPruning {

    // 定义节点类
    static class Node {
        int id;
        int parentId;
        int value; // 叶子节点的估值，非叶子节点初始为0
        List<Node> children = new ArrayList<>();

        public Node(int id, int parentId, int value) {
            this.id = id;
            this.parentId = parentId;
            this.value = value;
        }
    }

    // 用于存储剪枝记录的列表，格式：ParentID ChildID Type
    static List<String> prunedBranches = new ArrayList<>();

    // 全局Map方便查找节点
    static Map<Integer, Node> nodeMap = new HashMap<>();

    public static void main(String[] args) {
        // 1. 读取数据并构建树
        Node root = buildTree("tree.txt");

        if (root == null) {
            System.out.println("Error: Failed to load tree. Please check tree.txt format.");
            return;
        }

        // 2. 执行 Alpha-Beta 剪枝
        // 根节点是 MAX 节点 (Depth 0)，初始 alpha = -inf, beta = +inf
        Result result = alphaBeta(root, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, true);

        // 3. 输出结果
        // 输出第一行：最佳走步
        System.out.println(root.id + " " + result.bestMoveId + " " + result.value);

        // 输出剪枝信息
        for (String record : prunedBranches) {
            System.out.println(record);
        }
    }

    /**
     * Alpha-Beta 剪枝递归函数
     */
    static Result alphaBeta(Node node, int depth, int alpha, int beta, boolean isMax) {
        // 如果是叶子节点，直接返回其估值
        if (node.children.isEmpty()) {
            return new Result(node.value, -1);
        }

        int bestVal;
        int bestMoveId = -1;

        if (isMax) {
            bestVal = Integer.MIN_VALUE;
            // 遍历子节点
            for (int i = 0; i < node.children.size(); i++) {
                Node child = node.children.get(i);

                // 递归调用，下一层是 MIN
                Result childResult = alphaBeta(child, depth + 1, alpha, beta, false);
                int val = childResult.value;

                // 更新最大值
                if (val > bestVal) {
                    bestVal = val;
                    bestMoveId = child.id;
                }

                // 更新 Alpha
                alpha = Math.max(alpha, bestVal);

                // 剪枝检查：如果 alpha >= beta
                if (beta <= alpha) {
                    // 记录剪枝信息：只有当还有下一个兄弟节点未被访问时，才算作剪枝发生
                    if (i + 1 < node.children.size()) {
                        Node prunedChild = node.children.get(i + 1);
                        prunedBranches.add(node.id + " " + prunedChild.id + " beta");
                    }
                    break; // 停止遍历后续子节点
                }
            }
        } else {
            // MIN 节点逻辑
            bestVal = Integer.MAX_VALUE;
            for (int i = 0; i < node.children.size(); i++) {
                Node child = node.children.get(i);

                // 递归调用，下一层是 MAX
                Result childResult = alphaBeta(child, depth + 1, alpha, beta, true);
                int val = childResult.value;

                // 更新最小值
                if (val < bestVal) {
                    bestVal = val;
                    bestMoveId = child.id;
                }

                // 更新 Beta
                beta = Math.min(beta, bestVal);

                // 剪枝检查：如果 beta <= alpha
                if (beta <= alpha) {
                    // 记录剪枝信息
                    if (i + 1 < node.children.size()) {
                        Node prunedChild = node.children.get(i + 1);
                        prunedBranches.add(node.id + " " + prunedChild.id + " alpha");
                    }
                    break; // 停止遍历后续子节点
                }
            }
        }

        return new Result(bestVal, bestMoveId);
    }

    static class Result {
        int value;
        int bestMoveId;

        public Result(int value, int bestMoveId) {
            this.value = value;
            this.bestMoveId = bestMoveId;
        }
    }

    // 更加健壮的文件读取方法
    static Node buildTree(String filePath) {
        Node root = null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                // 去除BOM
                line = line.replace("\uFEFF", "").trim();

                if (line.isEmpty()) continue;

                // 智能分割：如果有Tab就按Tab分，否则按空格分
                String[] rawParts;
                if (line.contains("\t")) {
                    rawParts = line.split("\t");
                } else {
                    rawParts = line.split("\\s+");
                }

                List<String> validParts = new ArrayList<>();
                for (String p : rawParts) {
                    // 核心修复：使用正则 [^-\d] 替换掉所有非数字和非负号的字符
                    // 这会将 "1 0" 变成 "10"， " - 3 " 变成 "-3"
                    String cleanP = p.replaceAll("[^-\\d]", "");
                    if (!cleanP.isEmpty() && !cleanP.equals("-")) { // 防止只有负号或空
                        validParts.add(cleanP);
                    }
                }

                // 跳过标题行（判断第一个有效部分是否是数字）
                if (isHeader) {
                    if (validParts.isEmpty() || !isNumeric(validParts.get(0))) {
                        isHeader = false;
                        continue;
                    }
                    isHeader = false; // 如果第一行就是数字，那也标记header结束，开始处理
                }

                if (validParts.size() >= 3) {
                    try {
                        int id = Integer.parseInt(validParts.get(0));
                        int parentId = Integer.parseInt(validParts.get(1));
                        int value = Integer.parseInt(validParts.get(2));

                        Node node = new Node(id, parentId, value);
                        nodeMap.put(id, node);

                        if (parentId == -1) {
                            root = node;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Skipping invalid line (parse error): " + line);
                    }
                }
            }

            List<Node> allNodes = new ArrayList<>(nodeMap.values());
            Collections.sort(allNodes, Comparator.comparingInt(n -> n.id));

            for (Node node : allNodes) {
                if (node.parentId != -1) {
                    Node parent = nodeMap.get(node.parentId);
                    if (parent != null) {
                        parent.children.add(node);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return root;
    }

    // 辅助方法：检查字符串是否是纯数字
    static boolean isNumeric(String str) {
        return Pattern.matches("-?\\d+", str);
    }
}
```

输出结果

```text
1 3 1
9 21 alpha
5 11 beta
15 35 alpha
7 17 beta
```

### 随机棋手-V0

#### 配置项目


1. 解压缩AiDeveloper.zip，里面是一个Idea项目，已经设置好了对AI平台 的依赖，平台的源代码在另外一个压缩包AiFramework_src.zip中。同学们只需要 重新设置JDK为你电脑上的JDK，Java11以上即可。
2. 在src文件夹下，创建stud包，在该包下已经创建了三个小组（组号 77,88,99）所对应的包，在该包下创建相关的AI，这三个小组包下分别创建本步骤中所要求的三个随机棋手。同学们可以在此基础上，按照这样的结构，任意添加棋手AI，请添加你们组的一个AI，使用《项目2-博弈搜索.pdf》中步骤2的第③点中的方法创建随机棋手。
3. AITester类是用来帮助同学们完成实验的，程序的输出可作为实验分析的数据。

#### 需求任务

使用博弈机器人开发平台，开发以下三个不同随机策略的机器人。
走法 1: 两个子的位置均通过随机掷骰子的方式确定，在整个棋盘（19 * 19） 范围内掷骰子。
走法 2: 通过随机掷骰子确定第一个子的位置，第二个子下在与第一个子相邻的空位上，和第一个子相邻的空位最多有8个，要求随机选择一个空位落子， 若相邻的8个位置都有子，则在整个棋盘上随机选择一个空位落子。
走法 3: 两个子的位置均通过随机掷骰子的方式确定，在棋盘的中心区域 （13 * 13）随机掷骰子，若连续 10 次不中（没有找到空位）， 则在整个棋盘（19 * 19） 范围内掷骰子。
要求：
① 请预估走法 2 和走法 3 哪种走法的胜率更高。
② 编程实现走法 2 和走 3 分先（轮换先后手）各比 500 场，共1000场。根据比赛结果检验你的预估是否正确。
③ 走法2和走法3可以采用先将下棋范围内的所有位置打散 （洗牌），然后一个一个取出作为落子位置，请分析这种走法的胜率会不会提高， 算法的时间复杂度是否会降低。请用这种方法创建你们组的随机棋手。
④将上述结果及其分析，写入项目二的研发报告中。

#### 解决问题

**①** **预估结论：** **走法 2 (相邻落子)** 的胜率会明显高于 **走法 3 (中心区域)**。
**分析原因：** 六子棋的核心在于“连”和“断”。
**走法 2** 强制将两手棋下在一起（相邻），这能直接形成连通结构（如连2），大大增加了后续形成连4、连5的概率。在随机对抗中，拥有更多连通棋子的一方更容易凑齐6子。
**走法 3** 虽然占据了棋盘的战略高地（中心区域），但如果两手棋是分别随机落在中心区域的不同位置，它们很可能是分散的。分散的棋子在没有任何战术指导（如搜索算法）的情况下，很难形成合力。
因此，**结构优势（相邻） > 区域优势（中心）**。

**②** 先编程实现

走法2：**Strategy2-Adjacency (相邻策略)**

```java
package stud.gStrategy2;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AI extends core.player.AI {
    @Override
    public void playGame(Game game) {
        super.playGame(game);
        // 关键修复：每局开始前必须重置棋盘，否则会沿用上一局的棋盘状态导致死循环
        this.board = new Board();
    }
    @Override
    public Move findNextMove(Move opponentMove) {
        // 修复 1: 增加空指针检查，防止作为先手时第一回合崩溃
        if (opponentMove != null) {
            this.board.makeMove(opponentMove);
        }

        Random rand = new Random();
        int boardSize = 19;
        int totalSpots = boardSize * boardSize;

        // 检查是否是全盘第一手棋 (棋盘为空)
        boolean isFirstMove = (countPieces() == 0);

        // --- 第一步：确定第一个子 ---
        int index1;
        while (true) {
            index1 = rand.nextInt(totalSpots);
            if (this.board.get(index1) == PieceColor.EMPTY) {
                break;
            }
        }

        // 修复 2: 如果是开局第一手，只落一子 (通过将 index2 设为 index1 或 -1，视具体引擎实现而定)
        // 这里采用两个相同坐标，许多引擎会将其视为落一子
        if (isFirstMove) {
            Move move = new Move(index1, index1);
            this.board.makeMove(move);
            return move;
        }

        // --- 第二步：确定第二个子 (正常逻辑) ---
        int index2 = -1;
        List<Integer> emptyNeighbors = getEmptyNeighbors(index1);

        if (!emptyNeighbors.isEmpty()) {
            index2 = emptyNeighbors.get(rand.nextInt(emptyNeighbors.size()));
        } else {
            while (true) {
                index2 = rand.nextInt(totalSpots);
                if (index2 != index1 && this.board.get(index2) == PieceColor.EMPTY) {
                    break;
                }
            }
        }

        Move move = new Move(index1, index2);
        this.board.makeMove(move);
        return move;
    }

    // 辅助方法：统计棋盘棋子数
    private int countPieces() {
        int count = 0;
        for (int i = 0; i < 361; i++) {
            if (this.board.get(i) != PieceColor.EMPTY) count++;
        }
        return count;
    }

    private List<Integer> getEmptyNeighbors(int centerIndex) {
        List<Integer> neighbors = new ArrayList<>();
        int r = centerIndex / 19;
        int c = centerIndex % 19;
        for (int i = r - 1; i <= r + 1; i++) {
            for (int j = c - 1; j <= c + 1; j++) {
                if (i >= 0 && i < 19 && j >= 0 && j < 19) {
                    int neighborIndex = i * 19 + j;
                    if (neighborIndex != centerIndex && this.board.get(neighborIndex) == PieceColor.EMPTY) {
                        neighbors.add(neighborIndex);
                    }
                }
            }
        }
        return neighbors;
    }

    @Override
    public String name() {
        return "Strategy2-Adjacency";
    }
}
```

走法3：**Strategy3-Center (中心策略)**

```java
package stud.gStrategy3;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.Random;

public class AI extends core.player.AI {
    @Override
    public void playGame(Game game) {
        super.playGame(game);
        // 关键修复：每局开始前必须重置棋盘，否则会沿用上一局的棋盘状态导致死循环
        this.board = new Board();
    }
    @Override
    public Move findNextMove(Move opponentMove) {
        // 修复 1: 空指针检查
        if (opponentMove != null) {
            this.board.makeMove(opponentMove);
        }

        Random rand = new Random();
        boolean isFirstMove = (countPieces() == 0);

        int index1 = -1;
        int index2 = -1;
        boolean foundInCenter = false;

        // --- 逻辑部分 ---
        // 尝试在中心区域
        for (int i = 0; i < 10; i++) {
            int temp1 = getRandomCenterIndex(rand);
            int temp2 = getRandomCenterIndex(rand);

            // 如果是第一手，只需要 temp1 有效即可
            if (isFirstMove) {
                if (this.board.get(temp1) == PieceColor.EMPTY) {
                    index1 = temp1;
                    index2 = temp1; // 落一子
                    foundInCenter = true;
                    break;
                }
            } else {
                if (temp1 != temp2 && this.board.get(temp1) == PieceColor.EMPTY && this.board.get(temp2) == PieceColor.EMPTY) {
                    index1 = temp1;
                    index2 = temp2;
                    foundInCenter = true;
                    break;
                }
            }
        }

        // 如果中心没找到，全盘随机
        if (!foundInCenter) {
            while (true) {
                int t1 = rand.nextInt(361);
                // 第一手特殊处理
                if (isFirstMove) {
                    if (this.board.get(t1) == PieceColor.EMPTY) {
                        index1 = t1;
                        index2 = t1;
                        break;
                    }
                } else {
                    int t2 = rand.nextInt(361);
                    if (t1 != t2 && this.board.get(t1) == PieceColor.EMPTY && this.board.get(t2) == PieceColor.EMPTY) {
                        index1 = t1;
                        index2 = t2;
                        break;
                    }
                }
            }
        }

        Move move = new Move(index1, index2);
        this.board.makeMove(move);
        return move;
    }

    private int getRandomCenterIndex(Random rand) {
        int r = rand.nextInt(13) + 3;
        int c = rand.nextInt(13) + 3;
        return r * 19 + c;
    }

    private int countPieces() {
        int count = 0;
        for (int i = 0; i < 361; i++) {
            if (this.board.get(i) != PieceColor.EMPTY) count++;
        }
        return count;
    }

    @Override
    public String name() {
        return "Strategy3-Center";
    }
}
```

比赛结果：

```bash
=========================================================================
	Game Statistics (Strategy3-Center vs Strategy2-Adjacency):  1684
		先手：	win:   410, lose:    50, draw:    40, 得分:   860
		后手：	win:   382, lose:    58, draw:    60, 得分:   824
		合计：	win:   792, lose:   108, draw:   100, 得分:  1684
----------------------------------------------------------------------
	 Strategy3-Center总计：	win:   792, lose:   108, draw:   100, 得分:  1684

=========================================================================
	Game Statistics (Strategy2-Adjacency vs Strategy3-Center):   316
		先手：	win:    58, lose:   382, draw:    60, 得分:   176
		后手：	win:    50, lose:   410, draw:    40, 得分:   140
		合计：	win:   108, lose:   792, draw:   100, 得分:   316
----------------------------------------------------------------------
	 Strategy2-Adjacency总计：	win:   108, lose:   792, draw:   100, 得分:   316

================================棋手总战绩================================
	 Strategy3-Center总计：	win:   792, lose:   108, draw:   100, 得分:  1684
	 Strategy2-Adjacency总计：	win:   108, lose:   792, draw:   100, 得分:   316

===========棋手得分明细 (WSUM: 先手得分，BSUM：后手得分，TSUM：棋手的总得分)===========
         Strategy3-Center, Strategy2-Adjacency, |WSUM
	--------------------------
	Strategy3-Center|     ,   860, |  860
	Strategy2-Adjacency|  176,      , |  176
	BSUM|  824,   140, |   -
	TSUM| 1684,   316, |   -
0.7188
Process finished with exit code 0
```

**对阵双方**：Strategy 3 (中心优先) vs Strategy 2 (相邻优先)
**总场次**：1000 场 (分先各 500 场)

|统计项 |Strategy 3 (Center) |Strategy 2 (Adjacent) |和棋 (Draw) |
|---|---|---|---|
|**获胜场次** |**792** |108 |100 |
|**胜率** |**79.2%** |10.8% |10.0% |
|**总得分** |**1684** |316 |- |

分析结果：数据显示 Strategy 3 (中心策略) 取得了压倒性的胜利（792胜 vs 108胜），这与我最初的理论预估（认为相邻策略 Strategy 2 会赢）是截然相反的。剖析原因如下，
**Strategy 3**：强制将落子范围限制在棋盘中心的 13  13 区域。虽然落子是随机的，但由于**总面积小**，棋子的**分布密度极高**。在随机乱撞的过程中，高密度的棋子更容易无意中“凑”成 4 子、5 子甚至 6 子。
**Strategy 2**：虽然它试图通过“下在旁边”来制造结构，但如果没有中心限制，它的棋子会随着空位的减少而散布到整个 19 * 19 大棋盘的边缘和角落。这些**低密度、分散**的“小对子”在广阔的棋盘上孤掌难鸣，无法形成有效的连胜势力。

**③胜率不会提高**。洗牌算法本质上只是实现了“不放回的随机抽取”。从概率分布上看，它和“随机掷骰子直到找到空位”是等价的（都是均匀分布）。只要随机策略的逻辑不变，胜率就不会变。
**时间复杂度是会降低**。传统方法 (While Loop): 随着棋盘变满，空位越来越少，随机掷骰子命中“已占有”位置的概率变大，导致 while 循环次数指数级上升（冲突增多）。洗牌方法 (Shuffle): 维护一个“空闲位置列表”。每次落子只需 $O(1)$ 的时间从列表中取出一个位置，并将其移除（或交换到列表末尾）。无论棋盘多满，获取下一个随机空位的时间都是恒定的。
**实验数据：**

```bash
================================棋手总战绩================================
	 Strategy3-Center总计：	win:  2460, lose:   158, draw:   382, 得分:  5302
	 Strategy2-Adjacency总计：	win:   640, lose:   910, draw:  1450, 得分:  2730
	 Group-Shuffle总计：	win:   170, lose:  1159, draw:  1671, 得分:  2011
	 Strategy1-Random总计：	win:   140, lose:  1183, draw:  1677, 得分:  1957
```

两者极为接近，且产生了大量的和棋。这证明了仅仅改变随机数的生成方式**并不会提高 AI 的棋力**。
而**时间复杂度**显然通过代码逻辑分析就能判断。普通随机随着棋盘棋子增加，空位减少，随机掷骰子“命中”已占有位置的概率越来越大。在棋盘接近全满时，为了找到一个空位，可能需要循环尝试成百上千次。最坏时间复杂度趋近于 O(N)。而洗牌算法 (Shuffle)：初始化$O(N)$ (仅在开局执行一次)。每次只需从列表中取出一个元素，操作为 $O(1)$。O(1)vs O(N)显然降低。