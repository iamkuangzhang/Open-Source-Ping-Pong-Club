package org.opensourcepingpong.competition.scoring;

import java.util.List;

/**
 * Validates a match score against the template of its sport.
 *
 * <p>Table tennis follows the configured best-of (3/5/7) with 11-point games.
 * Badminton is best-of-3 to 21 with the 30-point cap, volleyball best-of-5 with
 * 25-point sets and a 15-point deciding set, and basketball/football use one
 * total-score game. An {@link IllegalArgumentException} is translated by the
 * controller into HTTP 422 {@code MATCH_INVALID_SCORE}.
 */
public final class ScoreTemplate {
    public record Game(int a, int b) {}

    public record Rule(
        Sport sport,
        int bestOf,
        int gamesToWin,
        int pointsToWin,
        int winBy,
        int decidingGame,
        int decidingPoints,
        Integer pointsCap,
        boolean totalScore
    ) {}

    private ScoreTemplate() {}

    public static Rule rule(Sport sport, int configuredBestOf) {
        return switch (sport) {
            case TABLE_TENNIS -> {
                int bestOf = configuredBestOf == 3 || configuredBestOf == 5 || configuredBestOf == 7
                    ? configuredBestOf : 3;
                yield new Rule(sport, bestOf, bestOf / 2 + 1, 11, 2, 0, 11, null, false);
            }
            case BADMINTON -> new Rule(sport, 3, 2, 21, 2, 0, 21, 30, false);
            case VOLLEYBALL -> new Rule(sport, 5, 3, 25, 2, 5, 15, null, false);
            case BASKETBALL, FOOTBALL -> new Rule(sport, 1, 1, 0, 0, 0, 0, null, true);
        };
    }

    public static int gameWinner(Rule rule, int gameNumber, Game game) {
        if (game.a() < 0 || game.b() < 0) throw invalid("score cannot be negative");
        if (rule.totalScore()) {
            if (game.a() == game.b()) throw invalid("basketball/football cannot end in a draw");
            return game.a() > game.b() ? 0 : 1;
        }
        int target = gameNumber == rule.decidingGame() ? rule.decidingPoints() : rule.pointsToWin();
        int high = Math.max(game.a(), game.b());
        int difference = Math.abs(game.a() - game.b());
        boolean normalWin = high >= target && difference >= rule.winBy();
        boolean capWin = rule.pointsCap() != null && high >= rule.pointsCap();
        if (!normalWin && !capWin) {
            throw invalid("game " + gameNumber + " does not satisfy the scoring template");
        }
        return game.a() > game.b() ? 0 : 1;
    }

    public static int matchWinner(Sport sport, int configuredBestOf, List<Game> games) {
        if (games == null || games.isEmpty()) throw invalid("at least one game is required");
        Rule rule = rule(sport, configuredBestOf);
        if (rule.totalScore()) {
            if (games.size() != 1) throw invalid("expected exactly one total-score game");
            return gameWinner(rule, 1, games.getFirst());
        }
        int aWins = 0;
        int bWins = 0;
        for (int index = 0; index < games.size(); index++) {
            if (aWins == rule.gamesToWin() || bWins == rule.gamesToWin()) {
                throw invalid("extra game after the match was decided");
            }
            if (gameWinner(rule, index + 1, games.get(index)) == 0) aWins++;
            else bWins++;
        }
        if (aWins != rule.gamesToWin() && bWins != rule.gamesToWin()) {
            throw invalid("match is not complete");
        }
        return aWins > bWins ? 0 : 1;
    }

    public static int gameWins(List<Game> games, int slot) {
        int wins = 0;
        for (Game game : games) {
            boolean aWon = game.a() > game.b();
            if (aWon == (slot == 0)) wins++;
        }
        return wins;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
