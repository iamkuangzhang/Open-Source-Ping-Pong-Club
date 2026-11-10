package org.opensourcepingpong.competition.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScoreTemplateTest {
    private static ScoreTemplate.Game game(int a, int b) {
        return new ScoreTemplate.Game(a, b);
    }

    @Test
    void tableTennisRequiresTwoPointMargin() {
        assertThrows(IllegalArgumentException.class, () -> ScoreTemplate.matchWinner(
            Sport.TABLE_TENNIS, 3, List.of(game(11, 10), game(11, 8))));
        assertEquals(0, ScoreTemplate.matchWinner(
            Sport.TABLE_TENNIS, 3, List.of(game(11, 9), game(11, 7))));
        assertEquals(1, ScoreTemplate.matchWinner(
            Sport.TABLE_TENNIS, 5, List.of(game(9, 11), game(11, 9), game(7, 11), game(11, 8), game(9, 11))));
    }

    @Test
    void incompleteAndExtraGamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ScoreTemplate.matchWinner(
            Sport.TABLE_TENNIS, 3, List.of(game(11, 9))));
        assertThrows(IllegalArgumentException.class, () -> ScoreTemplate.matchWinner(
            Sport.TABLE_TENNIS, 3, List.of(game(11, 9), game(11, 9), game(11, 9))));
    }

    @Test
    void badmintonUsesTwentyOnePointGamesAndThirtyPointCap() {
        assertThrows(IllegalArgumentException.class, () -> ScoreTemplate.matchWinner(
            Sport.BADMINTON, 3, List.of(game(21, 20), game(21, 19))));
        assertEquals(0, ScoreTemplate.matchWinner(
            Sport.BADMINTON, 3, List.of(game(21, 19), game(21, 18))));
        assertEquals(0, ScoreTemplate.matchWinner(
            Sport.BADMINTON, 3, List.of(game(30, 29), game(21, 19))));
    }

    @Test
    void volleyballUsesTwentyFivePointSetsAndFifteenPointDecider() {
        assertEquals(0, ScoreTemplate.matchWinner(
            Sport.VOLLEYBALL, 5, List.of(game(25, 23), game(25, 20), game(25, 22))));
        assertThrows(IllegalArgumentException.class, () -> ScoreTemplate.matchWinner(
            Sport.VOLLEYBALL, 5,
            List.of(game(25, 23), game(25, 23), game(25, 23), game(25, 23), game(15, 14))));
        assertEquals(1, ScoreTemplate.matchWinner(
            Sport.VOLLEYBALL, 5,
            List.of(game(23, 25), game(25, 20), game(20, 25), game(25, 18), game(13, 15))));
    }

    @Test
    void basketballAndFootballUseOneTotalScore() {
        assertEquals(0, ScoreTemplate.matchWinner(Sport.BASKETBALL, 3, List.of(game(88, 80))));
        assertEquals(1, ScoreTemplate.matchWinner(Sport.FOOTBALL, 3, List.of(game(1, 2))));
        assertThrows(IllegalArgumentException.class, () -> ScoreTemplate.matchWinner(
            Sport.BASKETBALL, 3, List.of(game(80, 80))));
        assertThrows(IllegalArgumentException.class, () -> ScoreTemplate.matchWinner(
            Sport.FOOTBALL, 3, List.of(game(1, 0), game(0, 1))));
    }
}
