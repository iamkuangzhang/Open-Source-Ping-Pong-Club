package org.opensourcepingpong.rating.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RatingCalculatorTest {

  /**
   * 22 个关键积分差边界，双向（高分者胜 / 低分者爆冷胜）结果与
   * {@code docs/RATING_RULE.md} 第 3 节档位表一致。
   */
  @ParameterizedTest(name = "diff={0}: 高分者胜 +{1}, 爆冷胜 +{2}")
  @CsvSource({
      "0,    8,  8",
      "12,   8,  8",
      "13,   7, 10",
      "37,   7, 10",
      "38,   6, 13",
      "62,   6, 13",
      "63,   5, 16",
      "87,   5, 16",
      "88,   4, 20",
      "112,  4, 20",
      "113,  3, 25",
      "137,  3, 25",
      "138,  2, 30",
      "162,  2, 30",
      "163,  2, 35",
      "187,  2, 35",
      "188,  1, 40",
      "212,  1, 40",
      "213,  1, 45",
      "237,  1, 45",
      "238,  0, 50",
      "1000, 0, 50"
  })
  void boundaryTableIsAppliedBidirectionally(int diff, int highWin, int lowWin) {
    // 高分者（A，1400+diff）获胜
    assertEquals(
        new RatingChange(highWin, -highWin, 1400 + diff + highWin, 1400 - highWin),
        RatingCalculator.calculate(1400 + diff, 1400, Winner.A),
        "high-rated winner at diff " + diff);

    // 低分者（B，1400）爆冷获胜
    assertEquals(
        new RatingChange(-lowWin, lowWin, 1400 + diff - lowWin, 1400 + lowWin),
        RatingCalculator.calculate(1400 + diff, 1400, Winner.B),
        "low-rated upset at diff " + diff);
  }

  /** 交换双方与胜者后，积分变化必须镜像对称。 */
  @ParameterizedTest
  @ValueSource(ints = {0, 12, 13, 37, 62, 87, 112, 137, 162, 187, 212, 237, 238, 1000})
  void mirroredInputsProduceSymmetricDelta(int diff) {
    var leftHigh = RatingCalculator.calculate(1400 + diff, 1400, Winner.A);
    var rightHigh = RatingCalculator.calculate(1400, 1400 + diff, Winner.B);
    assertEquals(leftHigh.deltaA(), rightHigh.deltaB());
    assertEquals(leftHigh.deltaB(), rightHigh.deltaA());

    var leftUpset = RatingCalculator.calculate(1400 + diff, 1400, Winner.B);
    var rightUpset = RatingCalculator.calculate(1400, 1400 + diff, Winner.A);
    assertEquals(leftUpset.deltaA(), rightUpset.deltaB());
    assertEquals(leftUpset.deltaB(), rightUpset.deltaA());
  }

  @Test
  void equalRatingsMirrorTheSameEightPointChange() {
    assertEquals(new RatingChange(8, -8, 1408, 1392),
        RatingCalculator.calculate(1400, 1400, Winner.A));
    assertEquals(new RatingChange(-8, 8, 1392, 1408),
        RatingCalculator.calculate(1400, 1400, Winner.B));
  }

  @Test
  void documentedExamplesMatch() {
    // 1600 vs 1400，高分者胜：1601 / 1399
    assertEquals(new RatingChange(1, -1, 1601, 1399),
        RatingCalculator.calculate(1600, 1400, Winner.A));
    // 1600 vs 1400，低分者爆冷胜：1560 / 1440
    assertEquals(new RatingChange(-40, 40, 1560, 1440),
        RatingCalculator.calculate(1600, 1400, Winner.B));
  }

  @Test
  void floorCapsLoserAndBothChangeBySameActualAmount() {
    // 败者已经是 400：双方都不再变化
    assertEquals(new RatingChange(0, 0, 1400, 400),
        RatingCalculator.calculate(1400, 400, Winner.A));
    // 败者 404，理论扣 8，实际只能扣 4，胜者同步只 +4
    assertEquals(new RatingChange(4, -4, 408, 400),
        RatingCalculator.calculate(404, 404, Winner.A));
    // 败者 404 面对 7 分档位，实际仍只可扣 4
    assertEquals(new RatingChange(4, -4, 424, 400),
        RatingCalculator.calculate(420, 404, Winner.A));
  }

  @Test
  void ratingBelowFloorIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> RatingCalculator.calculate(399, 1400, Winner.A));
    assertThrows(IllegalArgumentException.class,
        () -> RatingCalculator.calculate(1400, 399, Winner.B));
  }
}
