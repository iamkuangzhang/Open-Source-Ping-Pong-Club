package org.opensourcepingpong.competition.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AlgorithmsTest {
  @Test
  void knockoutSizesProduceExpectedByesAndUniqueParticipants() {
    for (int count : new int[] {2, 3, 4, 5, 7, 8, 9, 13, 16, 17, 31, 32, 33, 64}) {
      List<Integer> participants = participants(count);
      int bracketSize = SingleEliminationBracketGenerator.bracketSize(count);
      var slots = SingleEliminationBracketGenerator.arrange(participants, 9);

      assertTrue(bracketSize >= count);
      assertEquals(bracketSize, slots.size());
      assertEquals(bracketSize - count, slots.stream().filter(Objects::isNull).count());
      assertEquals(count, new HashSet<>(slots.stream().filter(Objects::nonNull).toList()).size());
    }
  }

  @Test
  void knockoutRandomSeedProducesSameResultOneHundredTimes() {
    List<Integer> participants = participants(32);
    var expected = SingleEliminationBracketGenerator.arrange(participants, 2026L);

    for (int iteration = 0; iteration < 100; iteration++) {
      assertEquals(expected, SingleEliminationBracketGenerator.arrange(participants, 2026L));
    }
  }

  @Test
  void knockoutArrangementRejectsInvalidParticipantCountsAndDuplicates() {
    assertThrows(IllegalArgumentException.class,
        () -> SingleEliminationBracketGenerator.bracketSize(0));
    assertThrows(IllegalArgumentException.class,
        () -> SingleEliminationBracketGenerator.arrange(List.of(1), 1));
    assertThrows(IllegalArgumentException.class,
        () -> SingleEliminationBracketGenerator.arrange(List.of(1, 1), 1));
  }

  @Test
  void roundRobinPairsEveryParticipantExactlyOncePerRound() {
    for (int count : new int[] {3, 4, 5, 6, 7, 8}) {
      var rounds = RoundRobinScheduleGenerator.generate(participants(count));
      Set<String> pairs = new HashSet<>();

      for (var round : rounds) {
        Set<Integer> appeared = new HashSet<>();
        for (var pair : round) {
          assertTrue(appeared.add(pair.a()));
          assertTrue(appeared.add(pair.b()));
          pairs.add(Math.min(pair.a(), pair.b()) + ":" + Math.max(pair.a(), pair.b()));
        }
      }

      assertEquals(count * (count - 1) / 2, pairs.size());
    }
  }

  @Test
  void roundRobinRejectsLessThanTwoParticipantsAndDuplicates() {
    assertThrows(IllegalArgumentException.class, () -> RoundRobinScheduleGenerator.generate(List.of(1)));
    assertThrows(IllegalArgumentException.class, () -> RoundRobinScheduleGenerator.generate(List.of(1, 1)));
  }

  @Test
  void validAndInvalidSingleGameScoresAreClassified() {
    for (var game : List.of(
        new ScoreValidator.Game(11, 0),
        new ScoreValidator.Game(11, 9),
        new ScoreValidator.Game(12, 10),
        new ScoreValidator.Game(15, 13),
        new ScoreValidator.Game(0, 11),
        new ScoreValidator.Game(9, 11),
        new ScoreValidator.Game(10, 12))) {
      assertTrue(ScoreValidator.winner(game) == 0 || ScoreValidator.winner(game) == 1);
    }
    for (var game : List.of(
        new ScoreValidator.Game(10, 8),
        new ScoreValidator.Game(11, 10),
        new ScoreValidator.Game(12, 11),
        new ScoreValidator.Game(-1, 11),
        new ScoreValidator.Game(11, -1))) {
      assertThrows(IllegalArgumentException.class, () -> ScoreValidator.winner(game));
    }
  }

  @Test
  void matchWinnerSupportsBestOfThreeFiveAndSeven() {
    assertEquals(0, ScoreValidator.matchWinner(3, List.of(
        new ScoreValidator.Game(11, 9), new ScoreValidator.Game(12, 10))));
    assertEquals(1, ScoreValidator.matchWinner(5, List.of(
        new ScoreValidator.Game(9, 11), new ScoreValidator.Game(9, 11), new ScoreValidator.Game(9, 11))));
    assertEquals(0, ScoreValidator.matchWinner(7, List.of(
        new ScoreValidator.Game(11, 9), new ScoreValidator.Game(9, 11),
        new ScoreValidator.Game(11, 9), new ScoreValidator.Game(9, 11),
        new ScoreValidator.Game(11, 9), new ScoreValidator.Game(12, 10))));
  }

  @Test
  void matchWinnerRejectsInvalidBestOfIncompleteAndExtraGames() {
    assertThrows(IllegalArgumentException.class,
        () -> ScoreValidator.matchWinner(4, List.of(new ScoreValidator.Game(11, 9))));
    assertThrows(IllegalArgumentException.class,
        () -> ScoreValidator.matchWinner(3, List.of(new ScoreValidator.Game(11, 9))));
    assertThrows(IllegalArgumentException.class, () -> ScoreValidator.matchWinner(3, List.of(
        new ScoreValidator.Game(11, 9), new ScoreValidator.Game(11, 9), new ScoreValidator.Game(11, 9))));
  }

  private static List<Integer> participants(int count) {
    List<Integer> participants = new ArrayList<>();
    for (int participant = 0; participant < count; participant++) {
      participants.add(participant);
    }
    return participants;
  }
}
