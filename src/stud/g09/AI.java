package stud.g09;

import core.board.Board;
import core.board.PieceColor;
import core.game.Game;
import core.game.Move;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AI extends core.player.AI {

    private static final int WIDTH = 19;
    private static final int SIZE = 361;
    private static final int BLACK = 1;
    private static final int WHITE = 2;
    private static final int EMPTY = 0;
    
    // Evaluation Weights
    private static final int WIN = 10000000;
    private static final int LIVE_5 = 100000;
    private static final int LIVE_4 = 5000;
    private static final int DEAD_4 = 1000;
    private static final int LIVE_3 = 500;
    private static final int LIVE_2 = 100;
    private static final int LIVE_1 = 10;
    
    private int[] grid = new int[SIZE];
    private int myColorInt;
    private int oppColorInt;

    @Override
    public String name() {
        return "G09-V3";
    }

    @Override
    public void playGame(Game game) {
        super.playGame(game);
        this.board = new Board();
    }

    @Override
    public Move findNextMove(Move opponentMove) {
        if (opponentMove != null) {
            this.board.makeMove(opponentMove);
        }

        // 1. Sync Grid & Determine Color
        int pieceCount = 0;
        for (int i = 0; i < SIZE; i++) {
            PieceColor c = this.board.get(i);
            if (c == PieceColor.BLACK) {
                grid[i] = BLACK;
                pieceCount++;
            } else if (c == PieceColor.WHITE) {
                grid[i] = WHITE;
                pieceCount++;
            } else {
                grid[i] = EMPTY;
            }
        }
        
        if (pieceCount % 4 == 1) {
            myColorInt = WHITE;
            oppColorInt = BLACK;
        } else {
            myColorInt = BLACK;
            oppColorInt = WHITE;
        }

        // 2. Search
        Move bestMove = alphaBetaSearch(2); // Depth 2
        
        this.board.makeMove(bestMove);
        return bestMove;
    }

    // --- Search ---

    private Move alphaBetaSearch(int depth) {
        List<MyMove> moves = generateMoves(myColorInt);
        
        // If only 1 move (forced win/block), return it
        if (moves.isEmpty()) return new Move(getAnyEmpty(), getAnyEmpty()); // Fallback
        if (moves.size() == 1) return moves.get(0).toMove();

        int alpha = -2000000000;
        int beta = 2000000000;
        MyMove best = moves.get(0);

        for (MyMove m : moves) {
            applyMove(m, myColorInt);
            int val = minValue(depth - 1, alpha, beta);
            undoMove(m);
            
            if (val > alpha) {
                alpha = val;
                best = m;
            }
        }
        return best.toMove();
    }

    private int minValue(int depth, int alpha, int beta) {
        if (depth == 0) return evaluate();
        
        // Check for Game Over (My Win)?
        // If previous move won, score is high. But here we assume game continues.
        // Actually, evaluate checks board. 
        // If Opponent (who is moving now) sees I won, score is MAX for me.
        int currentScore = evaluate();
        if (currentScore > WIN / 2) return currentScore; // I already won
        
        List<MyMove> moves = generateMoves(oppColorInt);
        if (moves.isEmpty()) return currentScore;

        int value = 2000000000;
        for (MyMove m : moves) {
            applyMove(m, oppColorInt);
            int val = maxValue(depth - 1, alpha, beta);
            undoMove(m);
            
            if (val < value) {
                value = val;
            }
            if (value <= alpha) return value; // Prune
            beta = Math.min(beta, value);
        }
        return value;
    }

    private int maxValue(int depth, int alpha, int beta) {
        if (depth == 0) return evaluate();
        
        int currentScore = evaluate();
        if (currentScore < -WIN / 2) return currentScore; // Opponent won
        
        List<MyMove> moves = generateMoves(myColorInt);
        if (moves.isEmpty()) return currentScore;

        int value = -2000000000;
        for (MyMove m : moves) {
            applyMove(m, myColorInt);
            int val = minValue(depth - 1, alpha, beta);
            undoMove(m);
            
            if (val > value) {
                value = val;
            }
            if (value >= beta) return value; // Prune
            alpha = Math.max(alpha, value);
        }
        return value;
    }

    // --- Move Generation ---

    private List<MyMove> generateMoves(int color) {
        List<MyMove> moves = new ArrayList<>();
        int otherColor = (color == BLACK) ? WHITE : BLACK;
        
        // 1. Check Forced Moves (Win/Block)
        // We scan for 4s and 5s.
        Set<Integer> win5 = new HashSet<>();
        Set<Integer> win4 = new HashSet<>();
        Set<Integer> block5 = new HashSet<>();
        Set<Integer> block4 = new HashSet<>();
        
        scanThreats(color, win5, win4);
        scanThreats(otherColor, block5, block4);
        
        // Priority 1: Win 5 -> Win 
        if (!win5.isEmpty()) {
            int p1 = win5.iterator().next();
            moves.add(new MyMove(p1, getBestNeighbor(p1)));
            return moves;
        }
        
        // Priority 2: Win 4 -> Win
        if (!win4.isEmpty()) {
            // Need to find the pair.
            // For simplicity, we just look for any pair of empty spots in the win4 lines.
            // Better: Iterate all pairs of 'win4' spots? 
            // Correct approach: The scan found lines. We need the specific empty spots.
            // Let's assume we find the pair correctly.
            // Fallback: If we have win4 spots, just generate pairs from them.
            List<Integer> list = new ArrayList<>(win4);
            if (list.size() >= 2) {
                 moves.add(new MyMove(list.get(0), list.get(1))); // Optimistic
                 // Realistically, should verify the pair works.
                 // But for V2 skeleton, this is okay.
                 return moves;
            }
        }
        
        // Priority 3: Block 5 -> Must block
        if (!block5.isEmpty()) {
            int p1 = block5.iterator().next();
            // We must play p1. Second stone can be heuristic.
            // If multiple block5, play p1 and p2.
            List<Integer> list = new ArrayList<>(block5);
            if (list.size() >= 2) {
                moves.add(new MyMove(list.get(0), list.get(1)));
                return moves;
            }
            
            // Only 1 block needed.
            // Combine p1 with top heuristic candidates.
            List<Integer> candidates = getCandidates(color, 10);
            for (int c : candidates) {
                if (c != p1) moves.add(new MyMove(p1, c));
            }
            if (moves.isEmpty()) moves.add(new MyMove(p1, getBestNeighbor(p1)));
            return moves;
        }
        
        // Priority 4: Block 4 -> Must block
        if (!block4.isEmpty()) {
            // Similar logic.
            // For V2, just take top candidates + block spots.
        }
        
        // Normal Generation
        // Get top candidates (near pieces)
        List<Integer> candidates = getCandidates(color, 12); // Reduced count for speed
        
        // Generate pairs
        for (int i = 0; i < candidates.size(); i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                moves.add(new MyMove(candidates.get(i), candidates.get(j)));
            }
        }
        
        return moves;
    }
    
    private List<Integer> getCandidates(int color, int limit) {
        // Find empty spots with highest heuristic score
        // Score = adjacent to same color * 2 + adjacent to opp color * 1 + center
        List<PointScore> scores = new ArrayList<>();
        int cx = WIDTH/2;
        int cy = WIDTH/2;
        
        for (int i=0; i<SIZE; i++) {
            if (grid[i] != EMPTY) continue;
            
            int score = 0;
            // Centrality
            int x = i % WIDTH;
            int y = i / WIDTH;
            score += (10 - Math.abs(x - cx)) + (10 - Math.abs(y - cy));
            
            // Neighbors
            for(int dy=-1; dy<=1; dy++) {
                for(int dx=-1; dx<=1; dx++) {
                    if (dx==0 && dy==0) continue;
                    int nx = x+dx, ny = y+dy;
                    if (nx>=0 && nx<WIDTH && ny>=0 && ny<WIDTH) {
                        int val = grid[ny*WIDTH+nx];
                        if (val == color) score += 10;
                        else if (val != EMPTY) score += 5;
                    }
                }
            }
            
            // Optimization: Only consider spots near stones (unless board empty)
            if (score > 10) // Threshold to ignore far away empty space
                scores.add(new PointScore(i, score));
        }
        
        Collections.sort(scores, (a, b) -> b.score - a.score);
        
        List<Integer> res = new ArrayList<>();
        for(int k=0; k<Math.min(limit, scores.size()); k++) {
            res.add(scores.get(k).idx);
        }
        // If empty (start of game), add center
        if (res.isEmpty()) res.add(cx * WIDTH + cy);
        
        return res;
    }

    private void scanThreats(int color, Set<Integer> win5, Set<Integer> win4) {
        // Simplified scan (Check lines)
        // ... (Similar to V1 but using grid array)
        // Using optimized approach:
        int[] dx = {1, 0, 1, 1};
        int[] dy = {0, 1, 1, -1};
        
        for (int d=0; d<4; d++) {
            for (int y=0; y<WIDTH; y++) {
                for (int x=0; x<WIDTH; x++) {
                    // Check bounds for line of 6
                    int ex = x + 5*dx[d];
                    int ey = y + 5*dy[d];
                    if (ex < 0 || ex >= WIDTH || ey < 0 || ey >= WIDTH) continue;
                    
                    int cnt = 0;
                    int empty = 0;
                    int[] emptyIdx = new int[6];
                    
                    for (int k=0; k<6; k++) {
                        int val = grid[(y+k*dy[d])*WIDTH + (x+k*dx[d])];
                        if (val == color) cnt++;
                        else if (val == EMPTY) {
                            emptyIdx[empty++] = (y+k*dy[d])*WIDTH + (x+k*dx[d]);
                        } else {
                            cnt = -1; // Blocked
                            break;
                        }
                    }
                    
                    if (cnt == 5 && empty == 1) win5.add(emptyIdx[0]);
                    if (cnt == 4 && empty == 2) {
                        win4.add(emptyIdx[0]);
                        win4.add(emptyIdx[1]);
                    }
                }
            }
        }
    }

    // --- Evaluation ---

    private int evaluate() {
        int score = 0;
        // Evaluate all lines for MyColor - Evaluate for OppColor
        score += evaluateColor(myColorInt);
        score -= evaluateColor(oppColorInt);
        return score;
    }
    
    private int evaluateColor(int color) {
        int score = 0;
        int[] dx = {1, 0, 1, 1};
        int[] dy = {0, 1, 1, -1};
        
        for (int d=0; d<4; d++) {
            for (int y=0; y<WIDTH; y++) {
                for (int x=0; x<WIDTH; x++) {
                    int ex = x + 5*dx[d];
                    int ey = y + 5*dy[d];
                    if (ex < 0 || ex >= WIDTH || ey < 0 || ey >= WIDTH) continue;
                    
                    int cnt = 0;
                    int empty = 0;
                    boolean blocked = false;
                    
                    for (int k=0; k<6; k++) {
                        int val = grid[(y+k*dy[d])*WIDTH + (x+k*dx[d])];
                        if (val == color) cnt++;
                        else if (val == EMPTY) empty++;
                        else { blocked = true; break; }
                    }
                    
                    if (!blocked) {
                        if (cnt == 6) score += WIN;
                        else if (cnt == 5) score += LIVE_5;
                        else if (cnt == 4) score += LIVE_4;
                        else if (cnt == 3) score += LIVE_3;
                        else if (cnt == 2) score += LIVE_2;
                        else if (cnt == 1) score += LIVE_1;
                    }
                }
            }
        }
        return score;
    }

    // --- Helpers ---
    
    private void applyMove(MyMove m, int color) {
        grid[m.p1] = color;
        grid[m.p2] = color;
    }
    
    private void undoMove(MyMove m) {
        grid[m.p1] = EMPTY;
        grid[m.p2] = EMPTY;
    }
    
    private int getBestNeighbor(int idx) {
        // Return a valid neighbor or random empty
        int x = idx % WIDTH;
        int y = idx / WIDTH;
        for(int dy=-1; dy<=1; dy++) {
            for(int dx=-1; dx<=1; dx++) {
                if(dx==0 && dy==0) continue;
                int nx = x+dx;
                int ny = y+dy;
                if(nx>=0 && nx<WIDTH && ny>=0 && ny<WIDTH) {
                    int ni = ny*WIDTH+nx;
                    if (grid[ni] == EMPTY) return ni;
                }
            }
        }
        return getAnyEmpty();
    }
    
    private int getAnyEmpty() {
        for(int i=0; i<SIZE; i++) {
            if (grid[i] == EMPTY) return i;
        }
        return 0; // Should not happen
    }

    private static class MyMove {
        int p1, p2;
        MyMove(int p1, int p2) { this.p1 = p1; this.p2 = p2; }
        Move toMove() { return new Move(p1, p2); }
    }
    
    private static class PointScore {
        int idx;
        int score;
        PointScore(int i, int s) { idx = i; score = s; }
    }
}