package stud.g09;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SmartAI extends core.player.AI {

    private static final int SEARCH_DEPTH = 3;
    private static final int VCT_DEPTH = 5;
    private static final int CANDIDATE_TOP_K = 15;

    // 缓存数组
    private int[] scoreCache = new int[361];

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        this.board = new Board();
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        try {
            if (this.board == null) this.board = new Board();
            if (opponentMove != null) board.makeMove(opponentMove);

            // 1. 开局天元 (标准策略)
            if (board.getMoveList().size() < 2) {
                int center = 19 * 9 + 9;
                if (board.get(center) == PieceColor.EMPTY) {
                    return new Move(center, center);
                }
            }

            PieceColor myColor = board.whoseMove();
            PieceColor opColor = myColor.opposite();

            // 2. 检查我方必胜 (Mate in 1)
            // 我只要下两个子就能凑成6个
            Move winMove = findMateInOne(myColor);
            if (winMove != null) {
                //System.out.println("G09: Found Mate in 1");
                board.makeMove(winMove);
                return winMove;
            }

            // 3. 检查是否需要紧急防守 (对手 Mate in 1)
            // 对手只要有4个子(Live4, Dead4)或5个子，下回合就能赢。必须防住！
            List<Move> defensiveMoves = getDefensiveMoves(opColor);
            if (!defensiveMoves.isEmpty()) {
                //System.out.println("G09: Defensive Mode - Threats detected: " + defensiveMoves.size());

                // 在防守棋中，选一个最好的 (比如能顺便进攻的，或者AlphaBeta分最高的)
                Move bestDef = defensiveMoves.get(0);
                int maxScore = Integer.MIN_VALUE;

                // 简单地用单层评估选最好的防守
                for (Move m : defensiveMoves) {
                    board.makeMove(m);
                    int score = G09Board.evaluate(board, myColor);
                    board.undo();
                    if (score > maxScore) {
                        maxScore = score;
                        bestDef = m;
                    }
                }
                board.makeMove(bestDef);
                return bestDef;
            }

            // 4. VCT 算杀 (如果此时没有防守压力)
            Move vctMove = searchVCT(VCT_DEPTH, myColor);
            if (vctMove != null) {
                //System.out.println("G09: VCT Attack");
                board.makeMove(vctMove);
                return vctMove;
            }

            // 5. 常规 Alpha-Beta 搜索
            Move bestMove = alphaBetaRoot(SEARCH_DEPTH);

            if (bestMove == null) bestMove = getSafeRandomMove();

            board.makeMove(bestMove);
            return bestMove;

        } catch (Exception e) {
            e.printStackTrace();
            return getSafeRandomMove();
        }
    }

    @Override
    public String name() {
        return "G09";
    }

    // --- 核心逻辑 ---

    private Move alphaBetaRoot(int depth) {
        List<Move> moves = generateSortedMoves();
        if (moves.isEmpty()) return getSafeRandomMove();

        Move bestMove = moves.get(0);
        int maxVal = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        for (Move move : moves) {
            board.makeMove(move);
            int val = -alphaBeta(depth - 1, -beta, -alpha);
            board.undo();

            if (val > maxVal) {
                maxVal = val;
                bestMove = move;
            }
            if (maxVal > alpha) alpha = maxVal;
        }
        return bestMove;
    }

    private int alphaBeta(int depth, int alpha, int beta) {
        if (depth <= 0 || board.gameOver()) {
            return G09Board.evaluate(board, board.whoseMove());
        }

        List<Move> moves = generateSortedMoves();
        if (moves.isEmpty()) return G09Board.evaluate(board, board.whoseMove());

        int maxVal = Integer.MIN_VALUE;
        for (Move move : moves) {
            board.makeMove(move);
            int val = -alphaBeta(depth - 1, -beta, -alpha);
            board.undo();

            if (val > maxVal) maxVal = val;
            if (maxVal > alpha) alpha = maxVal;
            if (alpha >= beta) break;
        }
        return maxVal;
    }

    // --- 智能着法生成 ---
    private List<Move> generateSortedMoves() {
        List<Integer> candidates = new ArrayList<>();
        PieceColor myColor = board.whoseMove();
        PieceColor opColor = myColor.opposite();

        // 选取高价值点
        for (int i = 0; i < 361; i++) {
            if (board.get(i) == PieceColor.EMPTY && hasNeighbor(i)) {
                // 简单的启发式：我下的分 + 敌下的分
                // 这里的 quickEvaluatePoint 已经包含了棋型权重
                int myVal = G09Board.quickEvaluatePoint(board, i, myColor);
                int opVal = G09Board.quickEvaluatePoint(board, i, opColor);
                scoreCache[i] = myVal + opVal;
                candidates.add(i);
            }
        }

        candidates.sort((a, b) -> scoreCache[b] - scoreCache[a]);

        int limit = Math.min(candidates.size(), CANDIDATE_TOP_K);
        List<Move> moves = new ArrayList<>();

        // 生成两子组合
        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                moves.add(new Move(candidates.get(i), candidates.get(j)));
            }
        }

        // 截断
        int maxMoves = 20;
        if (moves.size() > maxMoves) return moves.subList(0, maxMoves);
        return moves;
    }

    // --- 必胜/必防检测 (关键) ---

    // 寻找一步赢棋 (我有4子以上)
    private Move findMateInOne(PieceColor color) {
        // 扫描所有空点，看是否有点能让我达到 6
        // 优化：利用 quickEvaluatePoint
        List<Integer> wins = new ArrayList<>();
        for (int i = 0; i < 361; i++) {
            if (board.get(i) == PieceColor.EMPTY && hasNeighbor(i)) {
                // 如果单点能成6，那太好了，另一子随便下
                if (G09Board.quickEvaluatePoint(board, i, color) >= G09Board.SCORE_WIN) {
                    // 找个邻近空位凑成Move
                    for(int j=0; j<361; j++) if(i!=j && board.get(j)==PieceColor.EMPTY) return new Move(i, j);
                }
                // 如果单点成5 (Live5 or Dead5)，再加一子就能6
                if (G09Board.quickEvaluatePoint(board, i, color) >= G09Board.SCORE_DEAD_5) {
                    wins.add(i);
                }
            }
        }

        if (wins.size() >= 1) {
            // 尝试组合两个高分点
            // 其实只要一个是成5点，另一个子只要在同一条线上补齐就是赢
            // 简单遍历所有点对太慢，我们直接模拟：
            // 选一个成5点，然后试遍所有空位，看能不能evaluate出WIN
            int p1 = wins.get(0);
            for (int i=0; i<361; i++) {
                if (board.get(i) == PieceColor.EMPTY && i != p1) {
                    board.makeMove(new Move(p1, i));
                    boolean win = board.gameOver(); // 框架自带判断
                    if (!win) {
                        // 备用判断：如果 evaluate 极高
                        if (G09Board.evaluate(board, color) > G09Board.SCORE_WIN/2) win = true;
                    }
                    board.undo();
                    if (win) return new Move(p1, i);
                }
            }
        }
        return null;
    }

    // 获取所有有效的防守着法
    private List<Move> getDefensiveMoves(PieceColor opColor) {
        List<Move> validDefenses = new ArrayList<>();

        // 1. 识别威胁：对手哪里能赢？
        // 如果 quickEvaluatePoint(op) >= SCORE_DEAD_4，说明这里是敌人的必胜点
        List<Integer> threats = new ArrayList<>();
        for (int i = 0; i < 361; i++) {
            if (board.get(i) == PieceColor.EMPTY) {
                int s = G09Board.quickEvaluatePoint(board, i, opColor);
                if (s >= G09Board.SCORE_DEAD_4) {
                    threats.add(i);
                }
            }
        }

        if (threats.isEmpty()) return validDefenses;

        // 2. 生成防守候选项
        // 防守必须落在威胁点上，或者能通过进攻反杀(反杀很难，这里主要考虑堵截)
        // 策略：我们必须堵住威胁。如果威胁点很多(>2)，可能堵不住。

        // 收集所有可能的防守点：威胁点本身 + 邻域高分点
        List<Integer> defenseCandidates = new ArrayList<>(threats);
        // 再加几个我方高分点（用于进攻+防守）
        for(int i=0; i<361; i++) {
            if(board.get(i)==PieceColor.EMPTY && !defenseCandidates.contains(i)) {
                if(G09Board.quickEvaluatePoint(board, i, board.whoseMove()) >= G09Board.SCORE_LIVE_3)
                    defenseCandidates.add(i);
            }
        }

        // 排序，优先堵最危险的
        defenseCandidates.sort((a,b) -> G09Board.quickEvaluatePoint(board, b, opColor) - G09Board.quickEvaluatePoint(board, a, opColor));

        int limit = Math.min(defenseCandidates.size(), 8); // 不要太多

        // 3. 验证防守有效性
        // 对于每一个候选 Pair，模拟落子。
        // 落子后，再检查一遍对手是否还有 Win Threat。如果没有，才是有效防守。
        for (int i = 0; i < limit; i++) {
            for (int j = i + 1; j < limit; j++) {
                Move tryMove = new Move(defenseCandidates.get(i), defenseCandidates.get(j));
                board.makeMove(tryMove);

                boolean stillDie = false;
                // 检查对手是否还能赢
                // 快速检查：扫描所有空位，看对手是否有 >= DEAD_4 的点
                // 注意：这里需要再次调用 quickEvaluatePoint，比较耗时，但为了不输是值得的
                for (int k = 0; k < 361; k++) {
                    if (board.get(k) == PieceColor.EMPTY) {
                        // 只要对手还有任何一个点能成5或6，说明我没防住（或者他是双杀）
                        // 注意：这里阈值要稍微放宽，防止误判
                        if (G09Board.quickEvaluatePoint(board, k, opColor) >= G09Board.SCORE_DEAD_5) {
                            stillDie = true;
                            break;
                        }
                    }
                }

                board.undo();

                if (!stillDie) {
                    validDefenses.add(tryMove);
                }
            }
        }

        // 如果实在防不住（validDefenses为空），就返回空，交给外层随机或者进攻
        return validDefenses;
    }

    // --- VCT ---
    private Move searchVCT(int depth, PieceColor color) {
        if (depth <= 0) return null;

        // 简化版：只找能连续造Live3以上威胁的棋
        List<Integer> attacks = new ArrayList<>();
        for (int i = 0; i < 361; i++) {
            if (board.get(i) == PieceColor.EMPTY && hasNeighbor(i)) {
                if (G09Board.quickEvaluatePoint(board, i, color) >= G09Board.SCORE_LIVE_3) {
                    attacks.add(i);
                }
            }
        }
        if(attacks.isEmpty()) return null;

        attacks.sort((a,b) -> scoreCache[b] - scoreCache[a]);
        int limit = Math.min(attacks.size(), 5);

        for (int i=0; i<limit; i++) {
            for (int j=i+1; j<limit; j++) {
                Move m = new Move(attacks.get(i), attacks.get(j));
                board.makeMove(m);
                // 如果直接赢
                if (G09Board.evaluate(board, color) > G09Board.SCORE_WIN/2) {
                    board.undo(); return m;
                }
                // 如果造成巨大威胁 (Dead 4)，且深度允许，继续搜
                if (G09Board.evaluate(board, color) >= G09Board.SCORE_DEAD_4) {
                    Move next = searchVCT(depth-1, color);
                    if (next != null) {
                        board.undo(); return m;
                    }
                }
                board.undo();
            }
        }
        return null;
    }

    private boolean hasNeighbor(int idx) {
        int r = idx / 19, c = idx % 19;
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if(i==0 && j==0) continue;
                int nr=r+i, nc=c+j;
                if(nr>=0 && nr<19 && nc>=0 && nc<19 && board.get(nr*19+nc)!=PieceColor.EMPTY) return true;
            }
        }
        return false;
    }

    private Move getSafeRandomMove() {
        Random rand = new Random();
        for (int k=0; k<100; k++) {
            int i1 = rand.nextInt(361), i2 = rand.nextInt(361);
            if (i1!=i2 && board.get(i1)==PieceColor.EMPTY && board.get(i2)==PieceColor.EMPTY)
                return new Move(i1, i2);
        }
        return new Move(0, 1);
    }
}