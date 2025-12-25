package stud.g99;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * G09 AI Optimized for Connect6
 * 策略：Alpha-Beta 搜索 + 精确棋形评估 + 威胁剪枝
 */
public class AI extends core.player.AI {

    private static final int WIDTH = 19;
    private static final int SIZE = 361;

    // 棋子表示
    private static final int EMPTY = 0;
    private static final int BLACK = 1;
    private static final int WHITE = 2;
    private static final int BORDER = 3; // 用于边界判定

    // 评分权重 (Connect6 特有)
    // 赢棋
    private static final int SCORE_WIN = 10_000_000;
    // 活五 (下两子必成六) 或 冲五 (下一子成六) -> 极高威胁
    private static final int SCORE_FIVE = 1_000_000;
    // 活四 (_XXXX_) -> 下一手加两子成六，必胜
    private static final int SCORE_LIVE_4 = 500_000;
    // 冲四 (OXXXX_) -> 必须阻挡
    private static final int SCORE_DEAD_4 = 50_000;
    // 活三 (_XXX_) -> 强潜力
    private static final int SCORE_LIVE_3 = 5_000;
    // 冲三 (OXXX_)
    private static final int SCORE_DEAD_3 = 500;
    // 活二 (_XX_)
    private static final int SCORE_LIVE_2 = 100;

    // 搜索参数
    private static final int MAX_DEPTH = 2; // 六子棋分支极大，2层配合好的评估已足够强
    private static final int SEARCH_CANDIDATES = 15; // 每层只选前N个高分点组合

    private final int[] grid = new int[SIZE];
    private int myColorInt;
    private int oppColorInt;

    // 方向向量：右，下，右下，左下
    private static final int[] DX = {1, 0, 1, 1};
    private static final int[] DY = {0, 1, 1, -1};

    @Override
    public String name() {
        return "G99";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        this.board = new Board();
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        if (opponentMove != null) {
            this.board.makeMove(opponentMove);
        }

        // 1. 同步棋盘并确定颜色
        int stones = 0;
        for (int i = 0; i < SIZE; i++) {
            PieceColor c = this.board.get(i);
            if (c == PieceColor.BLACK) {
                grid[i] = BLACK;
                stones++;
            } else if (c == PieceColor.WHITE) {
                grid[i] = WHITE;
                stones++;
            } else {
                grid[i] = EMPTY;
            }
        }

        // 判断执棋颜色 (Black先手1子，之后每人2子。总数%4==1或0时该白，否则该黑？)
        // 实际上可以用 stones 数量判断。
        // Move 0: Black(1) -> stones=1. White move.
        // Move 1: White(2) -> stones=3. Black move.
        // Move 2: Black(2) -> stones=5. White move.
        // 规律: (stones + 1) / 2 是回合数。
        // 简单判断：如果当前 board.get(lastMove) 是黑，那我是白，反之亦然。
        // 或者直接根据 stones 奇偶性判断谁该下（注意第一手特例）
        // 假设当前轮到我下：
        if (stones == 0) { // 我是先手黑棋，只下一子
            // 开局占天元
            myColorInt = BLACK;
            oppColorInt = WHITE;
            // 修复：Move 构造函数需要两个参数，使用 -1 作为无效的第二手棋子
            return new Move(WIDTH/2 * WIDTH + WIDTH/2, -1);
        }

        // 简单判定颜色逻辑：根据已存在棋子推断
        // 一般来说框架会保证调用 findNextMove 时是我的回合
        // 如果棋盘上黑子多于白子，且差值为1，则轮到白子。
        int blackCnt = 0, whiteCnt = 0;
        for(int x : grid) { if(x == BLACK) blackCnt++; else if(x == WHITE) whiteCnt++; }

        if (blackCnt > whiteCnt) {
            myColorInt = WHITE;
            oppColorInt = BLACK;
        } else {
            myColorInt = BLACK;
            oppColorInt = WHITE;
        }

        // 2. 执行搜索
        Move bestMove = alphaBetaSearch();
        this.board.makeMove(bestMove);
        return bestMove;
    }

    // --- 搜索核心 ---

    private Move alphaBetaSearch() {
        // 第一步：检查是否有直接获胜的走法
        List<MyMove> winMoves = findWinningMoves(myColorInt);
        if (!winMoves.isEmpty()) return winMoves.get(0).toMove();

        // 第二步：检查是否必须防守（对方有必胜棋形）
        List<MyMove> forcedMoves = findForcedDefenseMoves();

        List<MyMove> moves;
        if (!forcedMoves.isEmpty()) {
            moves = forcedMoves; // 被迫防守，只搜索这些走法
        } else {
            moves = generateMoves(myColorInt); // 常规生成
        }

        if (moves.isEmpty()) return new Move(getAnyEmpty(), getAnyEmpty());

        MyMove best = moves.get(0);
        int alpha = -2_000_000_000;
        int beta = 2_000_000_000;

        for (MyMove m : moves) {
            applyMove(m, myColorInt);
            // 深度为 1 即可，因为 generateMoves 已经做了一定的筛选
            int val = minValue(MAX_DEPTH - 1, alpha, beta);
            undoMove(m);

            if (val > alpha) {
                alpha = val;
                best = m;
            }
        }
        return best.toMove();
    }

    private int maxValue(int depth, int alpha, int beta) {
        int score = evaluate();
        if (depth <= 0 || Math.abs(score) > SCORE_WIN / 2) return score;

        List<MyMove> moves = generateMoves(myColorInt);
        if (moves.isEmpty()) return score;

        int bestVal = -2_000_000_000;
        for (MyMove m : moves) {
            applyMove(m, myColorInt);
            int val = minValue(depth - 1, alpha, beta);
            undoMove(m);

            if (val > bestVal) bestVal = val;
            if (bestVal > alpha) alpha = bestVal;
            if (beta <= alpha) break;
        }
        return bestVal;
    }

    private int minValue(int depth, int alpha, int beta) {
        int score = evaluate();
        if (depth <= 0 || Math.abs(score) > SCORE_WIN / 2) return score;

        List<MyMove> moves = generateMoves(oppColorInt);
        if (moves.isEmpty()) return score;

        int bestVal = 2_000_000_000;
        for (MyMove m : moves) {
            applyMove(m, oppColorInt);
            int val = maxValue(depth - 1, alpha, beta);
            undoMove(m);

            if (val < bestVal) bestVal = val;
            if (bestVal < beta) beta = bestVal;
            if (beta <= alpha) break;
        }
        return bestVal;
    }

    // --- 走法生成与筛选 ---

    // 寻找直接获胜的走法 (成六，或成活四/五)
    private List<MyMove> findWinningMoves(int color) {
        List<MyMove> wins = new ArrayList<>();
        // 简化的启发式：如果能在某点形成 6 或 活4，直接返回
        // 由于这需要复杂的探测，我们把它合并在 evaluateCandidate 中
        // 这里留空，依赖 generateMoves 的排序将必胜棋排在最前
        return wins;
    }

    // 寻找必须防守的走法（阻挡对方的五或活四）
    private List<MyMove> findForcedDefenseMoves() {
        // 检测对方的高威胁点
        List<Integer> threats = getCriticalPoints(oppColorInt);
        if (threats.isEmpty()) return Collections.emptyList();

        // 必须在这些关键点上下子
        List<MyMove> defense = new ArrayList<>();

        // 如果威胁点非常多（>=2），我们必须同时堵住两个，或者堵一个并制造反威胁
        // 简单策略：生成包含威胁点的走法
        int t1 = threats.get(0);
        int t2 = (threats.size() > 1) ? threats.get(1) : -1;

        if (t2 != -1) {
            // 两个威胁，必须都堵
            defense.add(new MyMove(t1, t2));
        } else {
            // 一个威胁，堵住它，另一子选高分点
            List<Integer> candidates = getCandidates(myColorInt, 5);
            for (int c : candidates) {
                if (c != t1) defense.add(new MyMove(t1, c));
            }
            if (defense.isEmpty()) { // 找不到好的第二点，随便找个邻居
                defense.add(new MyMove(t1, getBestNeighbor(t1)));
            }
        }
        return defense;
    }

    // 生成候选走法
    private List<MyMove> generateMoves(int color) {
        List<MyMove> moves = new ArrayList<>();

        // 1. 获取单点评估最高的前 N 个点
        List<Integer> candidates = getCandidates(color, SEARCH_CANDIDATES);

        // 2. 组合这些点
        // 策略：高分点两两组合
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                moves.add(new MyMove(candidates.get(i), candidates.get(j)));
            }
        }

        // 如果还是没有走法（比如开局），选中心
        if (moves.isEmpty()) {
            int center = WIDTH/2 * WIDTH + WIDTH/2;
            if (grid[center] == EMPTY) {
                moves.add(new MyMove(center, getBestNeighbor(center)));
            } else {
                moves.add(new MyMove(getAnyEmpty(), getAnyEmpty()));
            }
        }
        return moves;
    }

    // 评估所有空位价值，返回前 limit 个
    private List<Integer> getCandidates(int color, int limit) {
        List<PointScore> scores = new ArrayList<>();

        for (int i = 0; i < SIZE; i++) {
            if (grid[i] != EMPTY) continue;

            // 评分 = 进攻分 + 防守分
            int attack = evaluatePoint(i, color);
            int defense = evaluatePoint(i, oppColorInt);

            // 六子棋中，防守通常比进攻更紧迫，除非进攻能直接赢
            // 简单的总分
            int score = attack + defense;

            // 距离中心的微弱加分（打破平局）
            int cx = WIDTH/2, cy = WIDTH/2;
            int x = i % WIDTH, y = i / WIDTH;
            score += (10 - Math.abs(x - cx) - Math.abs(y - cy));

            if (score > 10) { // 过滤掉太差的点
                scores.add(new PointScore(i, score));
            }
        }

        Collections.sort(scores);

        List<Integer> result = new ArrayList<>();
        for (int k = 0; k < Math.min(limit, scores.size()); k++) {
            result.add(scores.get(k).idx);
        }
        return result;
    }

    // 检测某一方的必救点（冲四、活四、五连的空位）
    private List<Integer> getCriticalPoints(int color) {
        List<Integer> points = new ArrayList<>();
        // 利用 evaluatePoint 如果分数极高，说明是关键点
        for (int i = 0; i < SIZE; i++) {
            if (grid[i] != EMPTY) continue;
            int score = evaluatePoint(i, color);
            // 如果此点能形成活四或五连，则是关键点
            if (score >= SCORE_LIVE_4) {
                points.add(i);
            }
        }
        return points;
    }

    // --- 棋形评估 ---

    // 全盘静态评估
    private int evaluate() {
        int myScore = evaluateColor(myColorInt);
        int oppScore = evaluateColor(oppColorInt);
        return myScore - oppScore * 2; // 稍微偏向防守 (Opponent score 权重高一点)
    }

    // 评估单色全盘分数
    private int evaluateColor(int color) {
        int total = 0;
        // 扫描四个方向的所有线
        // 为了性能，这里可以做增量更新，但 Java 实现为了简单先全盘扫

        // 水平
        for(int y=0; y<WIDTH; y++) {
            for(int x=0; x<WIDTH; x++) {
                // 仅当是线的起点或前一个不是同色时开始统计，避免重复
                // 这里为了简单，仅统计定长 pattern
                if (grid[y*WIDTH+x] == color) {
                    // 实际上全盘扫描比较复杂，这里简化：
                    // 遍历所有可能的 6 格窗口还是比较稳健的
                }
            }
        }

        // 由于全盘扫描代码量大，我们使用一种基于 Line Scanning 的方法
        // 直接复用 evaluateLine 逻辑
        return scanAllLines(color);
    }

    private int scanAllLines(int color) {
        int score = 0;
        // 4个方向
        for (int i = 0; i < 4; i++) {
            score += scanDirection(color, DX[i], DY[i]);
        }
        return score;
    }

    private int scanDirection(int color, int dx, int dy) {
        int score = 0;
        boolean[][] visited = new boolean[WIDTH][WIDTH]; // 避免重复计算同一串

        for (int y = 0; y < WIDTH; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (visited[y][x]) continue;
                if (grid[y*WIDTH+x] == color) {
                    // 发现棋子，向后延伸统计连续长度
                    int count = 0;
                    int curX = x, curY = y;
                    while(isValid(curX, curY) && grid[curY*WIDTH+curX] == color) {
                        visited[curY][curX] = true;
                        count++;
                        curX += dx;
                        curY += dy;
                    }

                    // 统计两端空位
                    int empty = 0;
                    // 前端 (x - dx, y - dy)
                    if (isValid(x - dx, y - dy) && grid[(y-dy)*WIDTH+(x-dx)] == EMPTY) empty++;
                    // 后端 (curX, curY)
                    if (isValid(curX, curY) && grid[curY*WIDTH+curX] == EMPTY) empty++;

                    // 根据长度和空位数评分 (这里是基础连子，不包含跳子，跳子需更复杂逻辑)
                    // Connect6 允许跳子成连，但基础 AI 先只看连续的
                    if (count >= 6) score += SCORE_WIN;
                    else if (count == 5) score += SCORE_FIVE;
                    else if (count == 4) {
                        if (empty == 2) score += SCORE_LIVE_4;
                        else if (empty == 1) score += SCORE_DEAD_4;
                    } else if (count == 3) {
                        if (empty == 2) score += SCORE_LIVE_3;
                        else if (empty == 1) score += SCORE_DEAD_3;
                    } else if (count == 2) {
                        if (empty == 2) score += SCORE_LIVE_2;
                    }
                }
            }
        }
        return score;
    }

    // 评估在点 p 下子能不能形成好棋形
    private int evaluatePoint(int p, int color) {
        int score = 0;
        int x = p % WIDTH;
        int y = p / WIDTH;

        grid[p] = color; // 试着下这个子

        // 检查 4 个方向
        for(int d=0; d<4; d++) {
            // 简单的局部线性扫描：向前找5步，向后找5步
            // 统计 6 格范围内的最大棋子数
            score += evaluateLineAround(x, y, DX[d], DY[d], color);
        }

        grid[p] = EMPTY; // 还原
        return score;
    }

    // 评估经过 (x,y) 的某条线上，在 (x,y) 落子后的价值
    private int evaluateLineAround(int x, int y, int dx, int dy, int color) {
        // 我们查看包含 (x,y) 的所有长度为 6 的窗口
        // 只要有一个窗口满足条件，就加分
        int maxScore = 0;

        // k 是窗口的起始偏移量，从 -5 到 0
        for (int k = -5; k <= 0; k++) {
            int cnt = 0;
            int empty = 0;
            boolean blocked = false;

            // 检查窗口 [k, k+5]
            for (int w = 0; w < 6; w++) {
                int nx = x + (k + w) * dx;
                int ny = y + (k + w) * dy;

                if (!isValid(nx, ny)) { blocked = true; break; }

                int val = grid[ny*WIDTH+nx];
                if (val == color) cnt++;
                else if (val == EMPTY) empty++;
                else { blocked = true; break; }
            }

            if (!blocked) {
                int currentScore = 0;
                if (cnt == 6) currentScore = SCORE_WIN;
                else if (cnt == 5) currentScore = SCORE_FIVE; // 下完变成5，说明原来是4
                else if (cnt == 4) currentScore = SCORE_LIVE_4; // 这是粗略估计，未严格区分活/冲
                else if (cnt == 3) currentScore = SCORE_LIVE_3;
                else if (cnt == 2) currentScore = SCORE_LIVE_2;

                if (currentScore > maxScore) maxScore = currentScore;
            }
        }
        return maxScore;
    }

    // --- 辅助 ---

    private boolean isValid(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < WIDTH;
    }

    private void applyMove(MyMove m, int color) {
        if (m.p1 != -1) grid[m.p1] = color;
        if (m.p2 != -1) grid[m.p2] = color;
    }

    private void undoMove(MyMove m) {
        if (m.p1 != -1) grid[m.p1] = EMPTY;
        if (m.p2 != -1) grid[m.p2] = EMPTY;
    }

    private int getBestNeighbor(int idx) {
        int x = idx % WIDTH, y = idx / WIDTH;
        for(int dy=-1; dy<=1; dy++) {
            for(int dx=-1; dx<=1; dx++) {
                if(dx==0 && dy==0) continue;
                if (isValid(x+dx, y+dy) && grid[(y+dy)*WIDTH+(x+dx)] == EMPTY)
                    return (y+dy)*WIDTH+(x+dx);
            }
        }
        return getAnyEmpty();
    }

    private int getAnyEmpty() {
        for(int i=0; i<SIZE; i++) if (grid[i] == EMPTY) return i;
        return 0;
    }

    private static class MyMove {
        int p1, p2;
        MyMove(int p1, int p2) { this.p1 = p1; this.p2 = p2; }
        Move toMove() {
            if (p2 == -1) return new Move(p1, -1); // 使用 -1 作为无效位置
            return new Move(p1, p2);
        }
    }

    private static class PointScore implements Comparable<PointScore> {
        int idx, score;
        PointScore(int i, int s) { idx = i; score = s; }
        @Override
        public int compareTo(PointScore o) { return o.score - this.score; } // 降序
    }
}