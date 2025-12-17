package stud.g99;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.Random;

public class AI extends core.player.AI {
    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);
        Random rand = new Random();
        while (true) {
            int index1 = rand.nextInt(179);
            int index2 = rand.nextInt(179);
            if (index1 != index2 && this.board.get(index1) == PieceColor.EMPTY && this.board.get(index2) == PieceColor.EMPTY) {
                Move move = new Move(index1, index2);
                this.board.makeMove(move);
                return move;
            }
        }
    }

    public String name() {
        return "G99";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new Board();
    }
}
