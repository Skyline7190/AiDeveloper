package stud.gStrategy3;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

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
        // 1: 空指针检查
        if (opponentMove != null) {
            this.board.makeMove(opponentMove);
        }

        Random rand = new Random();
        boolean isFirstMove = (countPieces() == 0);

        int index1 = -1;
        int index2 = -1;
        boolean foundInCenter = false;

        // --- 逻辑部分 ---
        // 尝试在中心区域
        for (int i = 0; i < 10; i++) {
            int temp1 = getRandomCenterIndex(rand);
            int temp2 = getRandomCenterIndex(rand);

            // 如果是第一手，只需要 temp1 有效即可
            if (isFirstMove) {
                if (this.board.get(temp1) == PieceColor.EMPTY) {
                    index1 = temp1;
                    index2 = temp1; // 落一子
                    foundInCenter = true;
                    break;
                }
            } else {
                if (temp1 != temp2 && this.board.get(temp1) == PieceColor.EMPTY && this.board.get(temp2) == PieceColor.EMPTY) {
                    index1 = temp1;
                    index2 = temp2;
                    foundInCenter = true;
                    break;
                }
            }
        }

        // 如果中心没找到，全盘随机
        if (!foundInCenter) {
            while (true) {
                int t1 = rand.nextInt(361);
                // 第一手特殊处理
                if (isFirstMove) {
                    if (this.board.get(t1) == PieceColor.EMPTY) {
                        index1 = t1;
                        index2 = t1;
                        break;
                    }
                } else {
                    int t2 = rand.nextInt(361);
                    if (t1 != t2 && this.board.get(t1) == PieceColor.EMPTY && this.board.get(t2) == PieceColor.EMPTY) {
                        index1 = t1;
                        index2 = t2;
                        break;
                    }
                }
            }
        }

        Move move = new Move(index1, index2);
        this.board.makeMove(move);
        return move;
    }

    private int getRandomCenterIndex(Random rand) {
        int r = rand.nextInt(13) + 3;
        int c = rand.nextInt(13) + 3;
        return r * 19 + c;
    }

    private int countPieces() {
        int count = 0;
        for (int i = 0; i < 361; i++) {
            if (this.board.get(i) != PieceColor.EMPTY) count++;
        }
        return count;
    }

    @Override
    public String name() {
        return "Strategy3-Center";
    }
}