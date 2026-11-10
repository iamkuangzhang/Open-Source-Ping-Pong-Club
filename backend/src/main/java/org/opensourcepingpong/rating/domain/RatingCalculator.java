package org.opensourcepingpong.rating.domain;

/**
 * 积分结算纯函数（规则 V1）。
 *
 * <p>不访问数据库、不访问 Redis、不读取当前时间；相同输入必须得到相同输出。
 * 档位表与 {@code docs/RATING_RULE.md} 第 3 节保持一致。
 */
public final class RatingCalculator {

  /** 最低积分，败者积分不得扣到该值以下。 */
  public static final int MIN_RATING = 400;

  /**
   * 积分差档位：{@code [差上限, 高分者获胜变化, 低分者爆冷获胜变化]}。
   *
   * <p>档位区间（含端点）：0–12、13–37、38–62、63–87、88–112、113–137、
   * 138–162、163–187、188–212、213–237、≥238。
   */
  private static final int[][] TIERS = {
      {12, 8, 8},
      {37, 7, 10},
      {62, 6, 13},
      {87, 5, 16},
      {112, 4, 20},
      {137, 3, 25},
      {162, 2, 30},
      {187, 2, 35},
      {212, 1, 40},
      {237, 1, 45},
      {Integer.MAX_VALUE, 0, 50}
  };

  private RatingCalculator() {}

  /**
   * 计算一场已完成有效场次后的积分变化。
   *
   * @param ratingA 赛前 A 积分，不得低于 {@link #MIN_RATING}
   * @param ratingB 赛前 B 积分，不得低于 {@link #MIN_RATING}
   * @param winner  本场胜者
   * @return 双方积分变化与赛后积分
   * @throws IllegalArgumentException 任一赛前积分低于 {@link #MIN_RATING}
   */
  public static RatingChange calculate(int ratingA, int ratingB, Winner winner) {
    if (ratingA < MIN_RATING || ratingB < MIN_RATING) {
      throw new IllegalArgumentException("rating must be at least " + MIN_RATING);
    }

    int diff = Math.abs(ratingA - ratingB);
    int[] tier = tierFor(diff);
    int highWin = tier[1];
    int lowWin = tier[2];

    boolean aWon = winner == Winner.A;
    boolean aIsHigher = ratingA > ratingB;
    boolean aIsLower = ratingA < ratingB;

    // 场景分属：高分者胜 / 低分者爆冷胜 / 双方积分相同
    int theoretical;
    if (ratingA == ratingB) {
      // 相同积分归入 0–12 档，双方名义变化相同（8 分），胜负结果镜像
      theoretical = highWin;
    } else if ((aIsHigher && aWon) || (aIsLower && !aWon)) {
      theoretical = highWin;
    } else {
      theoretical = lowWin;
    }

    int loserRating = aWon ? ratingB : ratingA;
    // 400 下限：败者扣不满时，双方都只变化实际可扣分数
    int actual = Math.min(theoretical, loserRating - MIN_RATING);
    int deltaA = aWon ? actual : -actual;

    return new RatingChange(deltaA, -deltaA, ratingA + deltaA, ratingB - deltaA);
  }

  private static int[] tierFor(int diff) {
    for (int[] tier : TIERS) {
      if (diff <= tier[0]) {
        return tier;
      }
    }
    return TIERS[TIERS.length - 1];
  }
}
