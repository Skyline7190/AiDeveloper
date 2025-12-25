package stud.g09;

import core.board.Board;
import core.board.PieceColor;

public class G09Board {
    // --- 权重定义 ---
    // 逻辑：一手两子。
    // 对手有4子 -> 下一手加2子 -> 6子(赢)。所以“4子”是最高警戒级别。

    public static final int SCORE_WIN     = 100000000;
    public static final int SCORE_LIVE_5  = 10000000;  // 活五 (自己有必胜，对手有必堵)
    public static final int SCORE_DEAD_5  = 10000000;  // 死五 (同上，下一手成6)
    public static final int SCORE_LIVE_4  = 5000000;   // 活四 (下一手成6)
    public static final int SCORE_DEAD_4  = 5000000;   // 死四 (下一手成6)

    // 活三：威胁稍小，因为需要两手棋才能成6，或者一手棋成5。
    // 但在六子棋中，活三往往是做杀的基础。
    public static final int SCORE_LIVE_3  = 50000;
    public static final int SCORE_DEAD_3  = 2000;
    public static final int SCORE_LIVE_2  = 500;
    public static final int SCORE_DEAD_2  = 50;

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    /**
     * 全局估值：只计算基本棋型分
     */
    public static int evaluate(Board board, PieceColor myColor) {
        long myScore = 0;
        long opScore = 0;

        // 简化全盘扫描
        for (int i = 0; i < 361; i++) {
            if (board.get(i) == PieceColor.EMPTY) continue;
            PieceColor p = board.get(i);
            int r = i / 19, c = i % 19;
            for (int[] d : DIRS) {
                if (isLineStart(board, c, r, d, p)) {
                    int s = getLineScore(board, c, r, d, p);
                    if (p == myColor) myScore += s;
                    else opScore += s;
                }
            }
        }

        // 这里的防守系数 1.2 很重要，稍微偏向防守，避免互爆时算不过对手
        return (int)(myScore - opScore * 1.2);
    }

    /**
     * 快速评估单点价值（用于排序）
     * 返回该点能构成的最大棋型分数
     */
    public static int quickEvaluatePoint(Board board, int idx, PieceColor color) {
        int r = idx / 19, c = idx % 19;
        int maxScore = 0;

        for (int[] d : DIRS) {
            int score = 0;
            // 模拟在该点落子后，向两边延伸
            int count = 1;
            int emptySide = 0;

            // 正向
            int k = 1;
            while (true) {
                int nr = r + d[0]*k, nc = c + d[1]*k;
                if (!isValid(nr, nc)) break;
                PieceColor p = board.get(nr*19+nc);
                if (p == color) count++;
                else {
                    if (p == PieceColor.EMPTY) emptySide++;
                    break;
                }
                k++;
            }

            // 反向
            k = 1;
            while (true) {
                int nr = r - d[0]*k, nc = c - d[1]*k;
                if (!isValid(nr, nc)) break;
                PieceColor p = board.get(nr*19+nc);
                if (p == color) count++;
                else {
                    if (p == PieceColor.EMPTY) emptySide++;
                    break;
                }
                k++;
            }

            // 评分
            if (count >= 6) score = SCORE_WIN;
            else if (count == 5) score = (emptySide > 0) ? SCORE_LIVE_5 : SCORE_DEAD_5;
            else if (count == 4) score = (emptySide == 2) ? SCORE_LIVE_4 : (emptySide==1 ? SCORE_DEAD_4 : 0);
            else if (count == 3) score = (emptySide == 2) ? SCORE_LIVE_3 : (emptySide==1 ? SCORE_DEAD_3 : 0);
            else if (count == 2) score = (emptySide == 2) ? SCORE_LIVE_2 : 0;

            maxScore += score; // 累加所有方向的分数，体现“交叉点”威力
        }
        return maxScore;
    }

    private static boolean isLineStart(Board board, int c, int r, int[] d, PieceColor p) {
        int pc = c - d[0], pr = r - d[1];
        if (!isValid(pc, pr)) return true;
        return board.get(pr*19+pc) != p;
    }

    private static int getLineScore(Board board, int startC, int startR, int[] d, PieceColor p) {
        int count = 0;
        int c = startC, r = startR;
        while (isValid(c, r) && board.get(r*19+c) == p) {
            count++;
            c += d[0]; r += d[1];
        }
        int open = 0;
        if (isValid(c, r) && board.get(r*19+c) == PieceColor.EMPTY) open++;
        int hc = startC - d[0], hr = startR - d[1];
        if (isValid(hc, hr) && board.get(hr*19+hc) == PieceColor.EMPTY) open++;

        if (count >= 6) return SCORE_WIN;
        if (count == 5) return open > 0 ? SCORE_LIVE_5 : SCORE_DEAD_5;
        if (count == 4) return open == 2 ? SCORE_LIVE_4 : (open==1 ? SCORE_DEAD_4 : 0);
        if (count == 3) return open == 2 ? SCORE_LIVE_3 : (open==1 ? SCORE_DEAD_3 : 0);
        if (count == 2) return open == 2 ? SCORE_LIVE_2 : 0;
        return 0;
    }

    public static boolean isValid(int c, int r) {
        return c>=0 && c<19 && r>=0 && r<19;
    }
}