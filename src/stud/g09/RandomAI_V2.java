package stud.g09;

import core.game.Move;
import core.board.PieceColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomAI_V2 extends core.player.AI {
    protected Random rand = new Random();

    @Override
    public Move findNextMove(Move opponentMove) {
        board.makeMove(opponentMove);

        // 1. 第一子：全盘随机
        int idx1;
        do {
            idx1 = rand.nextInt(361);
        } while (board.get(idx1) != PieceColor.EMPTY);

        // 2. 第二子：尝试在 idx1 周围 8 格找
        List<Integer> neighbors = new ArrayList<>();
        int r = idx1 / 19;
        int c = idx1 % 19;

        // 遍历周围 3x3 区域
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue; // 跳过中心自己
                int nr = r + i;
                int nc = c + j;
                if (nr >= 0 && nr < 19 && nc >= 0 && nc < 19) {
                    int nIdx = nr * 19 + nc;
                    if (board.get(nIdx) == PieceColor.EMPTY) {
                        neighbors.add(nIdx);
                    }
                }
            }
        }

        int idx2;
        if (!neighbors.isEmpty()) {
            // 如果周围有空位，随机选一个
            idx2 = neighbors.get(rand.nextInt(neighbors.size()));
        } else {
            // 否则全盘随机
            do {
                idx2 = rand.nextInt(361);
            } while (idx2 == idx1 || board.get(idx2) != PieceColor.EMPTY);
        }

        Move move = new Move(idx1, idx2);
        board.makeMove(move);
        return move;
    }

    @Override
    public String name() {
        return "G09-RandomV2";
    }
}