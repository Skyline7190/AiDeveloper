package stud.g13;

import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 六子棋 AI - G14 完整版 (修复版)
 * 修正了 PieceColor 类型不匹配和 Move 字段访问权限的问题
 */
public class AI extends core.player.AI {

    // --- 常量定义 ---
    private static final int SIZE = 19;
    private static final int BOARD_SIZE = SIZE * SIZE;
    private static final int SEARCH_DEPTH = 2;
    private static final int VCT_DEPTH = 6;

    // 棋型评分
    private static final int SCORE_WIN = 10000000;
    private static final int SCORE_LIVE_5 = 1000000;
    private static final int SCORE_DEAD_5 = 100000;
    private static final int SCORE_LIVE_4 = 50000;
    private static final int SCORE_DEAD_4 = 5000;
    private static final int SCORE_LIVE_3 = 1000;
    private static final int SCORE_DEAD_3 = 100;
    private static final int SCORE_LIVE_2 = 10;

    // 内部状态 - 使用 PieceColor 数组而不是 int 数组
    private PieceColor[] internalBoard = new PieceColor[BOARD_SIZE];
    private PieceColor myColor;
    private PieceColor opColor;

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        // 初始化内部棋盘，填充 EMPTY
        for (int i = 0; i < BOARD_SIZE; i++) internalBoard[i] = PieceColor.EMPTY;
        this.board = new G13Board("G13Board");
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        // 1. 同步棋盘状态
        // 直接让框架的 board 执行落子，确保状态准确
        if (opponentMove != null) {
            this.board.makeMove(opponentMove);
        }

        // 从 board 同步到 internalBoard，避免直接访问 opponentMove 的字段(防止 index1 访问权限问题)
        syncBoard();

        // 确定颜色
        if (opponentMove == null) {
            // 先手，我是黑棋
            myColor = PieceColor.BLACK;
            opColor = PieceColor.WHITE;
            // 先手开局：占天元
            int center = (SIZE / 2) * SIZE + (SIZE / 2);
            Move startMove = new Move(center, center);
            updateInternalBoard(center, center, myColor);
            this.board.makeMove(startMove);
            return startMove;
        } else {
            // 后手，推断颜色
            // 简单统计：如果棋盘上黑子多或相等(考虑刚下的)，那我可能是白？
            // 更准确的方法：opponentMove 下的是对手的颜色
            // 我们直接检查棋盘上刚下的位置的颜色
            opColor = (myColor == PieceColor.BLACK) ? PieceColor.WHITE : PieceColor.BLACK;
            // 第一次进入时 myColor 可能未初始化，修正：
            if (myColor == null) {
                // 如果当前只有对手刚下的2个子(或1个)，说明我是后手(白)
                // 此时棋盘上非空的颜色就是 opColor
                myColor = PieceColor.WHITE;
                opColor = PieceColor.BLACK;
                for(int i=0; i<BOARD_SIZE; i++){
                    if(internalBoard[i] == PieceColor.BLACK) {
                        opColor = PieceColor.BLACK;
                        myColor = PieceColor.WHITE;
                        break;
                    } else if (internalBoard[i] == PieceColor.WHITE) {
                        opColor = PieceColor.WHITE;
                        myColor = PieceColor.BLACK;
                        break;
                    }
                }
            }
        }

        // --- V3: 威胁空间搜索 (TBS / VCT) ---
        Move vctMove = searchVCT(VCT_DEPTH);
        if (vctMove != null) {
            makeMoveOnFrameworkBoard(vctMove);
            return vctMove;
        }

        // --- V1: 紧急防御与直接获胜 ---
        Move winMove = findImmediateWin();
        if (winMove != null) {
            makeMoveOnFrameworkBoard(winMove);
            return winMove;
        }

        // --- V2: Alpha-Beta 博弈树搜索 ---
        MyMove bestMoveInternal = alphaBetaSearch(SEARCH_DEPTH);
        Move bestMove = new Move(bestMoveInternal.i1, bestMoveInternal.i2);

        // 执行落子
        makeMoveOnFrameworkBoard(bestMove);
        return bestMove;
    }

    // 辅助类：在内部传输坐标，避免访问 Move 对象的字段
    private static class MyMove {
        int i1, i2;
        public MyMove(int i1, int i2) { this.i1 = i1; this.i2 = i2; }
    }

    // 将 Move 应用到实际棋盘并同步内部状态
    private void makeMoveOnFrameworkBoard(Move move) {
        // 更新内部状态 (假设我们知道坐标，如果是 VCT/Search 返回的 Move，我们最好保留坐标信息)
        // 这里为了安全，我们重新同步一次或使用已知坐标。
        // 由于 Move 对象字段不可见，我们在生成 Move 时如果没保留坐标会很麻烦。
        // *策略*：在 findNextMove 中，所有的 Move 都是我们刚才生成的，
        // 我们应该在生成 Move 时就知道坐标。
        // 这里做一次全量同步最安全，但效率低。
        // 更高效：我们在 return 前手动 updateInternalBoard

        // 这里的 move 对象是我们 new 出来的，无法简单获取 i1, i2。
        // 但我们在 return 语句前可以直接调用 updateInternalBoard(i1, i2, myColor)
        // 所以本方法只负责 board.makeMove，调用者负责 updateInternalBoard
        this.board.makeMove(move);
        // 为了防止状态不一致，再次同步
        syncBoard();
    }

    // 从框架 Board 同步到 int[] 数组（这里是 PieceColor[]）
    private void syncBoard() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            internalBoard[i] = this.board.get(i);
        }
    }

    // ==========================================
    //       V3: 威胁空间搜索 (TBS)
    // ==========================================

    private Move searchVCT(int depth) {
        List<MyMove> attackMoves = generateThreatMoves(myColor);
        if (attackMoves.isEmpty()) return null;

        for (MyMove mm : attackMoves) {
            updateInternalBoard(mm.i1, mm.i2, myColor);
            int score = evaluateBoard();

            // 恢复
            updateInternalBoard(mm.i1, mm.i2, PieceColor.EMPTY);

            if (score > SCORE_LIVE_5) return new Move(mm.i1, mm.i2);
        }
        return null;
    }

    private List<MyMove> generateThreatMoves(PieceColor color) {
        List<MyMove> moves = new ArrayList<>();
        List<Integer> points = getCandidatePoints(color);

        if (points.size() < 2) return moves;

        for (int i = 0; i < Math.min(5, points.size()); i++) {
            for (int j = i + 1; j < Math.min(5, points.size()); j++) {
                moves.add(new MyMove(points.get(i), points.get(j)));
            }
        }
        return moves;
    }

    // ==========================================
    //       V2: Alpha-Beta 搜索
    // ==========================================

    private MyMove alphaBetaSearch(int depth) {
        int alpha = -Integer.MAX_VALUE;
        int beta = Integer.MAX_VALUE;

        List<MyMove> candidates = generateCandidateMoves();
        MyMove bestMove = candidates.isEmpty() ? new MyMove(0, 1) : candidates.get(0);

        int maxVal = -Integer.MAX_VALUE;

        for (MyMove move : candidates) {
            updateInternalBoard(move.i1, move.i2, myColor);
            int val = minNode(depth - 1, alpha, beta);
            updateInternalBoard(move.i1, move.i2, PieceColor.EMPTY); // Undo

            if (val > maxVal) {
                maxVal = val;
                bestMove = move;
            }
            alpha = Math.max(alpha, maxVal);
            if (beta <= alpha) break;
        }
        return bestMove;
    }

    private int maxNode(int depth, int alpha, int beta) {
        if (depth <= 0) return evaluateBoard();

        List<MyMove> candidates = generateCandidateMoves();
        int maxVal = -Integer.MAX_VALUE;

        for (MyMove move : candidates) {
            updateInternalBoard(move.i1, move.i2, myColor);
            int val = minNode(depth - 1, alpha, beta);
            updateInternalBoard(move.i1, move.i2, PieceColor.EMPTY);

            maxVal = Math.max(maxVal, val);
            alpha = Math.max(alpha, maxVal);
            if (beta <= alpha) break;
        }
        return maxVal;
    }

    private int minNode(int depth, int alpha, int beta) {
        if (depth <= 0) return evaluateBoard();

        List<MyMove> candidates = generateCandidateMoves();
        int minVal = Integer.MAX_VALUE;

        for (MyMove move : candidates) {
            updateInternalBoard(move.i1, move.i2, opColor);
            int val = maxNode(depth - 1, alpha, beta);
            updateInternalBoard(move.i1, move.i2, PieceColor.EMPTY);

            minVal = Math.min(minVal, val);
            beta = Math.min(beta, minVal);
            if (beta <= alpha) break;
        }
        return minVal;
    }

    // ==========================================
    //       V1/V2: 候选点生成与评估
    // ==========================================

    private List<MyMove> generateCandidateMoves() {
        // 仅搜索己方高分点（兼顾进攻与防守，因为评估函数包含了对敌人点位的评估）
        List<Integer> points = getCandidatePoints(myColor);

        List<MyMove> moves = new ArrayList<>();
        int limit = Math.min(points.size(), 8);

        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                moves.add(new MyMove(points.get(i), points.get(j)));
            }
        }
        if (moves.isEmpty()) {
            int p1 = points.isEmpty() ? 0 : points.get(0);
            int p2 = points.size() > 1 ? points.get(1) : p1;
            moves.add(new MyMove(p1, p2));
        }
        return moves;
    }

    private List<Integer> getCandidatePoints(PieceColor color) {
        List<Integer> interestingPoints = new ArrayList<>();
        boolean[] visited = new boolean[BOARD_SIZE];

        for (int i = 0; i < BOARD_SIZE; i++) {
            if (internalBoard[i] != PieceColor.EMPTY) {
                int r = i / SIZE;
                int c = i % SIZE;
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        int nr = r + dr;
                        int nc = c + dc;
                        if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE) {
                            int idx = nr * SIZE + nc;
                            if (internalBoard[idx] == PieceColor.EMPTY && !visited[idx]) {
                                interestingPoints.add(idx);
                                visited[idx] = true;
                            }
                        }
                    }
                }
            }
        }

        // 排序：优先考虑当前局面下价值最高的点（无论是进攻还是防守）
        Collections.sort(interestingPoints, (a, b) -> {
            int scoreA = evaluatePoint(a, myColor) + evaluatePoint(a, opColor);
            int scoreB = evaluatePoint(b, myColor) + evaluatePoint(b, opColor);
            return scoreB - scoreA;
        });

        return interestingPoints;
    }

    private int evaluateBoard() {
        int myScore = calculateTotalScore(myColor);
        int opScore = calculateTotalScore(opColor);
        return myScore - (int)(opScore * 1.2);
    }

    private int calculateTotalScore(PieceColor color) {
        int total = 0;
        for (int i = 0; i < BOARD_SIZE; i++) {
            if (internalBoard[i] == color) {
                total += evaluatePoint(i, color);
            }
        }
        return total;
    }

    private int evaluatePoint(int idx, PieceColor color) {
        int r = idx / SIZE;
        int c = idx % SIZE;
        int maxScore = 0;
        int[][] directions = {{1,0}, {0,1}, {1,1}, {1,-1}};

        for (int[] dir : directions) {
            int count = 1;
            int emptyEnd = 0;

            // 正向
            for (int k = 1; k <= 5; k++) {
                int nr = r + dir[0]*k, nc = c + dir[1]*k;
                if (!isValid(nr, nc)) break;
                PieceColor val = internalBoard[nr*SIZE+nc];
                if (val == color) count++;
                else {
                    if (val == PieceColor.EMPTY) emptyEnd++;
                    break;
                }
            }
            // 反向
            for (int k = 1; k <= 5; k++) {
                int nr = r - dir[0]*k, nc = c - dir[1]*k;
                if (!isValid(nr, nc)) break;
                PieceColor val = internalBoard[nr*SIZE+nc];
                if (val == color) count++;
                else {
                    if (val == PieceColor.EMPTY) emptyEnd++;
                    break;
                }
            }

            if (count >= 6) return SCORE_WIN;
            if (count == 5) maxScore = Math.max(maxScore, (emptyEnd > 0) ? SCORE_LIVE_5 : SCORE_DEAD_5);
            else if (count == 4) maxScore = Math.max(maxScore, (emptyEnd == 2) ? SCORE_LIVE_4 : (emptyEnd == 1 ? SCORE_DEAD_4 : 0));
            else if (count == 3) maxScore = Math.max(maxScore, (emptyEnd == 2) ? SCORE_LIVE_3 : (emptyEnd == 1 ? SCORE_DEAD_3 : 0));
            else if (count == 2) maxScore = Math.max(maxScore, (emptyEnd == 2) ? SCORE_LIVE_2 : 0);
        }
        return maxScore;
    }

    private Move findImmediateWin() {
        List<Integer> points = getCandidatePoints(myColor);
        if (points.size() < 2) return null;

        int limit = Math.min(points.size(), 5);
        for(int i=0; i<limit; i++){
            for(int j=i+1; j<limit; j++){
                int p1 = points.get(i);
                int p2 = points.get(j);

                updateInternalBoard(p1, p2, myColor);
                if (evaluateBoard() > SCORE_WIN / 2) {
                    updateInternalBoard(p1, p2, PieceColor.EMPTY);
                    return new Move(p1, p2);
                }
                updateInternalBoard(p1, p2, PieceColor.EMPTY);
            }
        }
        return null;
    }

    // 更新内部棋盘的通用方法
    private void updateInternalBoard(int i1, int i2, PieceColor color) {
        internalBoard[i1] = color;
        internalBoard[i2] = color;
    }

    private boolean isValid(int r, int c) {
        return r >= 0 && r < SIZE && c >= 0 && c < SIZE;
    }

    @Override
    public String name() {
        return "G13";
    }
}