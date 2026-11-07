package org.opensourcepingpong.competition.algorithm;

import java.util.*;

public final class RatingBalancedGroupAllocator {
  private RatingBalancedGroupAllocator() {}

  public static <T> List<List<T>> allocate(List<T> participants, Map<T,Integer> ratings, int groupCount) {
    if (participants == null || ratings == null || participants.isEmpty() || groupCount < 1
        || groupCount > participants.size() || new HashSet<>(participants).size() != participants.size())
      throw new IllegalArgumentException("invalid groups");
    List<T> sorted = new ArrayList<>(participants);
    sorted.sort(Comparator.<T,Integer>comparing(ratings::get, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(Object::toString));
    List<List<T>> groups = new ArrayList<>();
    for (int i = 0; i < groupCount; i++) groups.add(new ArrayList<>());
    for (int i = 0; i < sorted.size(); i++) {
      int cycle = i / groupCount, position = i % groupCount;
      groups.get(cycle % 2 == 0 ? position : groupCount - 1 - position).add(sorted.get(i));
    }
    return groups;
  }
}
