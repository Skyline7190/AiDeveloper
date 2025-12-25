package stud.g09;

import core.game.Move;
import core.board.PieceColor;
import java.util.Random;

public class RandomAI_V3 extends core.player.AI {
    protected Random rand = new Random();

    @Override
    public Move findNextMove(Move opponentMove) {
        board.makeMove(opponentMove);

        int idx1 = getCenterOrRandom();
        int idx2;
        do {
            idx2 = getCenterOrRandom();
        } while (idx2 == idx1); // 保证两子不同

        Move move = new Move(idx1, idx2);
        board.makeMove(move);
        return move;
    }

    private int getCenterOrRandom() {
        // 尝试 10 次在中心 13x13 区域 (行列范围 3-15)
        for (int i = 0; i < 10; i++) {
            int r = 3 + rand.nextInt(13); // 3 到 15
            int c = 3 + rand.nextInt(13);
            int idx = r * 19 + c;
            if (board.get(idx) == PieceColor.EMPTY) {
                return idx;
            }
        }
        // 失败则全盘找
        int idx;
        do {
            idx = rand.nextInt(361);
        } while (board.get(idx) != PieceColor.EMPTY);
        return idx;
    }

    @Override
    public String name() {
        return "G09-RandomV3";
    }
}