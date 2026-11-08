package org.opensourcepingpong.competition.algorithm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AlgorithmsTest {
  private static final int[] KNOCKOUT_SIZES = {2, 3, 4, 5, 7, 8, 9, 13, 16, 17, 31, 32, 33, 64};

  @Test
  void knockoutSizesProduceExpectedByesAndUniqueParticipants() {
    for (int count : KNOCKOUT_SIZES) {
      List<Integer> participants = participants(count);
      int bracketSize = SingleEliminationBracketGenerator.bracketSize(count);
      var slots = SingleEliminationBracketGenerator.arrange(participants, 9);

      assertEquals(expectedBracketSize(count), bracketSize);
      assertEquals(bracketSize, slots.size());
      assertEquals(bracketSize - count, slots.stream().filter(Objects::isNull).count());
      assertEquals(count, new HashSet<>(slots.stream().filter(Objects::nonNull).toList()).size());
      for (int match = 0; match < bracketSize / 2; match++) {
        assertTrue(slots.get(match * 2) != null || slots.get(match * 2 + 1) != null,
            "count " + count + ": first round match " + match + " must not contain two byes");
      }
      assertDoesNotThrow(() -> SingleEliminationBracketGenerator.validate(slots, count));
    }
  }

  @Test
  void standardSeedPositionsMatchCompetitionRulesBitmap() {
    assertEquals(List.of(1, 2), SingleEliminationBracketGenerator.seedPositions(2));
    assertEquals(List.of(1, 4, 2, 3), SingleEliminationBracketGenerator.seedPositions(4));
    assertEquals(List.of(1, 8, 4, 5, 2, 7, 3, 6), SingleEliminationBracketGenerator.seedPositions(8));
  }

  @Test
  void fullProgressionLeavesExactlyOneChampionWhoAdvancedLog2BracketSizeRounds() {
    for (int count : KNOCKOUT_SIZES) {
      var slots = SingleEliminationBracketGenerator.arrange(participants(count), 2026L);
      int bracketSize = SingleEliminationBracketGenerator.bracketSize(count);
      int rounds = Integer.numberOfTrailingZeros(bracketSize);
      Map<Integer, Integer> roundsAdvanced = new HashMap<>();
      List<Integer> current = new ArrayList<>(slots);
      List<Integer> matchesPerRound = new ArrayList<>();

      while (current.size() > 1) {
        List<Integer> next = new ArrayList<>();
        for (int match = 0; match < current.size(); match += 2) {
          Integer playerA = current.get(match);
          Integer playerB = current.get(match + 1);
          Integer winner = playerA != null ? playerA : playerB; // BYE 自动胜，否则 slotA 晋级
          assertNotNull(winner, "count " + count + ": no match may contain two byes");
          roundsAdvanced.merge(winner, 1, Integer::sum);
          next.add(winner);
        }
        matchesPerRound.add(next.size());
        current = next;
      }

      assertEquals(rounds, matchesPerRound.size());
      assertEquals(1, matchesPerRound.getLast(), "count " + count + ": the final is the only match of its round");
      assertEquals(1, current.size());
      assertNotNull(current.get(0));
      assertEquals(rounds, roundsAdvanced.get(current.get(0)));
      assertEquals(1, roundsAdvanced.values().stream().filter(advanced -> advanced == rounds).count());
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
  void sameSeedReproducesIdenticalSlotsForEveryBracketSize() {
    for (int count : KNOCKOUT_SIZES) {
      var first = SingleEliminationBracketGenerator.arrange(participants(count), 7L);
      var second = SingleEliminationBracketGenerator.arrange(participants(count), 7L);

      assertEquals(first.size(), second.size());
      for (int slot = 0; slot < first.size(); slot++) {
        assertEquals(first.get(slot), second.get(slot),
            "count " + count + ": slot " + slot + " must reproduce with the same seed");
      }
    }
  }

  @Test
  void differentSeedsProduceDifferentArrangementsWithHighProbability() {
    List<Integer> participants = participants(17);
    var baseline = SingleEliminationBracketGenerator.arrange(participants, 1L);

    boolean differs = false;
    for (long seed = 2; seed <= 51 && !differs; seed++) {
      differs = !baseline.equals(SingleEliminationBracketGenerator.arrange(participants, seed));
    }
    assertTrue(differs);
  }

  @Test
  void realParticipantsReceiveAtMostOneFirstRoundBye() {
    for (int count : new int[] {3, 5, 9, 13, 17, 31, 33}) {
      var slots = SingleEliminationBracketGenerator.arrange(participants(count), 11L);
      for (int match = 0; match < slots.size() / 2; match++) {
        int byesInMatch = (slots.get(match * 2) == null ? 1 : 0) + (slots.get(match * 2 + 1) == null ? 1 : 0);
        assertTrue(byesInMatch <= 1,
            "count " + count + ": a real participant must receive at most one first round bye");
      }
      assertDoesNotThrow(() -> SingleEliminationBracketGenerator.validate(slots, count));
    }
  }

  @Test
  void validateRejectsDuplicatesDoubleByesAndWrongBracketSize() {
    assertDoesNotThrow(() -> SingleEliminationBracketGenerator.validate(Arrays.asList(1, null, 2, 3), 3));
    assertThrows(IllegalArgumentException.class,
        () -> SingleEliminationBracketGenerator.validate(Arrays.asList(1, null, 1, 2), 3));
    assertThrows(IllegalArgumentException.class,
        () -> SingleEliminationBracketGenerator.validate(Arrays.asList(1, 2, 3, 4, 5, null, null, null), 5));
    assertThrows(IllegalArgumentException.class,
        () -> SingleEliminationBracketGenerator.validate(Arrays.asList(1, null), 3));
  }

  @Test
  void knockoutArrangementRejectsInvalidParticipantCountsAndDuplicates() {
    assertThrows(IllegalArgumentException.class,
        () -> SingleEliminationBracketGenerator.bracketSize(0));
    assertThrows(IllegalArgumentException.class,
        () -> SingleEliminationBracketGenerator.bracketSize(1));
    assertThrows(IllegalArgumentException.class,
        () -> SingleEliminationBracketGenerator.bracketSize(65));
    assertThrows(IllegalArgumentException.class,
        () -> SingleEliminationBracketGenerator.arrange(List.of(1), 1));
    assertThrows(IllegalArgumentException.class,
        () -> SingleEliminationBracketGenerator.arrange(participants(65), 1));
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

  private static int expectedBracketSize(int count) {
    int size = 1;
    while (size < count) {
      size *= 2;
    }
    return size;
  }

  private static List<Integer> participants(int count) {
    List<Integer> participants = new ArrayList<>();
    for (int participant = 0; participant < count; participant++) {
      participants.add(participant);
    }
    return participants;
  }
}
