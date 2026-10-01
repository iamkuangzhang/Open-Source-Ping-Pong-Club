package org.opensourcepingpong.rating.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RatingCalculatorTest {
  @Test
  void exactTableValuesAreAppliedAtEveryBoundary() {
    int[][] cases = {
        {0, 8, 8}, {12, 8, 8}, {13, 7, 10}, {37, 7, 10},
        {38, 6, 13}, {62, 6, 13}, {63, 5, 16}, {87, 5, 16},
        {88, 4, 20}, {112, 4, 20}, {113, 3, 25}, {137, 3, 25},
        {138, 2, 30}, {162, 2, 30}, {163, 2, 35}, {187, 2, 35},
        {188, 1, 40}, {212, 1, 40}, {213, 1, 45}, {237, 1, 45},
        {238, 0, 50}, {1000, 0, 50}
    };
    for (int[] c : cases) {
      int diff = c[0], highWin = c[1], lowWin = c[2];
      assertEquals(new RatingChange(highWin, -highWin, 1400 + diff + highWin, 1400 - highWin),
          RatingCalculator.calculate(1400 + diff, 1400, Winner.A), "high rating wins at diff " + diff);
      assertEquals(new RatingChange(-lowWin, lowWin, 1400 + diff - lowWin, 1400 + lowWin),
          RatingCalculator.calculate(1400 + diff, 1400, Winner.B), "low rating upsets at diff " + diff);
    }
  }

  @Test
  void symmetricInputsMirrorTheSameDelta() {
    for (int diff : new int[]{0, 12, 13, 37, 238, 1000}) {
      var leftHigh = RatingCalculator.calculate(1400 + diff, 1400, Winner.A);
      var rightHigh = RatingCalculator.calculate(1400, 1400 + diff, Winner.B);
      assertEquals(leftHigh.deltaA(), rightHigh.deltaB());
      assertEquals(leftHigh.deltaB(), rightHigh.deltaA());
    }
  }

  @Test
  void examplesAndRatingFloor() {
    assertEquals(new RatingChange(8, -8, 1408, 1392), RatingCalculator.calculate(1400, 1400, Winner.A));
    assertEquals(new RatingChange(-40, 40, 1560, 1440), RatingCalculator.calculate(1600, 1400, Winner.B));
    assertEquals(new RatingChange(0, 0, 1400, 400), RatingCalculator.calculate(1400, 400, Winner.A));
    assertEquals(new RatingChange(4, -4, 408, 400), RatingCalculator.calculate(404, 404, Winner.A));
    assertThrows(IllegalArgumentException.class, () -> RatingCalculator.calculate(399, 1400, Winner.A));
  }
}
