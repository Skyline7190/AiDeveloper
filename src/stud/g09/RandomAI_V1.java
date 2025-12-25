package stud.g09;

import core.game.Move;
import core.board.PieceColor;
import java.util.Random;

public class RandomAI_V1 extends core.player.AI {
    protected Random rand = new Random();

    @Override
    public Move findNextMove(Move opponentMove) {
        board.makeMove(opponentMove); // 更新对方的着法

        int idx1, idx2;
        // 第一子：全盘随机找空位
        do {
            idx1 = rand.nextInt(361); // 19*19 = 361
        } while (board.get(idx1) != PieceColor.EMPTY);

        // 第二子：全盘随机找空位（不能覆盖第一子）
        do {
            idx2 = rand.nextInt(361);
        } while (idx2 == idx1 || board.get(idx2) != PieceColor.EMPTY);

        Move move = new Move(idx1, idx2);
        board.makeMove(move); // 更新自己的着法
        return move;
    }

    @Override
    public String name() {
        return "G09-RandomV1";
    }
}