package org.opensourcepingpong.competition.algorithm;

import java.util.*;

public final class RandomGroupAllocator {
  private RandomGroupAllocator() {}

  public static <T> List<List<T>> allocate(List<T> participants, int groupCount, long seed) {
    if (participants == null || participants.isEmpty() || groupCount < 1 || groupCount > participants.size()
        || new HashSet<>(participants).size() != participants.size()) throw new IllegalArgumentException("invalid groups");
    List<T> shuffled = new ArrayList<>(participants);
    shuffled.sort(Comparator.comparing(Object::toString));
    Collections.shuffle(shuffled, new Random(seed));
    List<List<T>> groups = new ArrayList<>();
    for (int i = 0; i < groupCount; i++) groups.add(new ArrayList<>());
    for (int i = 0; i < shuffled.size(); i++) groups.get(i % groupCount).add(shuffled.get(i));
    return groups;
  }
}
