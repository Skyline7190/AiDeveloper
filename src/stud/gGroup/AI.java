package stud.gGroup;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.ArrayList;
import java.util.Collections;

public class AI extends core.player.AI {

    private ArrayList<Integer> shuffledIndices;

    @Override
    public String name() {
        return "Group-Shuffle";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        // 1. 重置棋盘 (关键)
        this.board = new Board();

        // 2. 初始化并洗牌
        shuffledIndices = new ArrayList<>(361);
        for (int i = 0; i < 361; i++) {
            shuffledIndices.add(i);
        }
        Collections.shuffle(shuffledIndices);
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        if (opponentMove != null) {
            this.board.makeMove(opponentMove);
        }

        // 始终获取两个有效空位，无需特殊处理第一手
        int index1 = getNextValidEmptyIndex();
        int index2 = getNextValidEmptyIndex();

        Move move = new Move(index1, index2);
        this.board.makeMove(move);
        return move;
    }

    private int getNextValidEmptyIndex() {
        while (!shuffledIndices.isEmpty()) {
            int index = shuffledIndices.remove(shuffledIndices.size() - 1);
            if (this.board.get(index) == PieceColor.EMPTY) {
                return index;
            }
        }
        return -1;
    }
}