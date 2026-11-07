package org.opensourcepingpong.competition.algorithm;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GroupAllocatorTest {
  @Test void twoPlayerRoundRobinHasOneMatch() {
    assertEquals(1, RoundRobinScheduleGenerator.generate(List.of(1, 2)).getFirst().size());
  }
  @Test void randomAllocationIsBalancedAndReproducible() {
    List<Integer> players = IntStream.range(1, 14).boxed().toList();
    var first = RandomGroupAllocator.allocate(players, 4, 77);
    for (int i = 0; i < 100; i++) assertEquals(first, RandomGroupAllocator.allocate(players, 4, 77));
    assertEquals(players.size(), first.stream().mapToInt(List::size).sum());
    assertEquals(players.size(), first.stream().flatMap(List::stream).distinct().count());
    assertEquals(1, first.stream().mapToInt(List::size).max().orElseThrow() - first.stream().mapToInt(List::size).min().orElseThrow());
  }

  @Test void ratingAllocationSnakesAndPutsUnratedLast() {
    List<Integer> players = IntStream.rangeClosed(1, 8).boxed().toList();
    Map<Integer,Integer> ratings = new HashMap<>();
    for (int i = 1; i <= 7; i++) ratings.put(i, 1600 - i * 10);
    var groups = RatingBalancedGroupAllocator.allocate(players, ratings, 2);
    assertEquals(List.of(1, 4, 5, 8), groups.get(0));
    assertEquals(List.of(2, 3, 6, 7), groups.get(1));
  }

  @Test void invalidInputsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> RandomGroupAllocator.allocate(List.of(1, 1), 2, 1));
    assertThrows(IllegalArgumentException.class, () -> RatingBalancedGroupAllocator.allocate(List.of(1), Map.of(), 2));
  }
}
