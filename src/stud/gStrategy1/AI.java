package stud.gStrategy1;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.Random;

/**
 * 走法 1: 全盘随机策略 (替代有问题的 G88)
 * 两个子的位置均通过随机掷骰子的方式确定，在整个棋盘（19 * 19）范围内掷骰子。
 * * 修正注：移除“第一手落一子”的特殊逻辑，始终返回两个不同的有效空位，
 * 由游戏引擎自动处理第一手只取一子的规则。
 */
public class AI extends core.player.AI {
    @Override
    public void playGame(Game game) {
        super.playGame(game);
        // 关键修复：每局开始前必须重置棋盘，否则会沿用上一局的棋盘状态导致死循环
        this.board = new Board();
    }
    @Override
    public Move findNextMove(Move opponentMove) {
        // 1. 必须处理 opponentMove，否则棋盘状态不对
        if (opponentMove != null) {
            this.board.makeMove(opponentMove);
        }

        Random rand = new Random();
        int boardSize = 19;
        int totalSpots = boardSize * boardSize;

        // --- 寻找第一个空位 ---
        int index1;
        while (true) {
            index1 = rand.nextInt(totalSpots); // 0-360
            if (this.board.get(index1) == PieceColor.EMPTY) {
                break;
            }
        }

        // --- 寻找第二个空位 ---
        // 必须与 index1 不同，且为空
        int index2;
        while (true) {
            index2 = rand.nextInt(totalSpots);
            if (index2 != index1 && this.board.get(index2) == PieceColor.EMPTY) {
                break;
            }
        }

        Move move = new Move(index1, index2);
        this.board.makeMove(move);
        return move;
    }

    @Override
    public String name() {
        return "Strategy1-Random";
    }
}