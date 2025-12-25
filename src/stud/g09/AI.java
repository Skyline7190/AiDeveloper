package stud.g09;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * G09 AI - 完整实现六子棋 V1/V2/V3 功能
 * 
 * V1: 基础着法生成 + 胜着识别 + 基本防御
 * V2: 盘面估值 + Alpha-Beta 剪枝  
 * V3: 威胁空间搜索 (TSS) + 置换表优化
 */
public class AI extends core.player.AI {

    private static final int WIDTH = 19;
    private static final int SIZE = 361;

    // 棋子表示
    private static final int EMPTY = 0;
    private static final int BLACK = 1;
    private static final int WHITE = 2;

    // ============ V2: 评分权重 ============
    private static final int SCORE_WIN = 10_000_000;
    private static final int SCORE_FIVE = 1_000_000;
    private static final int SCORE_LIVE_4 = 500_000;
    private static final int SCORE_DEAD_4 = 80_000;
    private static final int SCORE_LIVE_3 = 10_000;
    private static final int SCORE_DEAD_3 = 1_000;
    private static final int SCORE_LIVE_2 = 200;
    private static final int SCORE_DEAD_2 = 50;

    // ============ 搜索参数 ============
    private static final int MAX_DEPTH = 2;               // Alpha-Beta搜索深度
    private static final int TSS_DEPTH = 4;               // 威胁搜索深度（降低防止卡顿）
    private static final int SEARCH_CANDIDATES = 15;      // 候选点数量
    private static final long MAX_SEARCH_TIME_MS = 4500;  // 最大搜索时间（毫秒）

    private final int[] grid = new int[SIZE];
    private int myColorInt;
    private int oppColorInt;
    private long searchStartTime;  // 搜索开始时间

    // 方向向量
    private static final int[] DX = {1, 0, 1, 1};
    private static final int[] DY = {0, 1, 1, -1};

    // V3: 置换表
    private static final long[][] ZOBRIST = new long[SIZE][3];
    private long currentHash = 0;
    private final Map<Long, TTEntry> transTable = new HashMap<>();

    static {
        java.util.Random r = new java.util.Random(12345);
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < 3; j++) {
                ZOBRIST[i][j] = r.nextLong();
            }
        }
    }

    @Override
    public String name() {
        return "G09";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        this.board = new Board();
        transTable.clear();
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        if (opponentMove != null) {
            this.board.makeMove(opponentMove);
        }

        // 记录搜索开始时间
        searchStartTime = System.currentTimeMillis();

        // 1. 同步棋盘
        syncBoard();

        // 2. 判断颜色
        int blackCnt = 0, whiteCnt = 0;
        for (int x : grid) {
            if (x == BLACK) blackCnt++;
            else if (x == WHITE) whiteCnt++;
        }

        // 开局第一手
        if (blackCnt == 0 && whiteCnt == 0) {
            myColorInt = BLACK;
            oppColorInt = WHITE;
            int center = WIDTH / 2 * WIDTH + WIDTH / 2;
            Move move = new Move(center, -1);
            this.board.makeMove(move);
            return move;
        }

        if (blackCnt > whiteCnt) {
            myColorInt = WHITE;
            oppColorInt = BLACK;
        } else {
            myColorInt = BLACK;
            oppColorInt = WHITE;
        }

        // 3. 智能搜索
        Move bestMove = findBestMove();
        this.board.makeMove(bestMove);
        return bestMove;
    }

    // 检查是否超时
    private boolean isTimeout() {
        return System.currentTimeMillis() - searchStartTime > MAX_SEARCH_TIME_MS;
    }

    private void syncBoard() {
        currentHash = 0;
        for (int i = 0; i < SIZE; i++) {
            PieceColor c = this.board.get(i);
            if (c == PieceColor.BLACK) {
                grid[i] = BLACK;
                currentHash ^= ZOBRIST[i][BLACK];
            } else if (c == PieceColor.WHITE) {
                grid[i] = WHITE;
                currentHash ^= ZOBRIST[i][WHITE];
            } else {
                grid[i] = EMPTY;
            }
        }
    }

    // ============ 主搜索入口 ============
    private Move findBestMove() {
        // V1: 检测直接获胜（非常快，不需要超时检查）
        MyMove winMove = findWinningMove(myColorInt);
        if (winMove != null) return winMove.toMove();

        // V1: 检测强制防御（非常快）
        MyMove defMove = findForcedDefense();
        if (defMove != null) return defMove.toMove();

        // V3: 威胁空间搜索 (TSS) - 带超时保护
        if (!isTimeout()) {
            MyMove tssMove = threatSpaceSearch(TSS_DEPTH);
            if (tssMove != null) return tssMove.toMove();
        }

        // V2: Alpha-Beta 搜索
        return alphaBetaSearch().toMove();
    }

    // ============ V1: 胜着识别 ============
    private MyMove findWinningMove(int color) {
        List<Integer> fivePoints = new ArrayList<>();
        List<Integer> live4Points = new ArrayList<>();

        for (int i = 0; i < SIZE; i++) {
            if (grid[i] != EMPTY) continue;

            int score = evaluatePoint(i, color);

            if (score >= SCORE_WIN) {
                int second = findBestSecond(i, color);
                return new MyMove(i, second);
            }
            if (score >= SCORE_FIVE) {
                fivePoints.add(i);
            } else if (score >= SCORE_LIVE_4) {
                live4Points.add(i);
            }
        }

        if (fivePoints.size() >= 2) {
            return new MyMove(fivePoints.get(0), fivePoints.get(1));
        }
        if (fivePoints.size() >= 1 && !live4Points.isEmpty()) {
            return new MyMove(fivePoints.get(0), live4Points.get(0));
        }
        if (live4Points.size() >= 2) {
            return new MyMove(live4Points.get(0), live4Points.get(1));
        }

        return null;
    }

    // ============ V1: 强制防御 ============
    private MyMove findForcedDefense() {
        List<Integer> oppFives = new ArrayList<>();
        List<Integer> oppLive4s = new ArrayList<>();

        for (int i = 0; i < SIZE; i++) {
            if (grid[i] != EMPTY) continue;

            int score = evaluatePoint(i, oppColorInt);

            if (score >= SCORE_FIVE) {
                oppFives.add(i);
            } else if (score >= SCORE_LIVE_4) {
                oppLive4s.add(i);
            }
        }

        if (oppFives.size() >= 2) {
            return new MyMove(oppFives.get(0), oppFives.get(1));
        }
        if (oppFives.size() == 1) {
            int def = oppFives.get(0);
            int second = !oppLive4s.isEmpty() ? oppLive4s.get(0) : findBestSecond(def, myColorInt);
            return new MyMove(def, second);
        }
        if (oppLive4s.size() >= 2) {
            return new MyMove(oppLive4s.get(0), oppLive4s.get(1));
        }

        return null;
    }

    // ============ V3: 威胁空间搜索 (TSS) - 带超时保护 ============
    private MyMove threatSpaceSearch(int maxDepth) {
        return tssInternal(myColorInt, 0, maxDepth);
    }

    private MyMove tssInternal(int attacker, int depth, int maxDepth) {
        // 超时检查
        if (isTimeout() || depth >= maxDepth) return null;

        // 只生成高威胁走法（活四及以上）
        List<ThreatMove> threats = generateHighThreatMoves(attacker);
        if (threats.isEmpty()) return null;

        // 限制搜索数量防止爆炸
        int searchLimit = Math.min(threats.size(), 5);
        int defender = (attacker == myColorInt) ? oppColorInt : myColorInt;

        for (int t = 0; t < searchLimit; t++) {
            if (isTimeout()) break;
            
            ThreatMove tm = threats.get(t);
            applyMove(tm.move, attacker);

            // 检查是否形成双威胁（必胜）
            if (tm.score >= SCORE_LIVE_4) {
                int threatCount = countHighThreats(attacker);
                if (threatCount >= 2) {
                    undoMove(tm.move);
                    return tm.move;
                }
            }

            // 简化：只检查最佳防守
            List<Integer> defPoints = getTopDefensePoints(attacker, 3);
            boolean canDefend = false;

            for (int dp : defPoints) {
                if (isTimeout()) break;
                if (grid[dp] != EMPTY) continue;

                int dp2 = findBestSecond(dp, defender);
                if (dp2 == dp || grid[dp2] != EMPTY) continue;

                MyMove defMove = new MyMove(dp, dp2);
                applyMove(defMove, defender);

                MyMove nextThreat = tssInternal(attacker, depth + 1, maxDepth);

                undoMove(defMove);

                if (nextThreat == null) {
                    canDefend = true;
                    break;
                }
            }

            undoMove(tm.move);

            if (!canDefend && !defPoints.isEmpty()) {
                return tm.move;
            }
        }

        return null;
    }

    // 生成高威胁走法（只考虑活四及以上）
    private List<ThreatMove> generateHighThreatMoves(int color) {
        List<ThreatMove> threats = new ArrayList<>();
        List<PointScore> candidates = new ArrayList<>();

        for (int i = 0; i < SIZE; i++) {
            if (grid[i] != EMPTY) continue;
            if (!hasNeighbor(i, 2)) continue;

            int score = evaluatePoint(i, color);
            if (score >= SCORE_DEAD_4) {  // 只考虑冲四及以上
                candidates.add(new PointScore(i, score));
            }
        }

        Collections.sort(candidates);

        // 限制组合数量
        int limit = Math.min(candidates.size(), 6);
        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                int p1 = candidates.get(i).idx;
                int p2 = candidates.get(j).idx;
                int combinedScore = candidates.get(i).score + candidates.get(j).score;
                threats.add(new ThreatMove(new MyMove(p1, p2), combinedScore));
            }
        }

        threats.sort((a, b) -> b.score - a.score);
        return threats;
    }

    // 统计高威胁点数量
    private int countHighThreats(int color) {
        int count = 0;
        for (int i = 0; i < SIZE; i++) {
            if (grid[i] != EMPTY) continue;
            if (evaluatePoint(i, color) >= SCORE_LIVE_4) count++;
        }
        return count;
    }

    // 获取前N个防守点
    private List<Integer> getTopDefensePoints(int attacker, int limit) {
        List<PointScore> points = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            if (grid[i] != EMPTY) continue;
            int score = evaluatePoint(i, attacker);
            if (score >= SCORE_DEAD_4) {
                points.add(new PointScore(i, score));
            }
        }
        Collections.sort(points);
        
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, points.size()); i++) {
            result.add(points.get(i).idx);
        }
        return result;
    }

    // ============ V2: Alpha-Beta 搜索 ============
    private MyMove alphaBetaSearch() {
        List<MyMove> moves = generateMoves(myColorInt);
        if (moves.isEmpty()) return new MyMove(getAnyEmpty(), getAnyEmpty());

        MyMove best = moves.get(0);
        int alpha = Integer.MIN_VALUE + 1;
        int beta = Integer.MAX_VALUE - 1;

        for (MyMove m : moves) {
            if (isTimeout()) break;
            
            applyMove(m, myColorInt);
            int val = minValue(MAX_DEPTH - 1, alpha, beta);
            undoMove(m);

            if (val > alpha) {
                alpha = val;
                best = m;
            }
        }
        return best;
    }

    private int maxValue(int depth, int alpha, int beta) {
        if (isTimeout()) return evaluate();
        
        // 置换表查询
        TTEntry entry = transTable.get(currentHash);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == TTEntry.EXACT) return entry.value;
            if (entry.flag == TTEntry.LOWER) alpha = Math.max(alpha, entry.value);
            if (entry.flag == TTEntry.UPPER) beta = Math.min(beta, entry.value);
            if (alpha >= beta) return entry.value;
        }

        int score = evaluate();
        if (depth <= 0 || Math.abs(score) >= SCORE_WIN / 2) return score;

        List<MyMove> moves = generateMoves(myColorInt);
        if (moves.isEmpty()) return score;

        int bestVal = Integer.MIN_VALUE + 1;
        for (MyMove m : moves) {
            applyMove(m, myColorInt);
            int val = minValue(depth - 1, alpha, beta);
            undoMove(m);

            bestVal = Math.max(bestVal, val);
            alpha = Math.max(alpha, bestVal);
            if (beta <= alpha) break;
        }

        int flag = bestVal <= alpha ? TTEntry.UPPER :
                   bestVal >= beta ? TTEntry.LOWER : TTEntry.EXACT;
        transTable.put(currentHash, new TTEntry(depth, bestVal, flag));

        return bestVal;
    }

    private int minValue(int depth, int alpha, int beta) {
        if (isTimeout()) return evaluate();
        
        TTEntry entry = transTable.get(currentHash);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == TTEntry.EXACT) return entry.value;
            if (entry.flag == TTEntry.LOWER) alpha = Math.max(alpha, entry.value);
            if (entry.flag == TTEntry.UPPER) beta = Math.min(beta, entry.value);
            if (alpha >= beta) return entry.value;
        }

        int score = evaluate();
        if (depth <= 0 || Math.abs(score) >= SCORE_WIN / 2) return score;

        List<MyMove> moves = generateMoves(oppColorInt);
        if (moves.isEmpty()) return score;

        int bestVal = Integer.MAX_VALUE - 1;
        for (MyMove m : moves) {
            applyMove(m, oppColorInt);
            int val = maxValue(depth - 1, alpha, beta);
            undoMove(m);

            bestVal = Math.min(bestVal, val);
            beta = Math.min(beta, bestVal);
            if (beta <= alpha) break;
        }

        int flag = bestVal <= alpha ? TTEntry.UPPER :
                   bestVal >= beta ? TTEntry.LOWER : TTEntry.EXACT;
        transTable.put(currentHash, new TTEntry(depth, bestVal, flag));

        return bestVal;
    }

    // ============ 走法生成 ============
    private List<MyMove> generateMoves(int color) {
        List<MyMove> moves = new ArrayList<>();
        List<Integer> candidates = getCandidates(color, SEARCH_CANDIDATES);

        if (candidates.size() < 2) {
            candidates = getEmptyNearStones();
        }

        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                moves.add(new MyMove(candidates.get(i), candidates.get(j)));
            }
        }

        // 按组合价值排序并限制数量
        moves.sort((a, b) -> {
            int sa = evaluatePoint(a.p1, color) + evaluatePoint(a.p2, color);
            int sb = evaluatePoint(b.p1, color) + evaluatePoint(b.p2, color);
            return sb - sa;
        });

        int limit = Math.min(moves.size(), 30);
        return new ArrayList<>(moves.subList(0, limit));
    }

    private List<Integer> getCandidates(int color, int limit) {
        List<PointScore> scores = new ArrayList<>();
        int opp = (color == myColorInt) ? oppColorInt : myColorInt;

        for (int i = 0; i < SIZE; i++) {
            if (grid[i] != EMPTY) continue;
            if (!hasNeighbor(i, 2)) continue;

            int attack = evaluatePoint(i, color);
            int defense = evaluatePoint(i, opp);
            int total = attack + defense;

            int x = i % WIDTH, y = i / WIDTH;
            total += 15 - Math.abs(x - 9) - Math.abs(y - 9);

            scores.add(new PointScore(i, total));
        }

        Collections.sort(scores);

        List<Integer> result = new ArrayList<>();
        for (int k = 0; k < Math.min(limit, scores.size()); k++) {
            result.add(scores.get(k).idx);
        }

        if (result.isEmpty()) {
            int center = WIDTH / 2 * WIDTH + WIDTH / 2;
            if (grid[center] == EMPTY) result.add(center);
            else result.add(getAnyEmpty());
        }

        return result;
    }

    private List<Integer> getEmptyNearStones() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            if (grid[i] == EMPTY && hasNeighbor(i, 2)) {
                result.add(i);
            }
        }
        if (result.isEmpty()) {
            result.add(WIDTH / 2 * WIDTH + WIDTH / 2);
        }
        return result;
    }

    private boolean hasNeighbor(int pos, int range) {
        int x = pos % WIDTH, y = pos / WIDTH;
        for (int dy = -range; dy <= range; dy++) {
            for (int dx = -range; dx <= range; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (isValid(nx, ny) && grid[ny * WIDTH + nx] != EMPTY) {
                    return true;
                }
            }
        }
        return false;
    }

    // ============ 评估函数 ============
    private int evaluate() {
        int myScore = evaluateColor(myColorInt);
        int oppScore = evaluateColor(oppColorInt);
        return myScore - oppScore;
    }

    private int evaluateColor(int color) {
        int total = 0;
        int opp = (color == myColorInt) ? oppColorInt : myColorInt;

        for (int d = 0; d < 4; d++) {
            total += scanDirection(color, opp, DX[d], DY[d]);
        }

        return total;
    }

    private int scanDirection(int color, int opp, int dx, int dy) {
        int score = 0;

        for (int startY = 0; startY < WIDTH; startY++) {
            for (int startX = 0; startX < WIDTH; startX++) {
                int px = startX - dx, py = startY - dy;
                if (isValid(px, py)) continue;

                int x = startX, y = startY;
                while (true) {
                    int endX = x + 5 * dx;
                    int endY = y + 5 * dy;
                    if (!isValid(endX, endY)) break;

                    int myCount = 0, emptyCount = 0;
                    boolean blocked = false;

                    for (int i = 0; i < 6; i++) {
                        int nx = x + i * dx;
                        int ny = y + i * dy;
                        int cell = grid[ny * WIDTH + nx];
                        if (cell == color) myCount++;
                        else if (cell == EMPTY) emptyCount++;
                        else { blocked = true; break; }
                    }

                    if (!blocked) {
                        if (myCount == 6) score += SCORE_WIN;
                        else if (myCount == 5 && emptyCount == 1) score += SCORE_FIVE;
                        else if (myCount == 4 && emptyCount == 2) score += SCORE_LIVE_4;
                        else if (myCount == 4 && emptyCount == 1) score += SCORE_DEAD_4;
                        else if (myCount == 3 && emptyCount == 3) score += SCORE_LIVE_3;
                        else if (myCount == 3 && emptyCount >= 2) score += SCORE_DEAD_3;
                        else if (myCount == 2 && emptyCount == 4) score += SCORE_LIVE_2;
                        else if (myCount == 2 && emptyCount >= 2) score += SCORE_DEAD_2;
                    }

                    x += dx;
                    y += dy;
                }
            }
        }
        return score;
    }

    private int evaluatePoint(int p, int color) {
        int x = p % WIDTH, y = p / WIDTH;
        int maxScore = 0;
        int opp = (color == myColorInt) ? oppColorInt : myColorInt;

        grid[p] = color;

        for (int d = 0; d < 4; d++) {
            int score = evaluateLineAtPoint(x, y, DX[d], DY[d], color, opp);
            maxScore = Math.max(maxScore, score);
        }

        grid[p] = EMPTY;
        return maxScore;
    }

    private int evaluateLineAtPoint(int x, int y, int dx, int dy, int color, int opp) {
        int maxScore = 0;

        for (int k = -5; k <= 0; k++) {
            int myCount = 0, emptyCount = 0;
            boolean blocked = false;

            for (int i = 0; i < 6; i++) {
                int nx = x + (k + i) * dx;
                int ny = y + (k + i) * dy;

                if (!isValid(nx, ny)) { blocked = true; break; }

                int val = grid[ny * WIDTH + nx];
                if (val == color) myCount++;
                else if (val == EMPTY) emptyCount++;
                else { blocked = true; break; }
            }

            if (!blocked) {
                int score = 0;
                if (myCount == 6) score = SCORE_WIN;
                else if (myCount == 5) score = SCORE_FIVE;
                else if (myCount == 4 && emptyCount >= 2) score = SCORE_LIVE_4;
                else if (myCount == 4) score = SCORE_DEAD_4;
                else if (myCount == 3 && emptyCount >= 3) score = SCORE_LIVE_3;
                else if (myCount == 3) score = SCORE_DEAD_3;
                else if (myCount == 2 && emptyCount >= 4) score = SCORE_LIVE_2;

                maxScore = Math.max(maxScore, score);
            }
        }

        return maxScore;
    }

    // ============ 辅助方法 ============
    private boolean isValid(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < WIDTH;
    }

    private void applyMove(MyMove m, int color) {
        if (m.p1 >= 0 && m.p1 < SIZE) {
            grid[m.p1] = color;
            currentHash ^= ZOBRIST[m.p1][color];
        }
        if (m.p2 >= 0 && m.p2 < SIZE) {
            grid[m.p2] = color;
            currentHash ^= ZOBRIST[m.p2][color];
        }
    }

    private void undoMove(MyMove m) {
        if (m.p1 >= 0 && m.p1 < SIZE) {
            currentHash ^= ZOBRIST[m.p1][grid[m.p1]];
            grid[m.p1] = EMPTY;
        }
        if (m.p2 >= 0 && m.p2 < SIZE) {
            currentHash ^= ZOBRIST[m.p2][grid[m.p2]];
            grid[m.p2] = EMPTY;
        }
    }

    private int findBestSecond(int first, int color) {
        int best = -1;
        int bestScore = -1;

        for (int i = 0; i < SIZE; i++) {
            if (i == first || grid[i] != EMPTY) continue;

            int score = evaluatePoint(i, color);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }

        if (best == -1) best = getAnyEmpty();
        return best;
    }

    private int getAnyEmpty() {
        int center = WIDTH / 2 * WIDTH + WIDTH / 2;
        if (grid[center] == EMPTY) return center;

        for (int i = 0; i < SIZE; i++) {
            if (grid[i] == EMPTY) return i;
        }
        return 0;
    }

    // ============ 内部类 ============
    private static class MyMove {
        int p1, p2;

        MyMove(int p1, int p2) {
            this.p1 = p1;
            this.p2 = p2;
        }

        Move toMove() {
            return new Move(p1, p2);
        }
    }

    private static class PointScore implements Comparable<PointScore> {
        int idx, score;

        PointScore(int i, int s) {
            idx = i;
            score = s;
        }

        @Override
        public int compareTo(PointScore o) {
            return o.score - this.score;
        }
    }

    private static class ThreatMove {
        MyMove move;
        int score;

        ThreatMove(MyMove m, int s) {
            move = m;
            score = s;
        }
    }

    private static class TTEntry {
        static final int EXACT = 0;
        static final int LOWER = 1;
        static final int UPPER = 2;

        int depth;
        int value;
        int flag;

        TTEntry(int d, int v, int f) {
            depth = d;
            value = v;
            flag = f;
        }
    }
}