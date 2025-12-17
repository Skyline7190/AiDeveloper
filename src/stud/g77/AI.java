package stud.g77;

import core.board.PieceColor;
import core.game.Game;
import core.game.Move;

import java.util.Random;

public class AI extends core.player.AI {
    private int steps = 0;

    @Override
    public Move findNextMove(Move opponentMove) {
        this.board.makeMove(opponentMove);
        Random rand = new Random();
        while (true) {
            int index1 = rand.nextInt(169);
            int index2 = rand.nextInt(169);
            if (index1 != index2 && this.board.get(index1) == PieceColor.EMPTY && this.board.get(index2) == PieceColor.EMPTY) {
                Move move = new Move(index1, index2);
                this.board.makeMove(move);
                steps++;
                return move;
            }
        }
    }

    public String name() {
        return "G77";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        board = new G77Board("G77Board");
        steps = 0;
    }
}
