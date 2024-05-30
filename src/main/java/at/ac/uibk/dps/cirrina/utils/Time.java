package at.ac.uibk.dps.cirrina.utils;

import java.time.Instant;

public class Time {

  public static double timeInMillisecondsSinceEpoch() {
    final var now = Instant.now();
    return now.getEpochSecond() * 1.0e3 + now.getNano() / 1.0e6;
  }

  public static double timeInMillisecondsSinceStart() {
    return System.nanoTime() / 1.0e6;
  }
}
