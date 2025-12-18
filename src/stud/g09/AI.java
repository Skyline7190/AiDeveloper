package stud.g09;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AI extends core.player.AI {

    private static final int WIDTH = 19;
    private static final int SIZE = 361;
    private static final int BLACK = 1;
    private static final int WHITE = 2;
    private static final int EMPTY = 0;
    
    // 评估权重
    private static final int WIN = 10000000;
    private static final int LIVE_5 = 100000;
    private static final int LIVE_4 = 5000;
    // DEAD_4 常量当前未使用，已移除以消除警告
    // private static final int DEAD_4 = 1000;
    private static final int LIVE_3 = 500;
    private static final int LIVE_2 = 100;
    private static final int LIVE_1 = 10;
    
    private final int[] grid = new int[SIZE];
    private int myColorInt;
    private int oppColorInt;

    @Override
    public String name() {
        return "G09-V3";
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
        int pieceCount = 0;
        for (int i = 0; i < SIZE; i++) {
            PieceColor c = this.board.get(i);
            if (c == PieceColor.BLACK) {
                grid[i] = BLACK;
                pieceCount++;
            } else if (c == PieceColor.WHITE) {
                grid[i] = WHITE;
                pieceCount++;
            } else {
                grid[i] = EMPTY;
            }
        }
        
        if (pieceCount % 4 == 1) {
            myColorInt = WHITE;
            oppColorInt = BLACK;
        } else {
            myColorInt = BLACK;
            oppColorInt = WHITE;
        }

        // 2. 搜索
        Move bestMove = alphaBetaSearch(); // 深度 2

        this.board.makeMove(bestMove);
        return bestMove;
    }

    // --- 搜索 ---

    private Move alphaBetaSearch() {
        int depth = 2;
        List<MyMove> moves = generateMoves(myColorInt);
        
        // 如果只有一个走法（必胜或必须阻挡），直接返回
        if (moves.isEmpty()) return new Move(getAnyEmpty(), getAnyEmpty()); // 备用（回退）
        if (moves.size() == 1) return moves.get(0).toMove();

        int alpha = -2000000000;
        int beta = 2000000000;
        MyMove best = moves.get(0);

        for (MyMove m : moves) {
            applyMove(m, myColorInt);
            int val = minValue(depth - 1, alpha, beta);
            undoMove(m);
            
            if (val > alpha) {
                alpha = val;
                best = m;
            }
        }
        return best.toMove();
    }

    private int minValue(int depth, int alpha, int beta) {
        if (depth == 0) return evaluate();
        
        // 检查游戏是否结束（我方获胜）?
        // 如果之前的走法已导致胜利，评分会很高。但这里假设游戏继续。
        // 实际上 evaluate 会检查棋盘。
        // 如果现在该对手走，对手会看到我方已获胜，则对我方评分为最大。
        int currentScore = evaluate();
        if (currentScore > WIN / 2) return currentScore; // 我已经获胜

        List<MyMove> moves = generateMoves(oppColorInt);
        if (moves.isEmpty()) return currentScore;

        int value = 2000000000;
        for (MyMove m : moves) {
            applyMove(m, oppColorInt);
            int val = maxValue(depth - 1, alpha, beta);
            undoMove(m);
            
            if (val < value) {
                value = val;
            }
            if (value <= alpha) return value; // 剪枝
            beta = Math.min(beta, value);
        }
        return value;
    }

    private int maxValue(int depth, int alpha, int beta) {
        if (depth == 0) return evaluate();
        
        int currentScore = evaluate();
        if (currentScore < -WIN / 2) return currentScore; // 对手获胜

        List<MyMove> moves = generateMoves(myColorInt);
        if (moves.isEmpty()) return currentScore;

        int value = -2000000000;
        for (MyMove m : moves) {
            applyMove(m, myColorInt);
            int val = minValue(depth - 1, alpha, beta);
            undoMove(m);
            
            if (val > value) {
                value = val;
            }
            if (value >= beta) return value; // 剪枝
            alpha = Math.max(alpha, value);
        }
        return value;
    }

    // --- 走法生成 ---

    private List<MyMove> generateMoves(int color) {
        List<MyMove> moves = new ArrayList<>();
        int otherColor = (color == BLACK) ? WHITE : BLACK;
        
        // 1. 检查强制走法（必胜/必须阻挡）
        // 我们扫描4 连和5连的威胁。
        Set<Integer> win5 = new HashSet<>();
        Set<Integer> win4 = new HashSet<>();
        Set<Integer> block5 = new HashSet<>();
        Set<Integer> block4 = new HashSet<>();
        
        scanThreats(color, win5, win4);
        scanThreats(otherColor, block5, block4);
        
        // 优先级1：连五获胜
        if (!win5.isEmpty()) {
            int p1 = win5.iterator().next();
            moves.add(new MyMove(p1, getBestNeighbor(p1)));
            return moves;
        }
        
        // 优先级2：活四或冲四获胜
        if (!win4.isEmpty()) {
            // 需要找到配对位置。
            // 为简化，这里从 win4 集合中取两个位置构成一对。
            List<Integer> list = new ArrayList<>(win4);
            if (list.size() >= 2) {
                 moves.add(new MyMove(list.get(0), list.get(1))); // 乐观策略
                 // 实际上应验证该对是否能成五，但这里作为简化处理。
                 return moves;
            }
        }
        
        // 优先级3：阻挡对方连五（必须阻挡）
        if (!block5.isEmpty()) {
            int p1 = block5.iterator().next();
            // 必须下 p1。第二个子位使用启发式选择。
            // 如果有多个 block5，尽量处理前两个。
            List<Integer> list = new ArrayList<>(block5);
            if (list.size() >= 2) {
                moves.add(new MyMove(list.get(0), list.get(1)));
                return moves;
            }
            
            // 仅需一个阻挡位置。
            // 将 p1 与顶级启发式候选位置组合。
            List<Integer> candidates = getCandidates(color, 10);
            for (int c : candidates) {
                if (c != p1) moves.add(new MyMove(p1, c));
            }
            if (moves.isEmpty()) moves.add(new MyMove(p1, getBestNeighbor(p1)));
            return moves;
        }
        
        // 优先级4：阻挡对方活四（必须阻挡）
        if (!block4.isEmpty()) {
            // 相似逻辑。
            // 为简化版本，仅将阻挡位于候选位组合。
            List<Integer> candidates = getCandidates(color, 8);
            for (int b : block4) {
                for (int c : candidates) {
                    if (b != c) moves.add(new MyMove(b, c));
                }
            }
            if (!moves.isEmpty()) return moves;
        }
        
        // 常规生成
        // 获取顶级候选位置（靠近棋子）
        List<Integer> candidates = getCandidates(color, 12); // 为速度减少候选数

        // 生成两子对组合
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                moves.add(new MyMove(candidates.get(i), candidates.get(j)));
            }
        }
        
        return moves;
    }
    
    private List<Integer> getCandidates(int color, int limit) {
        // 找到启发式评分最高的空位
        // 评分 = 与己方相邻*2 + 与对手相邻*1 + 中心性
        List<PointScore> scores = new ArrayList<>();
        int cx = WIDTH/2;
        int cy = WIDTH/2;
        
        for (int i=0; i<SIZE; i++) {
            if (grid[i] != EMPTY) continue;
            
            int score = 0;
            // 中心性
            int x = i % WIDTH;
            int y = i / WIDTH;
            score += (10 - Math.abs(x - cx)) + (10 - Math.abs(y - cy));
            
            // 邻居
            for(int dy=-1; dy<=1; dy++) {
                for(int dx=-1; dx<=1; dx++) {
                    if (dx==0 && dy==0) continue;
                    int nx = x+dx, ny = y+dy;
                    if (nx>=0 && nx<WIDTH && ny>=0 && ny<WIDTH) {
                        int val = grid[ny*WIDTH+nx];
                        if (val == color) score += 10;
                        else if (val != EMPTY) score += 5;
                    }
                }
            }
            
            // 优化：只考虑靠近棋子的空位（除非棋盘为空）
            if (score > 10) // 阈值，用于忽略远离的空位
                scores.add(new PointScore(i, score));
        }
        
        scores.sort((a, b) -> b.score - a.score);

        List<Integer> res = new ArrayList<>();
        for(int k=0; k<Math.min(limit, scores.size()); k++) {
            res.add(scores.get(k).idx);
        }
        // 如果为空（开局），加入中心
        if (res.isEmpty()) res.add(cx * WIDTH + cy);
        
        return res;
    }

    private void scanThreats(int color, Set<Integer> win5, Set<Integer> win4) {
        // 简化扫描（检查行）
        // ...（类似 V1，但使用 grid 数组）
        // 使用优化方式：
        int[] dx = {1, 0, 1, 1};
        int[] dy = {0, 1, 1, -1};
        
        for (int d=0; d<4; d++) {
            for (int y=0; y<WIDTH; y++) {
                for (int x=0; x<WIDTH; x++) {
                    // 检查长度为6的线段是否越界
                    int ex = x + 5*dx[d];
                    int ey = y + 5*dy[d];
                    // ex < 0 在当前 dx 集合中不可能（dx >= 0），因此移除该条件
                    if (ex >= WIDTH || ey < 0 || ey >= WIDTH) continue;

                    int cnt = 0;
                    int empty = 0;
                    int[] emptyIdx = new int[6];
                    
                    for (int k=0; k<6; k++) {
                        int nx = x + k * dx[d];
                        int ny = y + k * dy[d];
                        int pos = ny * WIDTH + nx;
                        int val = grid[pos];
                        if (val == color) cnt++;
                        else if (val == EMPTY) {
                            emptyIdx[empty++] = pos;
                        } else {
                            cnt = -1; // 被阻塞
                            break;
                        }
                    }

                    if (cnt == 5 && empty == 1) win5.add(emptyIdx[0]);
                    if (cnt == 4 && empty == 2) {
                        win4.add(emptyIdx[0]);
                        win4.add(emptyIdx[1]);
                    }
                 }
             }
         }
     }

    // --- 评估 ---

    private int evaluate() {
        int score = 0;
        // 对我方和对方的所有线路进行评估
        score += evaluateColor(myColorInt);
        score -= evaluateColor(oppColorInt);
        return score;
    }
    
    private int evaluateColor(int color) {
        int score = 0;
        int[] dx = {1, 0, 1, 1};
        int[] dy = {0, 1, 1, -1};
        
        for (int d=0; d<4; d++) {
            for (int y=0; y<WIDTH; y++) {
                for (int x=0; x<WIDTH; x++) {
                    int ex = x + 5*dx[d];
                    int ey = y + 5*dy[d];
                    // ex < 0 在当前 dx 集合中不可能（dx >= 0），因此移除该条件
                    if (ex >= WIDTH || ey < 0 || ey >= WIDTH) continue;

                    int cnt = 0;
                    boolean blocked = false;
                    
                    for (int k=0; k<6; k++) {
                        int nx = x + k * dx[d];
                        int ny = y + k * dy[d];
                        int pos = ny * WIDTH + nx;
                        int val = grid[pos];
                        if (val == color) cnt++;
                        else if (val != EMPTY) { blocked = true; break; }
                    }
                    
                    if (!blocked) {
                        if (cnt == 6) score += WIN;
                        else if (cnt == 5) score += LIVE_5;
                        else if (cnt == 4) score += LIVE_4;
                        else if (cnt == 3) score += LIVE_3;
                        else if (cnt == 2) score += LIVE_2;
                        else if (cnt == 1) score += LIVE_1;
                    }
                }
            }
        }
        return score;
    }

    // --- 辅助函数 ---

    private void applyMove(MyMove m, int color) {
        grid[m.p1] = color;
        grid[m.p2] = color;
    }
    
    private void undoMove(MyMove m) {
        grid[m.p1] = EMPTY;
        grid[m.p2] = EMPTY;
    }
    
    private int getBestNeighbor(int idx) {
        // 返回一个合法的邻居或随机空位
        int x = idx % WIDTH;
        int y = idx / WIDTH;
        for(int dy=-1; dy<=1; dy++) {
            for(int dx=-1; dx<=1; dx++) {
                if(dx==0 && dy==0) continue;
                int nx = x+dx;
                int ny = y+dy;
                if(nx>=0 && nx<WIDTH && ny>=0 && ny<WIDTH) {
                    int ni = ny*WIDTH+nx;
                    if (grid[ni] == EMPTY) return ni;
                }
            }
        }
        return getAnyEmpty();
    }
    
    private int getAnyEmpty() {
        for(int i=0; i<SIZE; i++) {
            if (grid[i] == EMPTY) return i;
        }
        return 0; // 正常不会到这里
    }

    private static class MyMove {
        int p1, p2;
        MyMove(int p1, int p2) { this.p1 = p1; this.p2 = p2; }
        Move toMove() { return new Move(p1, p2); }
    }
    
    private static class PointScore {
        int idx;
        int score;
        PointScore(int i, int s) { idx = i; score = s; }
    }
}

