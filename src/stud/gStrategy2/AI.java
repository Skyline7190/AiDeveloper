package stud.gStrategy2;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AI extends core.player.AI {
    @Override
    public void playGame(Game game) {
        super.playGame(game);
        // 每局开始前必须重置棋盘，否则会沿用上一局的棋盘状态导致死循环
        this.board = new Board();
    }
    @Override
    public Move findNextMove(Move opponentMove) {
        // 1: 增加空指针检查，防止作为先手时第一回合崩溃
        if (opponentMove != null) {
            this.board.makeMove(opponentMove);
        }

        Random rand = new Random();
        int boardSize = 19;
        int totalSpots = boardSize * boardSize;

        // 检查是否是全盘第一手棋 (棋盘为空)
        boolean isFirstMove = (countPieces() == 0);

        // --- 第一步：确定第一个子 ---
        int index1;
        while (true) {
            index1 = rand.nextInt(totalSpots);
            if (this.board.get(index1) == PieceColor.EMPTY) {
                break;
            }
        }

        // 2: 如果是开局第一手，只落一子 (通过将 index2 设为 index1 或 -1，视具体引擎实现而定)
        // 这里采用两个相同坐标，许多引擎会将其视为落一子
        if (isFirstMove) {
            Move move = new Move(index1, index1);
            this.board.makeMove(move);
            return move;
        }

        // --- 第二步：确定第二个子 (正常逻辑) ---
        int index2 = -1;
        List<Integer> emptyNeighbors = getEmptyNeighbors(index1);

        if (!emptyNeighbors.isEmpty()) {
            index2 = emptyNeighbors.get(rand.nextInt(emptyNeighbors.size()));
        } else {
            while (true) {
                index2 = rand.nextInt(totalSpots);
                if (index2 != index1 && this.board.get(index2) == PieceColor.EMPTY) {
                    break;
                }
            }
        }

        Move move = new Move(index1, index2);
        this.board.makeMove(move);
        return move;
    }

    // 辅助方法：统计棋盘棋子数
    private int countPieces() {
        int count = 0;
        for (int i = 0; i < 361; i++) {
            if (this.board.get(i) != PieceColor.EMPTY) count++;
        }
        return count;
    }

    private List<Integer> getEmptyNeighbors(int centerIndex) {
        List<Integer> neighbors = new ArrayList<>();
        int r = centerIndex / 19;
        int c = centerIndex % 19;
        for (int i = r - 1; i <= r + 1; i++) {
            for (int j = c - 1; j <= c + 1; j++) {
                if (i >= 0 && i < 19 && j >= 0 && j < 19) {
                    int neighborIndex = i * 19 + j;
                    if (neighborIndex != centerIndex && this.board.get(neighborIndex) == PieceColor.EMPTY) {
                        neighbors.add(neighborIndex);
                    }
                }
            }
        }
        return neighbors;
    }

    @Override
    public String name() {
        return "Strategy2-Adjacency";
    }
}